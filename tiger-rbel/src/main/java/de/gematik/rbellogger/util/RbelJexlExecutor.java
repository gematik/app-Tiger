/*
 *
 * Copyright 2021-2025 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.rbellogger.util;

import com.google.common.base.CharMatcher;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.test.tiger.common.TokenSubstituteHelper;
import de.gematik.test.tiger.common.config.TigerConfigurationLoader;
import de.gematik.test.tiger.common.jexl.TigerJexlContext;
import de.gematik.test.tiger.common.jexl.TigerJexlExecutor;
import java.util.*;
import java.util.function.IntPredicate;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RbelJexlExecutor {

  private static final String RBEL_PATH_CHARS = "(\\$\\.|\\w|\\.|\\*|-)+.*";

  private static boolean isInitialized = false;

  public static synchronized void initialize() {
    if (isInitialized) {
      return;
    }
    TokenSubstituteHelper.getReplacerOrder()
        .addFirst(
            Pair.of(
                '?',
                (str, source, ctx) ->
                    ctx.map(TigerJexlContext::getCurrentElement)
                        .filter(RbelElement.class::isInstance)
                        .map(RbelElement.class::cast)
                        .flatMap(el -> el.findElement(str))
                        .map(el -> el.printValue().orElseGet(el::getRawStringContent))));
    TigerJexlExecutor.setExpressionPreMapper(RbelJexlExecutor::evaluateRbelPathExpressions);
    TigerJexlExecutor.addContextDecorator(RbelContextDecorator::buildJexlMapContext);
    TokenSubstituteHelper.setResolve(RbelJexlExecutor::resolveConfigurationValue);
    isInitialized = true;
  }

  private static Optional<String> resolveConfigurationValue(
      String key, TigerConfigurationLoader configuration) {
    return configuration
        .readStringOptional(key)
        .or(
            () ->
                new RbelPathExecutor<>(new TigerConfigurationRbelObject(configuration), "$." + key)
                    .execute().stream()
                        .findFirst()
                        .map(TigerConfigurationRbelObject::getRawStringContent));
  }

  public static boolean matchAsTextExpression(Object element, String textExpression) {
    try {
      final boolean textMatchResult =
          Objects.requireNonNull(((RbelElement) element).getRawStringContent())
              .contains(textExpression);
      final boolean regexMatchResult =
          Pattern.compile(textExpression)
              .matcher(((RbelElement) element).getRawStringContent())
              .find();

      return textMatchResult || regexMatchResult;
    } catch (Exception e) {
      if (TigerJexlExecutor.isActivateJexlDebugging()) {
        log.info("Error during Text search.", e);
      }
      return false;
    }
  }

  /**
   * This method expands one expression to a list of all possible concrete-value interpretations.
   * Every containing RbelPath-expression is resolved to ALL POSSIBLE concrete values (there can be
   * more then one). Since the resulting expressions are static there will be a value added for
   * every result. For two RbelPath-expressions referencing three different elements each the
   * resulting list will contain all six combinations.
   *
   * @param jexlExpression The expression to be analyzed
   * @param mapContext The map-context to be used
   * @return A list of all concrete JEXL-expressions
   */
  private static List<String> evaluateRbelPathExpressions(
      String jexlExpression, TigerJexlContext mapContext) {
    // always skip the evaluation if there is no element in the current context
    // necessary hack, remove with AST
    if (mapContext.getRootElement() == null) {
      return List.of(jexlExpression);
    }

    List<String> resultingPaths = List.of(jexlExpression);
    for (var potentialPath : extractPotentialRbelPaths(jexlExpression)) {
      if (!(potentialPath.startsWith("$.") || potentialPath.startsWith("@."))) {
        continue;
      }
      List<String> previousIterationPaths = new ArrayList<>(resultingPaths);
      List<String> newPaths = new ArrayList<>();
      for (var expression : previousIterationPaths) {
        evaluatePathsAndCollectAllResults(mapContext, potentialPath, expression, newPaths);
      }
      if (!newPaths.isEmpty()) {
        resultingPaths = newPaths;
      }
    }
    return resultingPaths;
  }

  private static void evaluatePathsAndCollectAllResults(
      TigerJexlContext mapContext, String potentialPath, String expression, List<String> newPaths) {
    final List<String> pathResults =
        new ArrayList<>(
            extractPathAndConvertToString(
                potentialPath.startsWith("@.")
                    ? mapContext.getCurrentElement()
                    : mapContext.getRootElement(),
                potentialPath.startsWith("@.")
                    ? potentialPath.replaceFirst("@\\.", "\\$.")
                    : potentialPath));
    if (pathResults.isEmpty()
        || pathResults.stream().anyMatch(s -> !CharMatcher.ascii().matchesAllOf(s))) {
      pathResults.add(null);
    }
    for (String pathResult : pathResults) {
      final String id =
          "replacedPath_"
              + RandomStringUtils.insecure().nextAlphabetic(20).toLowerCase(); // NOSONAR
      mapContext.put(id, pathResult);
      newPaths.add(expression.replace(potentialPath, id));
    }
  }

  public static List<String> extractPotentialRbelPaths(String jexlExpression) {
    List<String> rbelPaths = new ArrayList<>();
    boolean insideRbelPath = false;
    boolean insideNestedJexlExpression = false;
    boolean insideVerbatimBracket = false;
    int jexlExpressionStart = -1;
    int pos = 0;
    IntPredicate openingJexlBracketIsNext = p -> jexlExpression.startsWith("[?(", p);
    IntPredicate closingJexlBracketIsNext = p -> jexlExpression.startsWith(")]", p);
    IntPredicate openingVerbatimBracketIsNext =
        p -> jexlExpression.startsWith("['", p) || jexlExpression.startsWith("[~'", p);
    IntPredicate closingVerbatimBracketIsNext = p -> jexlExpression.startsWith("']", p);
    IntPredicate nextCharIsNotStillRbelPath =
        p -> !jexlExpression.substring(p).matches(RBEL_PATH_CHARS); // NOSONAR
    IntPredicate startingRbelPathIsNext =
        p -> jexlExpression.startsWith("$.", p) || jexlExpression.startsWith("@.", p);

    while (pos < jexlExpression.length()) {
      if (insideNestedJexlExpression) {
        if (closingJexlBracketIsNext.test(pos)) {
          insideNestedJexlExpression = false;
          pos++;
        }
      } else if (insideVerbatimBracket) {
        if (closingVerbatimBracketIsNext.test(pos)) {
          insideVerbatimBracket = false;
          pos++;
        }
      } else if (insideRbelPath) {
        if (openingJexlBracketIsNext.test(pos)) {
          insideNestedJexlExpression = true;
          pos += 2;
        } else if (openingVerbatimBracketIsNext.test(pos)) {
          insideVerbatimBracket = true;
        } else if (nextCharIsNotStillRbelPath.test(pos)) {
          rbelPaths.add(jexlExpression.substring(jexlExpressionStart, pos));
          insideRbelPath = false;
        }
      } else {
        if (startingRbelPathIsNext.test(pos)) {
          insideRbelPath = true;
          jexlExpressionStart = pos;
          pos++;
        }
      }
      pos++;
    }

    // End of string and rbelPath still going: Add the current rbelPath
    if (insideRbelPath) {
      rbelPaths.add(jexlExpression.substring(jexlExpressionStart, pos));
    }

    return rbelPaths;
  }

  private static List<String> extractPathAndConvertToString(Object source, String rbelPath) {
    return Optional.ofNullable(source)
        .filter(RbelPathAble.class::isInstance)
        .map(RbelPathAble.class::cast)
        .map(s -> s.findRbelPathMembers(rbelPath))
        .orElse(List.of())
        .stream()
        .map(RbelContextDecorator::forceStringConvert)
        .toList();
  }
}
