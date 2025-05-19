/*
 * Copyright 2024 gematik GmbH
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
 */

package de.gematik.rbellogger.util;

import com.google.common.annotations.VisibleForTesting;
import de.gematik.rbellogger.RbelOptions;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.exceptions.RbelPathException;
import de.gematik.test.tiger.common.jexl.TigerJexlContext;
import de.gematik.test.tiger.common.jexl.TigerJexlExecutor;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

@RequiredArgsConstructor
@Slf4j
public class RbelPathExecutor<T extends RbelPathAble> {

  private final T targetObject;
  private final String rbelPath;

  private static List<RbelPathAble> findAllChildrenRecursive(final RbelPathAble content) {
    final List<? extends RbelPathAble> childNodes = content.getChildNodes();
    List<RbelPathAble> result = new ArrayList<>(childNodes);
    childNodes.stream()
        .map(RbelPathExecutor::findAllChildrenRecursive)
        .flatMap(List::stream)
        .forEach(result::add);
    return result;
  }

  @VisibleForTesting
  public static List<String> splitRbelPathIntoKeys(String rbelPath) {
    final String[] split = rbelPath.substring(1).trim().split("\\.(?!(\\.|[^\\[]*]))");
    final ArrayList<String> keys = new ArrayList<>();
    for (String part : split) {
      if (StringUtils.isBlank(part)) {
        continue;
      }
      if (part.length() > 1 && part.endsWith(".")) {
        keys.add(part.substring(0, part.length() - 1));
        keys.add(".");
      } else {
        keys.add(part);
      }
    }

    if (RbelOptions.isActivateRbelPathDebugging()) {
      log.info("Split rbelPath {} into the following keys: {}", rbelPath, keys);
    }

    return keys;
  }

  private static List<? extends RbelPathAble> executeNamedSelection(
      String functionExpression, RbelPathAble content, BiPredicate<String, String> keyPredicate) {
    return Stream.of(functionExpression.split("\\|"))
        .map(
            s -> {
              if (!s.startsWith("'") || !s.endsWith("'")) {
                throw new RbelPathException(
                    "Requiring all name selector to be surrounded by '. Violated by " + s);
              }
              return s.substring(1, s.length() - 1);
            })
        .map(s1 -> URLDecoder.decode(s1, StandardCharsets.UTF_8))
        .map(
            key ->
                content.getChildNodesWithKey().stream()
                    .filter(entry -> keyPredicate.test(key, entry.getKey()))
                    .map(Map.Entry::getValue)
                    .toList())
        .flatMap(List::stream)
        .toList();
  }

  @SuppressWarnings("unchecked")
  public List<T> execute() {
    enforceCorrectRbelPathForm();
    final List<String> keys = splitRbelPathIntoKeys(rbelPath);
    List<T> candidates = List.of(targetObject);
    performPreExecutionLogging(keys);
    checkFurtherPreconditions(keys);

    for (String key : keys) {
      if (RbelOptions.isActivateRbelPathDebugging()) {
        log.info(
            "Resolving key '{}' with candidates {}",
            key,
            candidates.stream()
                .flatMap(el -> el.getChildNodesWithKey().stream())
                .map(Map.Entry::getKey)
                .toList());
      }
      List<T> lastIterationCandidates = candidates;
      candidates =
          candidates.stream()
              .map(element -> resolveRbelPathElement(key, element))
              .flatMap(List::stream)
              .map(RbelPathAble::descendToContentNodeIfAdvised)
              .flatMap(List::stream)
              .map(o -> (T) o)
              .distinct()
              .toList();
      if (candidates.isEmpty() && RbelOptions.isActivateRbelPathDebugging()) {
        log.warn(
            "No more candidate-nodes in RbelPath execution! Last batch of candidates had {}"
                + " elements: \n"
                + " {}",
            lastIterationCandidates.size(),
            getPathList(lastIterationCandidates));
      }
    }

    final List<T> resultList =
        candidates.stream().filter(RbelPathAble::shouldElementBeKeptInFinalResult).toList();
    if (RbelOptions.isActivateRbelPathDebugging()) {
      log.info(
          "Returning {} result elements for RbelPath {} (Results are {})",
          resultList.size(),
          rbelPath,
          getPathList(resultList));
    }
    return resultList;
  }

  private void performPreExecutionLogging(List<String> keys) {
    if (RbelOptions.isActivateRbelPathDebugging()
        && targetObject instanceof RbelElement asRbelElement) {
      log.info(
          "Executing RBelPath {} into element '{}' (limited view to {} levels):\n{}",
          rbelPath,
          targetObject.findNodePath(),
          Math.max(RbelOptions.getRbelPathTreeViewMinimumDepth(), keys.size()),
          asRbelElement.printTreeStructure(
              Math.max(RbelOptions.getRbelPathTreeViewMinimumDepth(), keys.size()), false));
    }
  }

