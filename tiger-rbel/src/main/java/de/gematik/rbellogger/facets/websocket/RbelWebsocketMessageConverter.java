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
import java.util.zip.Inflater;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.tuple.Pair;

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
    fin0Bit = extractByteNumber(message.getContent().get(0), 7) != 0;
    rsv1Bit = (extractByteNumber(message.getContent().get(0), 6)) != 0;
    rsv2Bit = (extractByteNumber(message.getContent().get(0), 5)) != 0;
    rsv3Bit = (extractByteNumber(message.getContent().get(0), 4)) != 0;
    opcode = message.getContent().get(0) & 0b00001111;
    masked = (extractByteNumber(message.getContent().get(1), 7)) != 0;
    int payloadLength = message.getContent().get(1) & 0b01111111;
    lengthOfExtendedPayloadLength = calculateExtendedPayloadLength(payloadLength);

    val actualPayloadLength = calculateActualPayloadLength(payloadLength);
    val messageLength = 2 + lengthOfExtendedPayloadLength + (masked ? 4 : 0) + actualPayloadLength;
    if (messageLength > message.getContent().size()) {
      message.addFacet(
          RbelNoteFacet.builder()
              .value(
                  "Websocket message length ("
                      + messageLength
                      + ") exceeds actual content length ("
                      + message.getContent().size()
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
        .map(originalClient::equals)
        .orElse(true)) {
      message.addFacet(new RbelResponseFacet("Websocket"));
    } else {
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
        extensionMap.keySet().stream().anyMatch("permessage-deflate"::equalsIgnoreCase);
  }

  private int extractByteNumber(byte value, int target) {
    return value & ((1 << target) & 0xff);
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
        return buffer.getShort();
      } else {
        return (int) buffer.getLong();
      }
    }
  }

  private RbelElement extractPayloadElement() {
    RbelContent rawPayloadBytes =
        message
            .getContent()
            .subArray(
                2 + (masked ? 4 : 0) + lengthOfExtendedPayloadLength, message.getContent().size());
    if (masked) {
      val maskingKey =
          message
              .getContent()
              .toByteArray(
                  2 + lengthOfExtendedPayloadLength, 2 + lengthOfExtendedPayloadLength + 4);
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

    byte[] joined = new byte[compressedData.size() + TAIL.length];
    System.arraycopy(compressedData.toByteArray(), 0, joined, 0, compressedData.size());
    System.arraycopy(TAIL, 0, joined, compressedData.size(), TAIL.length);
    inflater.setInput(joined);

    while (!inflater.needsInput()) {
      int count = inflater.inflate(buffer);
      outputStream.write(buffer, 0, count);
    }

    return RbelContent.of(outputStream.toByteArray());
  }
}
