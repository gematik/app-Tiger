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

import static de.gematik.rbellogger.facets.websocket.RbelWebsocketFrameType.DATA_FRAME;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderer.showContentButtonAndDialog;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.*;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.addNotes;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.t2;
import static j2html.TagCreator.b;
import static j2html.TagCreator.div;
import static j2html.TagCreator.span;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.RbelFacet;
import de.gematik.rbellogger.data.core.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import de.gematik.test.tiger.common.util.TcpIpConnectionIdentifier;
import j2html.tags.ContainerTag;
import j2html.tags.DomContent;
import java.util.Optional;
import lombok.Builder;
import lombok.Data;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

@Data
@Builder
public class RbelWebsocketMessageFacet implements RbelFacet {

  static {
    RbelHtmlRenderer.registerFacetRenderer(
        new RbelHtmlFacetRenderer() {
          @Override
          public boolean checkForRendering(RbelElement element) {
            return element.hasFacet(RbelWebsocketMessageFacet.class);
          }

          @Override
          public ContainerTag performRendering(
              RbelElement element, Optional<String> key, RbelHtmlRenderingToolkit toolkit) {
            val messageFacet = element.getFacetOrFail(RbelWebsocketMessageFacet.class);
            return div()
                .with(addNotes(element, "mb-5"))
                .with(
                    ancestorTitle()
                        .with(
                            vertParentTitle()
                                .with(
                                    childBoxNotifTitle(CLS_HEADER)
                                        .with(t2("Header"))
                                        .with(
                                            toolkit.packAsInfoLine(
                                                "Flags",
                                                span(
                                                    booleanTo01(messageFacet.getFin0Bit()),
                                                    booleanTo01(messageFacet.getRsv1Bit()),
                                                    booleanTo01(messageFacet.getRsv2Bit()),
                                                    booleanTo01(messageFacet.getRsv3Bit()))))
                                        .with(
                                            toolkit.packAsInfoLine(
                                                "Masked",
                                                toolkit.formatHexAlike(
                                                    messageFacet
                                                        .getMasked()
                                                        .seekValue(Boolean.class)
                                                        .map(Object::toString)
                                                        .orElse("?"))))
                                        .with(
                                            toolkit.packAsInfoLine(
                                                "Opcode",
                                                toolkit.formatHex(messageFacet.getOpcode())))
                                        .with(
                                            toolkit.packAsInfoLine(
                                                "Frame Type",
                                                toolkit.formatHexAlike(
                                                    messageFacet
                                                        .getFrameType()
                                                        .seekValue(RbelWebsocketFrameType.class)
                                                        .map(Object::toString)
                                                        .orElse("?")))),
                                    childBoxNotifTitle(CLS_BODY)
                                        .with(
                                            showContentButtonAndDialog(
                                                messageFacet.getPayload(), toolkit))
                                        .with(t2("Payload"))
                                        .with(addNotes(messageFacet.getPayload()))
                                        .with(toolkit.convert(messageFacet.getPayload())))));
          }

          private DomContent booleanTo01(RbelElement bitValue) {
            return span(bitValue
                    .seekValue(Boolean.class)
                    .map(value -> value ? b("1") : b("0"))
                    .orElseGet(() -> b("-")))
                .withStyle("margin-right: 0.5em;");
          }
        });
  }

  private final RbelElement fin0Bit;
  private final RbelElement rsv1Bit;
  private final RbelElement rsv2Bit;
  private final RbelElement rsv3Bit;
  private final RbelElement opcode;
  private final RbelElement masked;
  private final RbelElement payloadLength;
  private final RbelElement payload;
  private final RbelElement extensions;
  private final RbelElement frameType;

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<RbelElement>()
        .with("fin0Bit", fin0Bit)
        .with("rsv1Bit", rsv1Bit)
        .with("rsv2Bit", rsv2Bit)
        .with("rsv3Bit", rsv3Bit)
        .with("opcode", opcode)
        .with("masked", masked)
        .with("payloadLength", payloadLength)
        .with("payload", payload)
        .with("extensions", extensions)
        .with("frameType", frameType);
  }

  @Override
  public Optional<String> printShortDescription(RbelElement element) {
    return Optional.of(
        "WebSocket("
            + element
                .getFacet(RbelTcpIpMessageFacet.class)
                .map(RbelTcpIpMessageFacet::getTcpIpConnectionIdentifier)
                .map(TcpIpConnectionIdentifier::printDirectionSymbol)
                .orElse("?")
            + "): "
            + printPayload());
  }

  private @Nullable String printPayload() {
    return frameType
        .seekValue(RbelWebsocketFrameType.class)
        .map(
            type -> {
              if (type == DATA_FRAME) {
                return "'" + StringUtils.abbreviate(payload.getRawStringContent(), 60) + "'";
              } else {
                return type.toString();
              }
            })
        .orElse("<unknown>");
  }
}
