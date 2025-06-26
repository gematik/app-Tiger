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

import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.ancestorTitle;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.vertParentTitle;
import static j2html.TagCreator.*;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.RbelFacet;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import j2html.tags.ContainerTag;
import j2html.tags.DomContent;
import java.util.*;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@Builder
@RequiredArgsConstructor
public class RbelLdapFacet implements RbelFacet {

  public static final String MSG_ID_KEY = "msgId";
  public static final String PROTOCOL_OP_KEY = "protocolOp";
  public static final String TEXT_REPRESENTATION_KEY = "textRepresentation";
  public static final String ATTRIBUTES_KEY = "attributes";

  static {
    RbelHtmlRenderer.registerFacetRenderer(
        new RbelHtmlFacetRenderer() {
          @Override
          public boolean checkForRendering(RbelElement element) {
            return element.hasFacet(RbelLdapFacet.class);
          }

          @Override
          public ContainerTag performRendering(
              RbelElement element,
              Optional<String> key,
              RbelHtmlRenderingToolkit renderingToolkit) {
            final RbelLdapFacet facet = element.getFacetOrFail(RbelLdapFacet.class);

            final String messageId = facet.getChildElements().get(MSG_ID_KEY).getRawStringContent();
            final String protocolOperation =
                facet.getChildElements().get(PROTOCOL_OP_KEY).getRawStringContent();

            final Map<String, String> attributesMap = new LinkedHashMap<>();
            attributesMap.put("Message ID", messageId);
            attributesMap.put("Operation", protocolOperation);

            if (facet.getChildElements().containsKey(ATTRIBUTES_KEY)) {
              final RbelElement attributesElement = facet.getChildElements().get(ATTRIBUTES_KEY);
              if (attributesElement != null
                  && attributesElement.hasFacet(RbelLdapAttributesFacet.class)) {
                final RbelLdapAttributesFacet attributesFacet =
                    attributesElement.getFacetOrFail(RbelLdapAttributesFacet.class);

                for (Map.Entry<String, RbelElement> attribute : attributesFacet.entries()) {
                  String attributeName = "Attribute: " + attribute.getKey();
                  String value = attribute.getValue().getRawStringContent();
                  attributesMap.computeIfPresent(attributeName, (k, v) -> v + ", " + value);
                  attributesMap.putIfAbsent(attributeName, value);
                }
              }
            }

            List<DomContent> tableRows = new ArrayList<>();
            for (Map.Entry<String, String> entry : attributesMap.entrySet()) {
              tableRows.add(
                  tr(
                      td(pre().withText(entry.getKey()).withClass("key")),
                      td(pre().withText(entry.getValue()).withClass("value"))));
            }

            DomContent table = table().withClass("table").with(tbody().with(tableRows));

            return ancestorTitle()
                .with(
                    vertParentTitle()
                        .with(
                            div()
                                .withClass("tile is-child pe-2")
                                .with(pre("LDAP message").withClass("json language-json"))
                                .with(table)
                                .with(renderingToolkit.convertNested(element))));
          }
        });
  }

  private final RbelElement textRepresentation;
  private final RbelElement msgId;
  private final RbelElement protocolOp;
  private final RbelElement attributes;

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<RbelElement>()
        .with(TEXT_REPRESENTATION_KEY, textRepresentation)
        .with(MSG_ID_KEY, msgId)
        .with(PROTOCOL_OP_KEY, protocolOp)
        .withSkipIfNull(ATTRIBUTES_KEY, attributes);
  }
}