  private void checkFurtherPreconditions(List<String> keys) {
    if (keys.stream().anyMatch(s -> s.startsWith(" ") || s.endsWith(" "))) {
      throw new RbelPathException(
          "Found key with unescaped spaces in rbel-path '"
              + rbelPath
              + "'! (If intended, please escape using \"[' b b ']\")");
    }
  }

  private void enforceCorrectRbelPathForm() {
    if (!rbelPath.startsWith("$")) {
      throw new RbelPathException(
          "RbelPath expressions always start with $. (got '" + rbelPath + "')");
    }
  }

  private List<? extends RbelPathAble> resolveRbelPathElement(
      final String key, final RbelPathAble content) {
    if (key.equals(".")) {
      final List<RbelPathAble> wildcardResult = findAllChildrenRecursive(content);
      wildcardResult.add(content);
      return wildcardResult;
    }
    final String[] parts = key.split("\\[", 2);
    final String selectorPart = parts[0];
    List<? extends RbelPathAble> keySelectionResult =
        executeNonFunctionalExpression(selectorPart, content);
    if (parts.length == 1 || keySelectionResult.isEmpty()) {
      return keySelectionResult;
    } else {
      return filterResultsThroughFunctionalSelector(
          keySelectionResult, parts[1].substring(0, parts[1].length() - 1), selectorPart.isEmpty());
    }
  }

  private List<? extends RbelPathAble> filterResultsThroughFunctionalSelector(
      List<? extends RbelPathAble> keySelectionResult,
      String functionalPart,
      boolean selectorPartIsEmpty) {
    if (RbelOptions.isActivateRbelPathDebugging()) {
      log.info(
          "Filtering resulting nodes '{}' through functional expression '{}'",
          getPathList(keySelectionResult),
          functionalPart);
    }

    if (NumberUtils.isParsable(functionalPart)) {
      final int selectionIndex = Integer.parseInt(functionalPart);
      if (keySelectionResult.size() <= selectionIndex) {
        return Collections.emptyList();
      }
      return List.of(keySelectionResult.get(selectionIndex));
    } else {
      return keySelectionResult.stream()
          .map(
              candidate ->
                  executeFunctionalExpression(functionalPart, candidate, selectorPartIsEmpty))
          .flatMap(List::stream)
          .toList();
    }
  }

  private List<? extends RbelPathAble> executeNonFunctionalExpression(
      String key, RbelPathAble content) {
    if (key.equals("*")) {
      return content.getChildNodes();
    } else if (key.isEmpty()) {
      return List.of(content);
    } else {
      return content.getAll(key);
    }
  }

  private List<? extends RbelPathAble> executeFunctionalExpression(
      final String functionExpression, final RbelPathAble content, boolean selectorPartIsEmpty) {
    if (functionExpression.startsWith("'") && functionExpression.endsWith("'")) {
      return executeNamedSelection(functionExpression, content, String::equals);
    } else if (functionExpression.equals("*")) {
      return content.getChildNodes();
    } else if (functionExpression.startsWith("?")) {
      if (functionExpression.startsWith("?(") && functionExpression.endsWith(")")) {
        return findChildNodesByJexlExpression(
            content,
            functionExpression.substring(2, functionExpression.length() - 1),
            selectorPartIsEmpty);
      } else {
        throw new RbelPathException(
            "Invalid JEXL-Expression encountered (Does not start with '?(' and end with ')'): "
                + functionExpression);
      }
    } else if (functionExpression.startsWith("~")) {
      if (functionExpression.startsWith("~'") && functionExpression.endsWith("'")) {
        return executeNamedSelection(
            functionExpression.substring(1), content, String::equalsIgnoreCase);
      } else {
        throw new RbelPathException(
            "Invalid JEXL-Expression encountered (Does not start with \"~'\"' and end with \")\"): "
                + functionExpression);
      }
    } else {
      throw new RbelPathException("Unknown function expression encountered: " + functionExpression);
    }
  }

  private List<? extends RbelPathAble> findChildNodesByJexlExpression(
      final RbelPathAble position, final String jexl, boolean selectorPartIsEmpty) {
    List<RbelPathAble> candidates = new ArrayList<>();
    if (selectorPartIsEmpty) {
      candidates.addAll(position.getChildNodes());
    } else {
      candidates.add(position);
    }
    return candidates.stream()
        .parallel()
        .filter(
            candidate ->
                TigerJexlExecutor.matchesAsJexlExpression(
                    jexl,
                    new TigerJexlContext()
                        .withKey(candidate.getKey().orElse(null))
                        .withCurrentElement(candidate)
                        .withRootElement(this.targetObject)))
        .toList();
  }

  private static <T extends RbelPathAble> List<String> getPathList(List<T> resultList) {
    return resultList.stream().map(RbelPathAble::findNodePath).map(path -> "$." + path).toList();
  }
}
