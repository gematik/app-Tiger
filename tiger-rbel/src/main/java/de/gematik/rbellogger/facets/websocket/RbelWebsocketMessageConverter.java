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

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.*;
import de.gematik.rbellogger.data.core.RbelNoteFacet.NoteStyling;
import de.gematik.rbellogger.util.RbelContent;
import de.gematik.rbellogger.util.RbelSocketAddress;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.zip.Inflater;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.tuple.Pair;
import org.bouncycastle.util.Arrays;

@Slf4j
@RequiredArgsConstructor
public class RbelWebsocketMessageConverter {
  private static final byte[] TAIL = {0x00, 0x00, (byte) 0xFF, (byte) 0xFF};

  private final RbelElement message;
  private final RbelConversionExecutor converter;
  private final RbelMultiMap<RbelElement> extensionMap;
  private final Inflater inflater;
  private final RbelSocketAddress originalClient;
  private boolean masked;
  private int lengthOfExtendedPayloadLength;
  private boolean messageCompression;
  private boolean fin0Bit;
  private boolean rsv1Bit;
  private boolean rsv2Bit;
  private boolean rsv3Bit;
  private int opcode;

  public void parseWebsocketMessage() {
    getUsedExtensions();
    RbelContent content = message.getContent();
    byte firstByte = content.get(0);
    byte secondByte = content.get(1);
    fin0Bit = isBitSet(firstByte, 7);
    rsv1Bit = isBitSet(firstByte, 6);
    rsv2Bit = isBitSet(firstByte, 5);
    rsv3Bit = isBitSet(firstByte, 4);
    opcode = firstByte & 0b00001111;
    masked = isBitSet(secondByte, 7);
    int payloadLength = secondByte & 0b01111111;
    lengthOfExtendedPayloadLength = calculateExtendedPayloadLength(payloadLength);

    val actualPayloadLength = calculateActualPayloadLength(payloadLength);
    val messageLength = 2 + lengthOfExtendedPayloadLength + (masked ? 4 : 0) + actualPayloadLength;
    if (messageLength > content.size()) {
      message.addFacet(
          RbelNoteFacet.builder()
              .value(
                  "Websocket message length ("
                      + messageLength
                      + ") exceeds actual content length ("
                      + content.size()
                      + ")")
              .style(NoteStyling.ERROR)
              .build());
      return;
    }
    message.setUsedBytes(messageLength);
    val payloadElement = extractPayloadElement();
    val websocketFacet =
        RbelWebsocketMessageFacet.builder()
            .fin0Bit(RbelElement.wrap(message, fin0Bit))
            .rsv1Bit(RbelElement.wrap(message, rsv1Bit))
            .rsv2Bit(RbelElement.wrap(message, rsv2Bit))
            .rsv3Bit(RbelElement.wrap(message, rsv3Bit))
            .opcode(RbelElement.wrap(new byte[] {(byte) opcode}, message, opcode))
            .masked(RbelElement.wrap(message, masked))
            .payloadLength(RbelElement.wrap(message, payloadLength))
            .payload(payloadElement)
            .extensions(
                RbelMapFacet.wrap(message, el -> copyExtensionMap(extensionMap, el), new byte[] {}))
            .frameType(RbelElement.wrap(message, getFrameType(payloadElement)))
            .build();
    message.addFacet(websocketFacet);
    message.addFacet(new RbelRootFacet<>(websocketFacet));
    if (message
        .getFacet(RbelTcpIpMessageFacet.class)
        .flatMap(RbelTcpIpMessageFacet::getReceiverHostname)
        .map(originalClient::isSameAddress)
        .orElse(true)) {
      message.addFacet(new RbelResponseFacet("Websocket"));
    } else if (message
        .getFacet(RbelTcpIpMessageFacet.class)
        .flatMap(RbelTcpIpMessageFacet::getSenderHostname)
        .map(originalClient::isSameAddress)
        .orElse(true)) {
      message.addFacet(new RbelRequestFacet("Websocket", false));
    }
    converter.convertElement(payloadElement);
    log.debug(
        "Parsed Websocket message with opcode {} and payload length {}\n\n{}",
        websocketFacet.getOpcode().getRawContent(),
        actualPayloadLength,
        payloadElement.printTreeStructure());
  }

  private RbelMultiMap<RbelElement> copyExtensionMap(
      RbelMultiMap<RbelElement> extensionMap, RbelElement parent) {
    return extensionMap.stream()
        .map(
            oldEl ->
                Pair.of(
                    oldEl.getKey(),
                    RbelElement.wrap(parent, oldEl.getValue().seekValue().orElse(null))))
        .collect(RbelMultiMap.COLLECTOR);
  }

