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
import de.gematik.rbellogger.data.core.RbelRequestFacet;
import de.gematik.rbellogger.data.core.RbelResponseFacet;
import de.gematik.rbellogger.data.core.RbelRootFacet;
import de.gematik.rbellogger.data.core.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.util.RbelContent;
import java.nio.ByteBuffer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@Slf4j
@RequiredArgsConstructor
public class RbelWebsocketMessageConverter {
  private final RbelElement message;
  private final RbelConversionExecutor converter;
  private final RbelElement previousMessage;
  private boolean masked;
  private int lengthOfExtendedPayloadLength;

  public void parseWebsocketMessage() {
    val fin0Bit = (message.getContent().get(0) & 0b10000000) != 0;
    val rsv1Bit = (message.getContent().get(0) & 0b01000000) != 0;
    val rsv2Bit = (message.getContent().get(0) & 0b00100000) != 0;
    val rsv3Bit = (message.getContent().get(0) & 0b00010000) != 0;
    val opcode = message.getContent().get(0) & 0b00001111;
    masked = (message.getContent().get(1) & 0b10000000) != 0;
    val payloadLength = message.getContent().get(1) & 0b01111111;
    lengthOfExtendedPayloadLength = calculateExtendedPayloadLength(payloadLength);

    val actualPayloadLength = calculateActualPayloadLength(payloadLength);
    val messageLength = 2 + lengthOfExtendedPayloadLength + (masked ? 4 : 0) + actualPayloadLength;
    message.setUsedBytes(messageLength);
    val payloadElement = extractPayloadElement();
    val websocketFacet =
        RbelWebsocketMessageFacet.builder()
            .fin0Bit(RbelElement.wrap(message, fin0Bit))
            .rsv1Bit(RbelElement.wrap(message, rsv1Bit))
            .rsv2Bit(RbelElement.wrap(message, rsv2Bit))
            .rsv3Bit(RbelElement.wrap(message, rsv3Bit))
            .opcode(RbelElement.wrap(message, opcode))
            .masked(RbelElement.wrap(message, masked))
            .payloadLength(RbelElement.wrap(message, payloadLength))
            .payload(payloadElement)
            .build();
    message.addFacet(websocketFacet);
    message.addFacet(new RbelRootFacet<>(websocketFacet));
    if (message
        .getFacet(RbelTcpIpMessageFacet.class)
        .map(tcpFacet -> tcpFacet.isSameDirectionAs(previousMessage))
        .filter(facet -> previousMessage.hasFacet(RbelRequestFacet.class))
        .orElse(false)) {
      message.addFacet(new RbelRequestFacet("Websocket", false));
    } else {
      message.addFacet(new RbelResponseFacet("Websocket"));
    }
    converter.convertElement(payloadElement);
    log.debug(
        "Parsed Websocket message with opcode {} and payload length {}\n\n{}",
        websocketFacet.getOpcode().getRawContent(),
        actualPayloadLength,
        payloadElement.printTreeStructure());
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
      return RbelElement.builder().rawContent(unmaskedPayloadBytes).parentNode(message).build();
    }
    return RbelElement.builder().content(rawPayloadBytes).parentNode(message).build();
  }
}
