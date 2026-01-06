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
package de.gematik.rbellogger.facets.websocket;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderer.showContentButtonAndDialog;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.ancestorTitle;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.t1ms;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.vertParentTitle;
import static j2html.TagCreator.*;
import static j2html.TagCreator.pre;

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
public class RbelStompFacet implements RbelFacet {

  public static final String COMMAND_KEY = "command";
  public static final String HEADERS_KEY = "headers";
  public static final String BODY_KEY = "body";

  static {
    RbelHtmlRenderer.registerFacetRenderer(
        new RbelHtmlFacetRenderer() {
          @Override
          public boolean checkForRendering(RbelElement element) {
            return element.hasFacet(RbelStompFacet.class);
          }

          @Override
          public ContainerTag performRendering(
              RbelElement element,
              Optional<String> key,
              RbelHtmlRenderingToolkit renderingToolkit) {
            final RbelStompFacet facet = element.getFacetOrFail(RbelStompFacet.class);

            final Map<String, String> attributesMap = new LinkedHashMap<>();
            final RbelElement headersElement = facet.getChildElements().get(HEADERS_KEY);
            if (headersElement != null && headersElement.hasFacet(RbelStompHeadersFacet.class)) {
              final RbelStompHeadersFacet headersFacet =
                  headersElement.getFacetOrFail(RbelStompHeadersFacet.class);

              for (Map.Entry<String, RbelElement> header : headersFacet.entries()) {
                String attributeName = header.getKey();
                String value = header.getValue().getRawStringContent();
                attributesMap.computeIfPresent(attributeName, (k, v) -> v + ", " + value);
                attributesMap.putIfAbsent(attributeName, value);
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

            return div()
                .with(
                    t1ms("STOMP")
                        .with(span("(" + facet.getCommand().getRawStringContent() + ")"))
                        .with(showContentButtonAndDialog(element, renderingToolkit)))
                .with(table)
                .with(br())
                .with(ancestorTitle())
                .with(vertParentTitle().with(renderingToolkit.convert(facet.getBody())));
            //
            // .with(vertParentTitle().with(renderingToolkit.convertNested(element)));
          }
        });
  }

  private final RbelElement command;
  private final RbelElement headers;
  private final RbelElement body;

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<RbelElement>()
        .with(COMMAND_KEY, command)
        .with(HEADERS_KEY, headers)
        .with(BODY_KEY, body);
  }
}
