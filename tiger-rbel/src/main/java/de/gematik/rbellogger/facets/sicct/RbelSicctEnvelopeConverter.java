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
package de.gematik.rbellogger.facets.sicct;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.converter.ConverterInfo;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelNoteFacet;
import de.gematik.rbellogger.data.core.RbelRequestFacet;
import de.gematik.rbellogger.data.core.RbelResponseFacet;
import de.gematik.rbellogger.data.core.RbelRootFacet;
import de.gematik.rbellogger.facets.apdu.ApduCommandDictionary;
import de.gematik.rbellogger.facets.apdu.CardCommand;
import de.gematik.rbellogger.facets.apdu.RbelApduCommandFacet;
import de.gematik.rbellogger.facets.apdu.RbelApduParser;
import de.gematik.rbellogger.facets.apdu.RbelApduResponseFacet;
import de.gematik.rbellogger.facets.http.RbelHttpMessageFacet;
import de.gematik.rbellogger.util.RbelContent;
import java.nio.ByteBuffer;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@ConverterInfo(onlyActivateFor = "sicct")
@Slf4j
public class RbelSicctEnvelopeConverter extends RbelConverterPlugin {

  // compare SICCT-specification, chapter 6.1.4.2
  private static final int ENVELOPE_LENGTH = 10;
  private static final int RFU_OFFSET = 5;
  private static final long MAX_BODY_LENGTH = 1 << 20;
  private static final String UNKNOWN_TYPE_BODY_KEY = "body";

  private static final ApduCommandDictionary TERMINAL_FIRST =
      RbelSicctCommand.DICTIONARY.orElse(CardCommand.DICTIONARY);
  private static final ApduCommandDictionary CARD_FIRST =
      CardCommand.DICTIONARY.orElse(RbelSicctCommand.DICTIONARY);

  @Override
  public void consumeElement(final RbelElement element, final RbelConversionExecutor context) {
    if (element.getParentNode() != null
        || element.hasFacet(RbelHttpMessageFacet.class)
        || element.getSize() < ENVELOPE_LENGTH
        || element.getContent().get(RFU_OFFSET) != 0) {
      return;
    }
    final RbelContent content = element.getContent();
    final Optional<SicctMessageType> messageType = SicctMessageType.find(content.get(0));
    final long bodyLength =
        Integer.toUnsignedLong(ByteBuffer.wrap(content.toByteArray(6, ENVELOPE_LENGTH)).getInt());
    final long availableBodyLength = element.getSize() - ENVELOPE_LENGTH;

    if (messageType.isEmpty()) {
      if (bodyLength == availableBodyLength) {
        addEnvelope(element, UNKNOWN_TYPE_BODY_KEY, availableBodyLength);
        element.addFacet(
            RbelNoteFacet.warn(
                "Unknown SICCT message type 0x%02X".formatted(Byte.toUnsignedInt(content.get(0)))));
      }
      return;
    }
    final String bodyKey = messageType.get().bodyKey;
    if (bodyLength > MAX_BODY_LENGTH) {
      addEnvelope(element, bodyKey, availableBodyLength);
      element.addFacet(
          RbelNoteFacet.error(
              "SICCT body length of %d bytes exceeds the maximum of %d bytes"
                  .formatted(bodyLength, MAX_BODY_LENGTH)));
      return;
    }
    if (bodyLength > availableBodyLength) {
      element.addFacet(
          RbelNoteFacet.warn(
              "Incomplete SICCT envelope: %d of %d body bytes available"
                  .formatted(availableBodyLength, bodyLength)));
      return;
    }
    element.setUsedBytes((int) (ENVELOPE_LENGTH + bodyLength));
    final RbelSicctEnvelopeFacet envelope = addEnvelope(element, bodyKey, bodyLength);
    switch (messageType.get()) {
      case C_COMMAND -> parseCommand(element, envelope, context);
      case R_RESPONSE -> parseResponse(element, envelope, context);
      case EVENT_MESSAGE ->
          envelope
              .getBody()
              .addFacet(RbelNoteFacet.info("The format of SICCT event messages is not parsed"));
    }
  }

  private RbelSicctEnvelopeFacet addEnvelope(RbelElement element, String bodyKey, long bodyLength) {
    final RbelContent content = element.getContent();
    final byte typeByte = content.get(0);
    final byte[] address = content.toByteArray(1, 3);
    final byte[] sequenceNumber = content.toByteArray(3, 5);
    final byte[] length = content.toByteArray(6, ENVELOPE_LENGTH);
    final RbelSicctEnvelopeFacet envelope =
        RbelSicctEnvelopeFacet.builder()
            .messageType(
                RbelElement.wrap(
                    new byte[] {typeByte},
                    element,
                    SicctMessageType.find(typeByte)
                        .<Object>map(type -> type)
                        .orElse("%02X".formatted(Byte.toUnsignedInt(typeByte)))))
            .srcOrDesAddress(RbelElement.wrap(address, element, unsignedShort(address)))
            .sequenceNumber(
                RbelElement.wrap(sequenceNumber, element, unsignedShort(sequenceNumber)))
            .abRfu(new RbelElement(content.toByteArray(RFU_OFFSET, RFU_OFFSET + 1), element))
            .length(
                RbelElement.wrap(
                    length, element, Integer.toUnsignedLong(ByteBuffer.wrap(length).getInt())))
            .bodyKey(bodyKey)
            .body(
                RbelElement.create(
                    content.subArray(ENVELOPE_LENGTH, (int) (ENVELOPE_LENGTH + bodyLength)),
                    element))
            .build();
    element.addFacet(envelope);
    element.addFacet(new RbelRootFacet<>(envelope));
    return envelope;
  }

  private void parseCommand(
      RbelElement element, RbelSicctEnvelopeFacet envelope, RbelConversionExecutor context) {
    final ApduCommandDictionary dictionary =
        envelope.isAddressedToCardTerminal() ? TERMINAL_FIRST : CARD_FIRST;
    final Optional<RbelApduCommandFacet> apdu =
        RbelApduParser.parseCommand(envelope.getBody(), dictionary, context);
    if (apdu.isEmpty()) {
      envelope.getBody().addFacet(RbelNoteFacet.warn(invalidBodyNote(envelope, "command")));
    }
    apdu.filter(EhealthTerminalAuthenticate::isCommand)
        .ifPresent(EhealthTerminalAuthenticate::annotateCommand);
    element.addFacet(
        new RbelRequestFacet(
            apdu.map(RbelApduCommandFacet::infoLine).orElse("invalid command APDU"), true));
  }

  private void parseResponse(
      RbelElement element, RbelSicctEnvelopeFacet envelope, RbelConversionExecutor context) {
    final Optional<RbelApduResponseFacet> apdu =
        RbelApduParser.parseResponse(envelope.getBody(), context);
    if (apdu.isEmpty()) {
      envelope.getBody().addFacet(RbelNoteFacet.warn(invalidBodyNote(envelope, "response")));
    }
    element.addFacet(
        new RbelResponseFacet(
            apdu.map(sw -> sw.printStatusWord() + " " + sw.printMeaning())
                .orElse("invalid response APDU")));
  }

  private static String invalidBodyNote(RbelSicctEnvelopeFacet envelope, String apduType) {
    return envelope.getBody().getSize() == 0
        ? "SICCT envelope without body"
        : "Body is not a valid ISO 7816-4 " + apduType + " APDU";
  }

  private static int unsignedShort(byte[] bytes) {
    return (Byte.toUnsignedInt(bytes[0]) << 8) | Byte.toUnsignedInt(bytes[1]);
  }
}
