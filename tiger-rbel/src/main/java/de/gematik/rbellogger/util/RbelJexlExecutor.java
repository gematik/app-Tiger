/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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

  private static final String RBEL_PATH_CHARS = "(\\$\\.|\\w|\\.)+.*";

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
    TokenSubstituteHelper.RESOLVE = RbelJexlExecutor::resolveConfigurationValue;
    isInitialized = true;
  }

  private static Optional<String> resolveConfigurationValue(
      String key, TigerConfigurationLoader configuration) {
    return configuration.readStringOptional(key)
      .or(() -> new RbelPathExecutor<>(new TigerConfigurationRbelObject(configuration), "$." + key)
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
      if (TigerJexlExecutor.ACTIVATE_JEXL_DEBUGGING) {
        log.info("Error during Text search.", e);
      }
      return false;
    }
  }

  private static List<String> evaluateRbelPathExpressions(
      String jexlExpression, TigerJexlContext mapContext) {
    List<String> resultingPaths = List.of(jexlExpression);
    for (var potentialPath : extractPotentialRbelPaths(jexlExpression)) {
      if (!(potentialPath.startsWith("$.") || potentialPath.startsWith("@."))) {
        continue;
      }
      List<String> previousIterationPaths = new ArrayList<>(resultingPaths);
      List<String> newPaths = new ArrayList<>();
      for (var expression : previousIterationPaths) {
        final List<String> pathResults =
            extractPathAndConvertToString(
                potentialPath.startsWith("@.")
                    ? mapContext.getCurrentElement()
                    : mapContext.getRootElement(),
                potentialPath.startsWith("@.")
                    ? potentialPath.replaceFirst("@\\.", "\\$.")
                    : potentialPath);
        if (pathResults.isEmpty()
            || pathResults.stream().anyMatch(s -> !CharMatcher.ascii().matchesAllOf(s))) {
          continue;
        }
        for (String pathResult : pathResults) {
          final String id = "replacedPath_" + RandomStringUtils.randomAlphabetic(20); // NOSONAR
          mapContext.put(id, pathResult);
          newPaths.add(expression.replace(potentialPath, id));
        }
      }
      if (!newPaths.isEmpty()) {
        resultingPaths = newPaths;
      }
    }
    return resultingPaths;
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
    IntPredicate openingVerbatimBracketIsNext = p -> jexlExpression.startsWith("['", p);
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
