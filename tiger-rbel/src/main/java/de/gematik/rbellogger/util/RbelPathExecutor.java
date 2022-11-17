/*
 * Copyright (c) 2022 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.rbellogger.util;

import static de.gematik.rbellogger.RbelOptions.ACTIVATE_RBEL_PATH_DEBUGGING;
import static de.gematik.rbellogger.RbelOptions.RBEL_PATH_TREE_VIEW_MINIMUM_DEPTH;
import de.gematik.rbellogger.converter.RbelJexlExecutor;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelJsonFacet;
import de.gematik.rbellogger.data.facet.RbelNestedFacet;
import de.gematik.rbellogger.exceptions.RbelPathException;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.math.NumberUtils;

@RequiredArgsConstructor
@Slf4j
public class RbelPathExecutor {

    private final RbelElement rbelElement;
    private final String rbelPath;

    private static List<RbelElement> findAllChildsRecursive(final RbelElement element) {
        final List<? extends RbelElement> childNodes = element.getChildNodes();
        List<RbelElement> result = new ArrayList<>(childNodes);
        childNodes.stream()
            .map(RbelPathExecutor::findAllChildsRecursive)
            .filter(Objects::nonNull)
            .flatMap(List::stream)
            .forEach(result::add);
        return result;
    }

    public List<RbelElement> execute() {
        if (!rbelPath.startsWith("$")) {
            throw new RbelPathException("RbelPath expressions always start with $.");
        }
        final List<String> keys = List.of(rbelPath.substring(2).split("\\.(?![^\\(]*\\))"));
        List<RbelElement> candidates = List.of(rbelElement);
        if (ACTIVATE_RBEL_PATH_DEBUGGING) {
            log.info("Executing RBelPath {} into root-element (limited view to {} levels)\n{}",
                rbelPath, Math.max(RBEL_PATH_TREE_VIEW_MINIMUM_DEPTH, keys.size()),
                rbelElement.printTreeStructure(Math.max(RBEL_PATH_TREE_VIEW_MINIMUM_DEPTH, keys.size()), false));
        }
        for (String key : keys) {
            if (ACTIVATE_RBEL_PATH_DEBUGGING) {
                log.info("Resolving key '{}' with candidates {}", key, candidates.stream()
                    .flatMap(el -> el.getChildNodesWithKey().stream())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList()));
            }
            List<RbelElement> lastIterationCandidates = candidates;
            candidates = candidates.stream()
                .map(element -> resolveRbelPathElement(key, element))
                .flatMap(List::stream)
                .map(this::descendToContentNodeIfAdvised)
                .flatMap(List::stream)
                .distinct()
                .collect(Collectors.toUnmodifiableList());
            if (candidates.isEmpty() && ACTIVATE_RBEL_PATH_DEBUGGING) {
                log.warn("No more candidate-nodes in RbelPath execution! Last batch of candidates had {} elements: \n {}",
                    lastIterationCandidates.size(),
                    lastIterationCandidates.stream()
                    .map(el -> el.printTreeStructure(Integer.MAX_VALUE, true))
                    .collect(Collectors.joining("\n")));
            }
        }

        final List<RbelElement> resultList = candidates.stream()
            .filter(el -> !(el.hasFacet(RbelJsonFacet.class) && el.hasFacet(RbelNestedFacet.class)))
            .collect(Collectors.toUnmodifiableList());
        if (ACTIVATE_RBEL_PATH_DEBUGGING) {
            log.info("Returning {} result elements for RbelPath {}", resultList.size(), rbelPath);
        }
        return resultList;
    }

    private List<RbelElement> descendToContentNodeIfAdvised(RbelElement rbelElement) {
        if (rbelElement.hasFacet(RbelJsonFacet.class)
            && rbelElement.hasFacet(RbelNestedFacet.class)) {
            return List.of(rbelElement.getFacet(RbelNestedFacet.class)
                    .map(RbelNestedFacet::getNestedElement)
                    .get(),
                rbelElement);
        } else {
            return List.of(rbelElement);
        }
    }

    private List<? extends RbelElement> resolveRbelPathElement(final String key, final RbelElement element) {
        final String[] parts = key.split("\\[", 2);
        final String selectorPart = parts[0];
        List<? extends RbelElement> keySelectionResult = executeNonFunctionalExpression(selectorPart, element);
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
                    .map(candidate -> executeFunctionalExpression(functionalPart, candidate.getParentNode()))
                    .flatMap(List::stream)
                    .collect(Collectors.toList());
            }
        }
    }

    private List<? extends RbelElement> executeNonFunctionalExpression(String key, RbelElement element) {
        if (key.isEmpty()) {
            return findAllChildsRecursive(element);
        } else if (key.equals("*")) {
            return element.getChildNodes();
        } else {
            return element.getAll(key);
        }
    }

    private List<? extends RbelElement> executeFunctionalExpression(
        final String functionExpression, final RbelElement element) {
        if (functionExpression.startsWith("'") && functionExpression.endsWith("'")) {
            return element.getAll(functionExpression.substring(1, functionExpression.length() - 1));
        } else if (functionExpression.equals("*")) {
            return element.getChildNodes();
        } else if (functionExpression.startsWith("?")) {
            if (functionExpression.startsWith("?(") && functionExpression.endsWith(")")) {
                return findChildNodesByJexlExpression(element,
                    functionExpression.substring(2, functionExpression.length() - 1));
            } else {
                throw new RbelPathException(
                    "Invalid JEXL-Expression encountered (Does not start with '?(' and end with ')'): "
                        + functionExpression);
            }
        } else {
            throw new RbelPathException("Unknown function expression encountered: " + functionExpression);
        }
    }

    private List<RbelElement> findChildNodesByJexlExpression(final RbelElement element, final String jexl) {
        RbelJexlExecutor executor = new RbelJexlExecutor();
        return element.getChildNodesWithKey().stream()
            .filter(candidate ->
                executor.matchesAsJexlExpression(candidate.getValue(), jexl, Optional.of(candidate.getKey())))
            .map(Map.Entry::getValue)
            .collect(Collectors.toList());
    }
}
