/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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
      log.debug("Split rbelPath {} into the following keys: {}", rbelPath, keys);
    }

    return keys;
  }

  private static List<? extends RbelPathAble> executeNamedSelection(
      String functionExpression, RbelPathAble content) {
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
        .map(content::getAll)
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
      return executeNamedSelection(functionExpression, content);
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