  private RbelWebsocketFrameType getFrameType(RbelElement payloadElement) {
    if (payloadElement.getContent().size() > 125) {
      return RbelWebsocketFrameType.DATA_FRAME;
    }
    if (opcode == 0x1) {
      return RbelWebsocketFrameType.DATA_FRAME;
    } else if (opcode == 0x8) {
      return RbelWebsocketFrameType.CLOSE_FRAME;
    } else if (opcode == 0x9) {
      return RbelWebsocketFrameType.PING_FRAME;
    } else if (opcode == 0xA) {
      return RbelWebsocketFrameType.PONG_FRAME;
    } else {
      return RbelWebsocketFrameType.UNKOWN_CONTROL_FRAME;
    }
  }

  private void getUsedExtensions() {
    messageCompression =
        extensionMap.keySet().stream()
            .map(k -> k == null ? "" : k.toLowerCase())
            .anyMatch(k -> k.contains("permessage-deflate"));
  }

  private boolean isBitSet(byte value, int target) {
    return (value & ((1 << target) & 0xff)) != 0;
  }

  private static int calculateExtendedPayloadLength(int payloadLength) {
    if (payloadLength == 126) {
      /* 16 bit */
      return 2;
    } else if (payloadLength == 127) {
      /* 64 bit */
      return 8;
    } else {
      return 0;
    }
  }

  private int calculateActualPayloadLength(int payloadLength) {
    if (payloadLength < 126) {
      return payloadLength;
    } else {
      val buffer =
          ByteBuffer.wrap(
              message.getContent().subArray(2, 2 + lengthOfExtendedPayloadLength).toByteArray());
      if (payloadLength == 126) {
        return Short.toUnsignedInt(buffer.getShort());
      } else {
        long l = buffer.getLong();
        if (l > Integer.MAX_VALUE) {
          return Integer.MAX_VALUE;
        }
        return (int) l;
      }
    }
  }

  private RbelElement extractPayloadElement() {
    final int extendedLengthOffset = 2;
    final int extendedLengthBytes = lengthOfExtendedPayloadLength;
    final int maskingKeyOffset = extendedLengthOffset + extendedLengthBytes;
    final int payloadOffset = maskingKeyOffset + (masked ? 4 : 0);

    val content = message.getContent();

    RbelContent rawPayloadBytes = content.subArray(payloadOffset, content.size());

    if (masked) {
      val maskingKey = content.toByteArray(maskingKeyOffset, maskingKeyOffset + 4);
      final byte[] maskedPayloadBytes = rawPayloadBytes.toByteArray();
      final byte[] unmaskedPayloadBytes = new byte[maskedPayloadBytes.length];
      for (int i = 0; i < unmaskedPayloadBytes.length; i++) {
        unmaskedPayloadBytes[i] = (byte) (maskedPayloadBytes[i] ^ maskingKey[i % 4]);
      }
      rawPayloadBytes = RbelContent.of(unmaskedPayloadBytes);
    }
    if (messageCompression && rsv1Bit) {
      rawPayloadBytes = decompress(rawPayloadBytes);
    }
    return RbelElement.builder().content(rawPayloadBytes).parentNode(message).build();
  }

  @SneakyThrows
  private RbelContent decompress(RbelContent compressedData) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    byte[] buffer = new byte[1024];

    byte[] joined = Arrays.concatenate(compressedData.toByteArray(), TAIL);
    resetInflaterIfNecessary();

    inflater.setInput(joined);

    while (!inflater.finished() && !inflater.needsInput()) {
      int count = inflater.inflate(buffer);
      if (count <= 0) {
        break; // avoid potential infinite loop
      }
      outputStream.write(buffer, 0, count);
    }

    return RbelContent.of(outputStream.toByteArray());
  }

  private void resetInflaterIfNecessary() {
    final boolean clientNoContext =
        extensionMap.stream()
            .map(Map.Entry::getValue)
            .anyMatch(
                el -> {
                  if (el == null || el.getRawStringContent() == null) return false;
                  String lc = el.getRawStringContent().toLowerCase();
                  return lc.contains("client_no_context_takeover")
                      || lc.contains("no_context_takeover");
                });
    final boolean serverNoContext =
        extensionMap.stream()
            .map(Map.Entry::getValue)
            .anyMatch(
                el -> {
                  if (el == null || el.getRawStringContent() == null) return false;
                  String lc = el.getRawStringContent().toLowerCase();
                  return lc.contains("server_no_context_takeover")
                      || lc.contains("no_context_takeover");
                });

    final boolean fromClient =
        message
            .getFacet(RbelTcpIpMessageFacet.class)
            .flatMap(RbelTcpIpMessageFacet::getSenderHostname)
            .map(originalClient::isSameAddress)
            .orElse(false);

    final boolean needReset = (fromClient && clientNoContext) || (!fromClient && serverNoContext);

    if (needReset) {
      inflater.reset();
    }
  }
}
