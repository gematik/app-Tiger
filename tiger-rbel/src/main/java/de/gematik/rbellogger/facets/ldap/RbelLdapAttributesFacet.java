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
package de.gematik.rbellogger.facets.ldap;

import static j2html.TagCreator.*;
import static j2html.TagCreator.b;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.RbelFacet;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import j2html.tags.ContainerTag;
import java.util.Map;
import java.util.Optional;

public class RbelLdapAttributesFacet extends RbelMultiMap<RbelElement> implements RbelFacet {

  public void putAttribute(String attributeName, RbelElement element) {
    String sanitizedName = sanitize(attributeName);
    element.addFacet(new RbelLdapAttributeMetadataFacet(attributeName));
    put(sanitizedName, element);
  }

  private static String sanitize(String attributeName) {
    if (attributeName == null || attributeName.isBlank()) {
      return "attribute";
    }
    String sanitized = attributeName.replaceAll("[^A-Za-z0-9_]", "_");
    if (sanitized.isBlank()) {
      sanitized = "attribute";
    }
    if (!Character.isLetter(sanitized.charAt(0)) && sanitized.charAt(0) != '_') {
      sanitized = "attr_" + sanitized;
    }
    return sanitized;
  }

  static {
    RbelHtmlRenderer.registerFacetRenderer(
        new RbelHtmlFacetRenderer() {
          @Override
          public boolean checkForRendering(RbelElement element) {
            return element.hasFacet(RbelLdapAttributesFacet.class);
          }

          @Override
          public ContainerTag performRendering(
              RbelElement element,
              Optional<String> key,
              RbelHtmlRenderingToolkit renderingToolkit) {
            var div = div();
            div.with(h2().withClass("title").withText("LDAP attributes: "));
            var facet = element.getFacet(RbelLdapAttributesFacet.class).orElseThrow();
            div.with(
                facet.getChildElements().stream()
                    .map(
                        child ->
                            p().with(b().withText(resolveDisplayName(child)).withText(": "))
                                .withText(child.getValue().printValue().orElse("")))
                    .toList());
            return div;
          }

          private String resolveDisplayName(Map.Entry<String, RbelElement> child) {
            return child
                .getValue()
                .getFacet(RbelLdapAttributeMetadataFacet.class)
                .map(RbelLdapAttributeMetadataFacet::getOriginalName)
                .orElse(child.getKey());
          }
        });
  }

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return this;
  }
}
