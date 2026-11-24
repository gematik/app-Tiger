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

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConversionPhase;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.converter.ConverterInfo;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMessageKind;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.data.core.RbelBinaryFacet;
import de.gematik.rbellogger.data.core.RbelRequestFacet;
import de.gematik.rbellogger.data.core.RbelResponseFacet;
import de.gematik.rbellogger.data.core.RbelRootFacet;
import de.gematik.rbellogger.data.core.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.data.core.RbelValueFacet;
import de.gematik.rbellogger.facets.http.RbelHttpMessageFacet;
import de.gematik.rbellogger.facets.http.RbelHttpRequestConverter;
import de.gematik.rbellogger.facets.http.RbelHttpResponseConverter;
import de.gematik.rbellogger.util.BinaryClassifier;
import de.gematik.rbellogger.util.RbelContent;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Parses raw HTTP/2 binary frames into a structural representation. Does NOT perform HPACK
 * decompression or stream reassembly — only exposes the frame structure (type, flags, stream ID,
 * payload length, raw payload).
 */
@Slf4j
@ConverterInfo(
    dependsOn = {RbelHttpResponseConverter.class, RbelHttpRequestConverter.class},
    onlyActivateFor = "http2frames")
public class RbelHttp2FrameConverter extends RbelConverterPlugin {

  private static final byte[] H2_PREFACE =
      "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII);

  private static final int FRAME_HEADER_LENGTH = 9;

  // Frame header field offsets (within the 9-byte header)
  private static final int OFFSET_LENGTH_HIGH = 0;
  private static final int OFFSET_TYPE = 3;
  private static final int OFFSET_FLAGS = 4;
  private static final int OFFSET_STREAM_ID = 5;

  // Stream ID reserved bit mask (most significant bit of the 4-byte stream ID field)
  private static final int STREAM_ID_RESERVED_BIT_MASK = 0x80000000;

  // Flag bit masks (RFC 7540 §6)
  private static final int FLAG_END_STREAM_OR_ACK = 0x01;
  private static final int FLAG_END_HEADERS = 0x04;
  private static final int FLAG_PADDED = 0x08;
  private static final int FLAG_PRIORITY = 0x20;

  @Override
  public RbelConversionPhase getPhase() {
    return RbelConversionPhase.PROTOCOL_PARSING;
  }

  @Override
  public void consumeElement(RbelElement rbelElement, RbelConversionExecutor converter) {
    if (rbelElement.getParentNode() != null
        || rbelElement.hasFacet(RbelHttpMessageFacet.class)
        || !rbelElement.hasFacet(RbelTcpIpMessageFacet.class)) {
      return;
    }

    RbelContent content = rbelElement.getContent();
    if (content == null || content.size() < FRAME_HEADER_LENGTH) {
      return;
    }

    int offset = 0;

    if (content.startsWith(H2_PREFACE)) {
      offset = H2_PREFACE.length;
    }

    if (content.size() - offset < FRAME_HEADER_LENGTH) {
      return;
    }
    if (!looksLikeH2Frame(content, offset)) {
      return;
    }

    parseFrame(rbelElement, offset);
  }

  private static void parseFrame(RbelElement rbelElement, int offset) {
    var content = rbelElement.getContent();

    byte[] header = content.toByteArray(offset, offset + FRAME_HEADER_LENGTH);
    ByteBuffer headerBuffer = ByteBuffer.wrap(header);
    int payloadLength = headerBuffer.getInt(OFFSET_LENGTH_HIGH) >>> 8;

    int frameEnd = offset + FRAME_HEADER_LENGTH + payloadLength;
    if (frameEnd > content.size()) {
      return;
    }

    int type = header[OFFSET_TYPE] & 0xFF;
    int flags = header[OFFSET_FLAGS] & 0xFF;
    int streamId = headerBuffer.getInt(OFFSET_STREAM_ID) & ~STREAM_ID_RESERVED_BIT_MASK;

    val frameType = Http2FrameType.fromValue(type);

    val facetBuilder =
        RbelHttp2FrameFacet.builder()
            .frameType(
                RbelElement.create(
                        content.subArray(offset + OFFSET_TYPE, offset + OFFSET_TYPE + 1),
                        rbelElement)
                    .addFacet(RbelValueFacet.of(frameType)))
            .streamId(RbelElement.wrap(rbelElement, streamId));

    if (flags != 0) {
      facetBuilder.flags(
          RbelElement.create(
                  content.subArray(offset + OFFSET_FLAGS, offset + OFFSET_FLAGS + 1), rbelElement)
              .addFacet(RbelValueFacet.of(decodeFlagNames(frameType, flags))));
    }
    if (payloadLength > 0) {
      var payloadElement =
          RbelElement.create(content.subArray(offset + FRAME_HEADER_LENGTH, frameEnd), rbelElement);
      // Non-DATA frame payloads (HPACK headers, settings, etc.) are always binary.
      // DATA frame payloads might be text, so use BinaryClassifier.
      if (frameType != Http2FrameType.DATA
          || BinaryClassifier.isBinary(payloadElement.getContent())) {
        payloadElement.addFacet(new RbelBinaryFacet());
      }
      facetBuilder
          .payloadLength(RbelElement.wrap(rbelElement, payloadLength))
          .payload(payloadElement);
    }

    val frameFacet = facetBuilder.build();
    rbelElement.addFacet(frameFacet);
    rbelElement.addFacet(new RbelRootFacet<>(frameFacet));

    addRequestOrResponseFacet(rbelElement, frameType);

    rbelElement.setUsedBytes(frameEnd);
  }

  private static void addRequestOrResponseFacet(RbelElement rbelElement, Http2FrameType frameType) {
    var messageKind =
        rbelElement
            .getFacet(RbelMessageMetadata.class)
            .flatMap(RbelMessageMetadata.MESSAGE_KIND::getValue)
            .orElse(null);
    if (messageKind == null) {
      return;
    }
    String menuInfo = frameType.name() + " frame";
    if (messageKind == RbelMessageKind.REQUEST) {
      rbelElement.addFacet(new RbelRequestFacet(menuInfo, false));
    } else {
      rbelElement.addFacet(RbelResponseFacet.builder().menuInfoString(menuInfo).build());
    }
  }

  /** Heuristic: check if the data at offset looks like a valid h2 frame header. */
  private static boolean looksLikeH2Frame(RbelContent content, int offset) {
    int type = content.get(offset + OFFSET_TYPE) & 0xFF;
    return type <= Http2FrameType.CONTINUATION.getValue();
  }

  private static List<String> decodeFlagNames(Http2FrameType frameType, int flags) {
    List<String> flagNames = new ArrayList<>();

    if ((flags & FLAG_END_STREAM_OR_ACK) != 0) {
      if (frameType == Http2FrameType.DATA || frameType == Http2FrameType.HEADERS) {
        flagNames.add("END_STREAM");
      } else if (frameType == Http2FrameType.SETTINGS || frameType == Http2FrameType.PING) {
        flagNames.add("ACK");
      }
    }
    if ((flags & FLAG_END_HEADERS) != 0 && frameType == Http2FrameType.HEADERS) {
      flagNames.add("END_HEADERS");
    }
    if ((flags & FLAG_PADDED) != 0
        && (frameType == Http2FrameType.DATA || frameType == Http2FrameType.HEADERS)) {
      flagNames.add("PADDED");
    }
    if ((flags & FLAG_PRIORITY) != 0 && frameType == Http2FrameType.HEADERS) {
      flagNames.add("PRIORITY");
    }

    return flagNames;
  }
}
