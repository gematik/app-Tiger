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
package de.gematik.rbellogger.facets.http2;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderer.showContentButtonAndDialog;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.*;
import static j2html.TagCreator.div;
import static j2html.TagCreator.text;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.RbelFacet;
import de.gematik.rbellogger.data.core.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import j2html.tags.ContainerTag;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.Builder;
import lombok.Getter;
import lombok.val;

/** Facet representing a single HTTP/2 frame's structure. */
@Getter
@Builder
public class RbelHttp2FrameFacet implements RbelFacet {

  static {
    RbelHtmlRenderer.registerFacetRenderer(
        new RbelHtmlFacetRenderer() {
          @Override
          public boolean checkForRendering(RbelElement element) {
            return element.hasFacet(RbelHttp2FrameFacet.class);
          }

          @Override
          public ContainerTag performRendering(
              RbelElement element, Optional<String> key, RbelHtmlRenderingToolkit toolkit) {
            val facet = element.getFacetOrFail(RbelHttp2FrameFacet.class);
            val renderFrameType = facet.getFrameType().printValue().orElseThrow();

            var headerBox =
                childBoxNotifTitle(CLS_HEADER)
                    .with(t2("HTTP/2 Frame"))
                    .with(toolkit.packAsInfoLine("Type", text(renderFrameType)))
                    .with(
                        toolkit.packAsInfoLine(
                            "Stream ID", text(facet.getStreamId().printValue().orElseThrow())));

            if (facet.getFlags() != null) {
              headerBox.with(
                  toolkit.packAsInfoLine(
                      "Flags", text(facet.getFlags().printValue().orElseThrow())));
            }
            if (facet.getPayloadLength() != null) {
              headerBox.with(
                  toolkit.packAsInfoLine(
                      "Payload Length",
                      text(facet.getPayloadLength().printValue().orElseThrow() + " bytes")));
            }

            var container =
                div()
                    .with(addNotes(element, "mb-5"))
                    .with(ancestorTitle().with(vertParentTitle().with(headerBox)));

            if (facet.getPayload() != null) {
              container.with(
                  ancestorTitle()
                      .with(
                          vertParentTitle()
                              .with(
                                  childBoxNotifTitle(CLS_BODY)
                                      .with(showContentButtonAndDialog(facet.getPayload(), toolkit))
                                      .with(t2("Payload"))
                                      .with(addNotes(facet.getPayload()))
                                      .with(toolkit.convert(facet.getPayload())))));
            }

            return container;
          }
        });
  }

  private final RbelElement frameType;
  private final RbelElement flags;
  private final RbelElement streamId;
  private final RbelElement payloadLength;
  private final RbelElement payload;

  @Getter(lazy = true)
  private final RbelMultiMap<RbelElement> childElements =
      new RbelMultiMap<RbelElement>()
          .with("frameType", frameType)
          .withSkipIfNull("flags", flags)
          .with("streamId", streamId)
          .withSkipIfNull("payloadLength", payloadLength)
          .withSkipIfNull("payload", payload);

  @Override
  public Optional<String> printShortDescription(RbelElement element) {
    var frameTypeName = frameType.printValue().orElse("UNKNOWN");
    return Optional.of("HTTP/2 " + frameTypeName + " frame, stream " + streamId.printValue());
  }

  /**
   * Finds all HTTP/2 frames in the given messages that belong to the same stream as the given frame
   * (same stream ID and same connection endpoints). This includes the frame itself.
   *
   * @param frame a message element with an {@link RbelHttp2FrameFacet}
   * @param allMessages the messages to search
   * @return all frames on the same stream and connection, in message-list order
   */
  public static List<RbelElement> findStreamFrames(
      RbelElement frame, Collection<RbelElement> allMessages) {
    var facet = frame.getFacetOrFail(RbelHttp2FrameFacet.class);
    int targetStreamId = facet.getStreamId().seekValue(Integer.class).orElseThrow();

    return allMessages.stream()
        .filter(msg -> msg.hasFacet(RbelHttp2FrameFacet.class))
        .filter(
            msg -> {
              if (msg == frame) {
                return true;
              }
              var otherFacet = msg.getFacetOrFail(RbelHttp2FrameFacet.class);
              int otherStreamId = otherFacet.getStreamId().seekValue(Integer.class).orElse(-1);
              return otherStreamId == targetStreamId
                  && RbelTcpIpMessageFacet.haveSameConnection(frame, msg);
            })
        .toList();
  }
}
