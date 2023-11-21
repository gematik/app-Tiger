/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.util;

import static de.gematik.rbellogger.RbelOptions.ACTIVATE_RBEL_PATH_DEBUGGING;
import static de.gematik.rbellogger.RbelOptions.RBEL_PATH_TREE_VIEW_MINIMUM_DEPTH;

import de.gematik.rbellogger.RbelContent;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelJsonFacet;
import de.gematik.rbellogger.data.facet.RbelNestedFacet;
import de.gematik.rbellogger.exceptions.RbelPathException;
import de.gematik.test.tiger.common.jexl.TigerJexlContext;
import de.gematik.test.tiger.common.jexl.TigerJexlExecutor;
import java.net.URLDecoder;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

@RequiredArgsConstructor
@Slf4j
public class RbelPathExecutor {

  private final RbelContent rbelContent;
  private final String rbelPath;

  private static List<RbelContent> findAllChildsRecursive(final RbelContent content) {
    final List<? extends RbelContent> childNodes = content.getChildNodes();
    List<RbelContent> result = new ArrayList<>(childNodes);
    childNodes.stream()
        .map(RbelPathExecutor::findAllChildsRecursive)
        .filter(Objects::nonNull)
        .flatMap(List::stream)
        .forEach(result::add);
    return result;
  }

