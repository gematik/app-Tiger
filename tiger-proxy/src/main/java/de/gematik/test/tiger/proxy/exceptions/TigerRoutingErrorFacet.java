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
package de.gematik.test.tiger.proxy.exceptions;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.*;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.addNotes;
import static j2html.TagCreator.*;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.RbelFacet;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import j2html.tags.ContainerTag;
import java.util.Collections;
import java.util.Optional;
import lombok.*;
import org.apache.commons.lang3.exception.ExceptionUtils;

@EqualsAndHashCode
public class TigerRoutingErrorFacet implements RbelFacet {

  static {
    RbelHtmlRenderer.registerFacetRenderer(
        new RbelHtmlFacetRenderer() {
          @Override
          public boolean checkForRendering(RbelElement element) {
            return element.hasFacet(TigerRoutingErrorFacet.class);
          }

          @Override
          public ContainerTag performRendering(
              RbelElement element, Optional<String> key, RbelHtmlRenderingToolkit context) {
            val routingException =
                element.getFacetOrFail(TigerRoutingErrorFacet.class).getException();
            return div()
                .with(addNotes(element, "mb-5"))
                .with(
                    ancestorTitle()
                        .with(
                            vertParentTitle()
                                .with(
                                    childBoxNotifTitle(CLS_HEADER)
                                        .with(t2("TRANSMISSION ERROR"))
                                        .with(
                                            context.packAsInfoLine(
                                                "Error Message", p(routingException.getMessage())))
                                        .with(
                                            context.packAsInfoLine(
                                                "Timestamp",
                                                p(routingException.getTimestamp().toString())))
                                        .with(
                                            Optional.ofNullable(routingException.getSenderAddress())
                                                .map(
                                                    sender ->
                                                        context.packAsInfoLine(
                                                            "Sender", p(sender.toString())))
                                                .orElse(Collections.emptyList()))
                                        .with(
                                            Optional.ofNullable(
                                                    routingException.getReceiverAddress())
                                                .map(
                                                    receiver ->
                                                        context.packAsInfoLine(
                                                            "Receiver", p(receiver.toString())))
                                                .orElse(Collections.emptyList())))
                                .with(
                                    collapsibleBox(
                                        "Stacktrace",
                                        buildScrollableTextbox(
                                            ExceptionUtils.getStackTrace(routingException))))));
          }
        });
  }

  @Getter private final TigerProxyRoutingException exception;
  @Getter private RbelElement errorElement;
  private RbelMultiMap<RbelElement> childMap = new RbelMultiMap<>();

  public TigerRoutingErrorFacet(TigerProxyRoutingException exception) {
    this.exception = exception;
  }

  public void setErrorElement(RbelElement errorElement) {
    this.errorElement = errorElement;
    this.childMap = new RbelMultiMap<RbelElement>().with("error", errorElement);
  }

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return childMap;
  }
}
