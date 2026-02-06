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
import java.util.concurrent.atomic.AtomicInteger;
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
  public static final String CONTROLS_KEY = "controls";

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
            final RbelElement protocolOpElement = facet.getChildElements().get(PROTOCOL_OP_KEY);

            // Get operation type from the protocolOp facet
            String operationType =
                protocolOpElement
                    .getFacet(RbelLdapProtocolOpFacet.class)
                    .map(RbelLdapProtocolOpFacet::getOperationType)
                    .flatMap(RbelElement::printValue)
                    .orElse("Unknown");

            final Map<String, String> attributesMap = new LinkedHashMap<>();
            attributesMap.put("Message ID", messageId);
            attributesMap.put("Operation", operationType);

            // Add all protocolOp fields if present
            protocolOpElement
                .getFacet(RbelLdapProtocolOpFacet.class)
                .ifPresent(
                    protocolOpFacet -> {
                      addFieldIfPresent(attributesMap, "DN", protocolOpFacet.getDn());
                      addFieldIfPresent(
                          attributesMap, "Base Object", protocolOpFacet.getBaseObject());
                      addFieldIfPresent(attributesMap, "Scope", protocolOpFacet.getScope());
                      addFieldIfPresent(
                          attributesMap, "Deref Aliases", protocolOpFacet.getDerefAliases());
                      addFieldIfPresent(
                          attributesMap, "Size Limit", protocolOpFacet.getSizeLimit());
                      addFieldIfPresent(
                          attributesMap, "Time Limit", protocolOpFacet.getTimeLimit());
                      addFieldIfPresent(
                          attributesMap, "Types Only", protocolOpFacet.getTypesOnly());
                      addFieldIfPresent(attributesMap, "Filter", protocolOpFacet.getFilter());
                      addFieldIfPresent(attributesMap, "New RDN", protocolOpFacet.getNewRdn());
                      addFieldIfPresent(
                          attributesMap, "Delete Old RDN", protocolOpFacet.getDeleteOldRdn());
                      addFieldIfPresent(
                          attributesMap, "New Superior", protocolOpFacet.getNewSuperior());
                      addFieldIfPresent(attributesMap, "Version", protocolOpFacet.getVersion());
                      addFieldIfPresent(attributesMap, "Name", protocolOpFacet.getName());
                      addFieldIfPresent(attributesMap, "Simple", protocolOpFacet.getSimple());
                      addFieldIfPresent(
                          attributesMap, "Attribute Desc", protocolOpFacet.getAttributeDesc());
                      addFieldIfPresent(
                          attributesMap, "Assertion Value", protocolOpFacet.getAssertionValue());
                      addFieldIfPresent(
                          attributesMap, "Request Name", protocolOpFacet.getRequestName());
                      addFieldIfPresent(
                          attributesMap, "Response Name", protocolOpFacet.getResponseName());
                      addFieldIfPresent(
                          attributesMap, "Result Code", protocolOpFacet.getResultCode());
                      addFieldIfPresent(
                          attributesMap, "Matched DN", protocolOpFacet.getMatchedDN());
                      addFieldIfPresent(
                          attributesMap,
                          "Diagnostic Message",
                          protocolOpFacet.getDiagnosticMessage());
                      addFieldIfPresent(
                          attributesMap, "Server SASL Creds", protocolOpFacet.getServerSaslCreds());
                    });

            if (facet.getChildElements().containsKey(ATTRIBUTES_KEY)) {
              final RbelElement attributesElement = facet.getChildElements().get(ATTRIBUTES_KEY);
              if (attributesElement != null
                  && attributesElement.hasFacet(RbelLdapAttributesFacet.class)) {
                final RbelLdapAttributesFacet attributesFacet =
                    attributesElement.getFacetOrFail(RbelLdapAttributesFacet.class);

                for (Map.Entry<String, RbelElement> attribute : attributesFacet.entries()) {
                  String attributeName =
                      resolveAttributeName(attribute.getKey(), attribute.getValue());
                  String value = attribute.getValue().getRawStringContent();
                  attributesMap.computeIfPresent(attributeName, (k, v) -> v + ", " + value);
                  attributesMap.putIfAbsent(attributeName, value);
                }
              }
              // --- Enhancement: Render LDAP modifications ---
              if (attributesElement != null && attributesElement.getFacets() != null) {
                attributesElement.getFacets().stream()
                    .filter(RbelLdapModificationFacet.class::isInstance)
                    .map(RbelLdapModificationFacet.class::cast)
                    .forEach(
                        modFacet -> {
                          modFacet
                              .getOperation()
                              .printValue()
                              .ifPresent(op -> attributesMap.put("Modification Operation", op));
                          modFacet
                              .getAttributeName()
                              .printValue()
                              .ifPresent(s -> attributesMap.put("Modification Attribute Name", s));
                          var i = new AtomicInteger(0);
                          modFacet.getValues().stream()
                              .map(RbelElement::getRawStringContent)
                              .forEach(
                                  v ->
                                      attributesMap.put(
                                          "Modification Value " + i.incrementAndGet(), v));
                        });
              }
            }

            if (facet.getChildElements().containsKey(CONTROLS_KEY)) {
              final RbelElement controlsElement = facet.getChildElements().get(CONTROLS_KEY);
              if (controlsElement != null
                  && controlsElement.hasFacet(RbelLdapControlsFacet.class)) {
                final RbelLdapControlsFacet controlsFacet =
                    controlsElement.getFacetOrFail(RbelLdapControlsFacet.class);
                controlsFacet
                    .entries()
                    .forEach(
                        control ->
                            addFieldIfPresent(
                                attributesMap, "Control: " + control.oid(), control.element()));
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
  private final RbelElement controls;

  private static void addFieldIfPresent(Map<String, String> map, String key, RbelElement element) {
    if (element != null) {
      String value = element.printValue().orElseGet(element::getRawStringContent);
      if (value != null && !value.isEmpty()) {
        map.put(key, value);
      }
    }
  }

  private static String resolveAttributeName(String sanitizedKey, RbelElement valueElement) {
    return valueElement
        .getFacet(RbelLdapAttributeMetadataFacet.class)
        .map(RbelLdapAttributeMetadataFacet::getOriginalName)
        .map(name -> "Attribute: " + name)
        .orElse("Attribute: " + sanitizedKey);
  }

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<RbelElement>()
        .with(TEXT_REPRESENTATION_KEY, textRepresentation)
        .with(MSG_ID_KEY, msgId)
        .with(PROTOCOL_OP_KEY, protocolOp)
        .withSkipIfNull(ATTRIBUTES_KEY, attributes)
        .withSkipIfNull(CONTROLS_KEY, controls);
  }
}