  @SuppressWarnings("unchecked")
  public <T extends RbelContent> List<T> execute(Class<T> clazz) {
    if (!rbelPath.startsWith("$")) {
      throw new RbelPathException(
          "RbelPath expressions always start with $. (got '" + rbelPath + "')");
    }
    final List<String> keys = splitRbelPathIntoKeys(rbelPath);

    if (keys.stream().anyMatch(s -> s.startsWith(" ") || s.endsWith(" "))) {
      throw new RbelPathException(
          "Found key with unescaped spaces in rbel-path '"
              + rbelPath
              + "'! (If intended, please escape using \"[' b b ']\")");
    }

    if (!(clazz.equals(rbelContent.getClass()) || clazz.isAssignableFrom(rbelContent.getClass()))) {
      throw new ClassCastException(
          "The provided Class '%s' is not the same of RbelContent of the Path Executor."
              .formatted(clazz.toString()));
    }

    List<T> candidates = List.of((T) rbelContent);

    if (ACTIVATE_RBEL_PATH_DEBUGGING && rbelContent instanceof RbelElement asRbelElement) {
      log.info(
          "Executing RBelPath {} into root-element (limited view to {} levels)\n{}",
          rbelPath,
          Math.max(RBEL_PATH_TREE_VIEW_MINIMUM_DEPTH, keys.size()),
          asRbelElement.printTreeStructure(
              Math.max(RBEL_PATH_TREE_VIEW_MINIMUM_DEPTH, keys.size()), false));
    }
    for (String key : keys) {
      if (ACTIVATE_RBEL_PATH_DEBUGGING) {
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
              .map(this::descendToContentNodeIfAdvised)
              .flatMap(List::stream)
              .map(o -> (T) o)
              .distinct()
              .toList();
      if (candidates.isEmpty() && ACTIVATE_RBEL_PATH_DEBUGGING) {
        List<RbelElement> asRbelElements =
            lastIterationCandidates.stream()
                .filter(RbelElement.class::isInstance)
                .map(r -> (RbelElement) r)
                .toList();
        log.warn(
            "No more candidate-nodes in RbelPath execution! Last batch of candidates had {}"
                + " elements: \n"
                + " {}",
            asRbelElements.size(),
            asRbelElements.stream()
                .map(el -> el.printTreeStructure(Integer.MAX_VALUE, true))
                .collect(Collectors.joining("\n")));
      }
    }

    final List<T> resultList =
        candidates.stream()
            .filter(el -> !(el.hasFacet(RbelJsonFacet.class) && el.hasFacet(RbelNestedFacet.class)))
            .toList();
    if (ACTIVATE_RBEL_PATH_DEBUGGING) {
      log.info("Returning {} result elements for RbelPath {}", resultList.size(), rbelPath);
    }
    return resultList;
  }

  public static List<String> splitRbelPathIntoKeys(String rbelPath) {
    final String[] split = rbelPath.substring(1).trim().split("\\.(?!(\\.|[^\\[]*\\]))");
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

    if (ACTIVATE_RBEL_PATH_DEBUGGING) {
      log.debug("Split rbelPath {} into the following keys: {}", rbelPath, keys);
    }

    return keys;
  }

  private List<RbelContent> descendToContentNodeIfAdvised(RbelContent rbelContent) {
    if (rbelContent instanceof RbelElement asRbelElement
        && rbelContent.hasFacet(RbelJsonFacet.class)
        && rbelContent.hasFacet(RbelNestedFacet.class)) {
      return List.of(
          asRbelElement
              .getFacet(RbelNestedFacet.class)
              .map(RbelNestedFacet::getNestedElement)
              .orElseThrow(),
          asRbelElement);
    } else {
      return List.of(rbelContent);
    }
  }

  private List<? extends RbelContent> resolveRbelPathElement(
      final String key, final RbelContent content) {
    if (key.equals(".")) {
      final List<RbelContent> wildcardResult = findAllChildsRecursive(content);
      wildcardResult.add(content);
      return wildcardResult;
    }
    final String[] parts = key.split("\\[", 2);
    final String selectorPart = parts[0];
    List<? extends RbelContent> keySelectionResult =
        executeNonFunctionalExpression(selectorPart, content);
    if (parts.length == 1) {
      return keySelectionResult;
    } else {
      final String functionalPart = parts[1].substring(0, parts[1].length() - 1);
      if (NumberUtils.isParsable(functionalPart)) {
        final int selectionIndex = Integer.parseInt(parts[1].substring(0, parts[1].length() - 1));
        if (keySelectionResult.size() <= selectionIndex) {
          return Collections.emptyList();
        }
        return List.of(keySelectionResult.get(selectionIndex));
      } else {
        return keySelectionResult.stream()
            .map(
                candidate -> executeFunctionalExpression(functionalPart, candidate.getParentNode()))
            .flatMap(List::stream)
            .collect(Collectors.toList());
      }
    }
  }

  private List<? extends RbelContent> executeNonFunctionalExpression(
      String key, RbelContent content) {
    if (key.isEmpty() | key.equals("*")) {
      return content.getChildNodes();
    } else {
      return content.getAll(key);
    }
  }

  private List<? extends RbelContent> executeFunctionalExpression(
      final String functionExpression, final RbelContent content) {
    if (functionExpression.startsWith("'") && functionExpression.endsWith("'")) {
      return executeNamedSelection(functionExpression, content);
    } else if (functionExpression.equals("*")) {
      return content.getChildNodes();
    } else if (functionExpression.startsWith("?")) {
      if (functionExpression.startsWith("?(") && functionExpression.endsWith(")")) {
        return findChildNodesByJexlExpression(
            content, functionExpression.substring(2, functionExpression.length() - 1));
      } else {
        throw new RbelPathException(
            "Invalid JEXL-Expression encountered (Does not start with '?(' and end with ')'): "
                + functionExpression);
      }
    } else {
      throw new RbelPathException("Unknown function expression encountered: " + functionExpression);
    }
  }

  private static List<? extends RbelContent> executeNamedSelection(
      String functionExpression, RbelContent content) {
    return Stream.of(functionExpression.split("\\|"))
        .peek(
            s -> {
              if (!s.startsWith("'") || !s.endsWith("'")) {
                throw new RbelPathException(
                    "Requiring all name selector to be surrounded by '. Violated by " + s);
              }
            })
        .map(s -> s.substring(1, s.length() - 1))
        .map(URLDecoder::decode)
        .map(content::getAll)
        .flatMap(List::stream)
        .toList();
  }

  private List<RbelContent> findChildNodesByJexlExpression(
      final RbelContent content, final String jexl) {
    return content.getChildNodesWithKey().stream()
        .parallel()
        .filter(
            candidate ->
                TigerJexlExecutor.matchesAsJexlExpression(
                    jexl,
                    new TigerJexlContext()
                        .withKey(candidate.getKey())
                        .withCurrentElement(candidate.getValue())
                        .withRootElement(this.rbelContent)))
        .map(Map.Entry::getValue)
        .collect(Collectors.toList());
  }
}
