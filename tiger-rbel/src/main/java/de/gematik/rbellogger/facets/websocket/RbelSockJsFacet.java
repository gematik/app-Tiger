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
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.*;
import static j2html.TagCreator.div;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.RbelFacet;
import de.gematik.rbellogger.data.core.RbelListFacet;
import de.gematik.rbellogger.facets.jackson.RbelJsonFacet;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import de.gematik.rbellogger.util.RbelPathAble;
import j2html.tags.ContainerTag;
import java.util.Optional;
import lombok.Builder;
import lombok.Data;
import lombok.val;

@Data
@Builder
public class RbelSockJsFacet implements RbelFacet {

  static {
    RbelHtmlRenderer.registerFacetRenderer(
        new RbelHtmlFacetRenderer() {
          @Override
          public boolean checkForRendering(RbelElement element) {
            return element.hasFacet(RbelSockJsFacet.class);
          }

          @Override
          public ContainerTag performRendering(
              RbelElement element, Optional<String> key, RbelHtmlRenderingToolkit toolkit) {
            val messageFacet = element.getFacetOrFail(RbelSockJsFacet.class);
            return div()
                .with(
                    ancestorTitle()
                        .with(
                            vertParentTitle()
                                .with(
                                    childBoxNotifTitle(CLS_HEADER)
                                        .with(t2("SockJS"))
                                        .with(showContentButtonAndDialog(element, toolkit))
                                        .with(addNotes(element))
                                        .with(
                                            childBoxNotifTitle(CLS_BODY)
                                                .with(renderContent(toolkit, messageFacet))))));
          }

          private ContainerTag renderContent(
              RbelHtmlRenderingToolkit toolkit, RbelSockJsFacet messageFacet) {
            if (messageFacet.getContent() == null) {
              return div();
            }
            if (messageFacet.getContent().hasFacet(RbelJsonFacet.class)
                && messageFacet.getContent().hasFacet(RbelListFacet.class)) {
              return div()
                  .with(addNotes(messageFacet.getContent()))
                  .with(
                      messageFacet
                          .getContent()
                          .getChildNodesStream()
                          .flatMap(RbelPathAble::getChildNodesStream)
                          .map(RbelElement.class::cast)
                          .map(toolkit::convert)
                          .toList());
            }
            return childBoxNotifTitle(CLS_BODY)
                .with(t2("Payload"))
                .with(addNotes(messageFacet.getContent()))
                .with(toolkit.convert(messageFacet.getContent()));
          }
        });
  }

  private final RbelElement type;
  private final RbelElement qualifier;
  private final RbelElement content;

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<RbelElement>()
        .with("type", type)
        .with("qualifier", qualifier)
        .with("content", content);
  }
}
