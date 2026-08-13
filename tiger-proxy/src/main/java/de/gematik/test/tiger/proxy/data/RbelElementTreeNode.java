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
package de.gematik.test.tiger.proxy.data;

import static de.gematik.rbellogger.util.RbelContextDecorator.forceStringConvert;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelListFacet;
import de.gematik.rbellogger.data.core.RbelMapFacet;
import de.gematik.rbellogger.data.core.RbelNestedFacet;
import de.gematik.rbellogger.data.core.RbelRootFacet;
import de.gematik.rbellogger.data.util.RbelElementTreePrinter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A structured, JSON-serializable representation of an {@link RbelElement} subtree, for the Tiger
 * Proxy WebUI's REST API. Mirrors the tree {@link RbelElementTreePrinter} renders as ASCII/HTML,
 * but as plain data instead of a rendered string, and without abbreviating node content.
 */
public record RbelElementTreeNode(
    String key, String content, List<String> facets, List<RbelElementTreeNode> children) {

  private static final Set<Class<?>> STRUCTURAL_FACETS =
      Set.of(RbelListFacet.class, RbelNestedFacet.class, RbelMapFacet.class);

  public static RbelElementTreeNode from(RbelElement rootElement) {
    return build(rootElement, findKeyOfRootElement(rootElement));
  }

  private static RbelElementTreeNode build(RbelElement element, String key) {
    return new RbelElementTreeNode(
        key,
        forceStringConvert(element),
        element.getFacets().stream()
            .map(Object::getClass)
            .filter(facetClass -> !STRUCTURAL_FACETS.contains(facetClass))
            .map(Class::getSimpleName)
            .toList(),
        element.getChildNodesWithKey().stream()
            .map(entry -> build(entry.getValue(), entry.getKey()))
            .toList());
  }

  private static String findKeyOfRootElement(RbelElement rootElement) {
    return Optional.ofNullable(rootElement.getParentNode()).stream()
        .flatMap(RbelElement::getChildNodesWithKeyStream)
        .filter(pair -> pair.getValue() == rootElement)
        .map(Map.Entry::getKey)
        .findFirst()
        .orElse("");
  }
}
