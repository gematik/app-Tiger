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

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelBinaryFacet;
import de.gematik.rbellogger.data.core.RbelNoteFacet;
import de.gematik.rbellogger.data.core.RbelValueFacet;
import de.gematik.rbellogger.facets.apdu.RbelApduCommandFacet;
import de.gematik.rbellogger.facets.apdu.RbelApduResponseFacet;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;

/**
 * The pairing of Konnektor and card terminal, see gemSpec_KT, chapter 2.5.2.1. The command is
 * encoded according to TIP1-A_3119, an ECDSA signature in the response is returned in plain format
 * (r || s, TR-03111) according to A_3124.
 */
final class EhealthTerminalAuthenticate {

  private static final String DISPLAY_MESSAGE_TAG = "50";
  private static final int RSA_SIGNATURE_LENGTH = 256;
  // r || s of the largest supported curve (P-521)
  private static final int MAX_ECDSA_SIGNATURE_LENGTH = 132;
  private static final int SHA_256_LENGTH = 32;

  private EhealthTerminalAuthenticate() {}

  @RequiredArgsConstructor
  enum Qualifier {
    CREATE(0x01, "CREATE"),
    VALIDATE(0x02, "VALIDATE"),
    ADD_CHALLENGE(0x03, "ADD (generate challenge)"),
    ADD_RESPONSE(0x04, "ADD (add Konnektor)");

    private final int p2;
    private final String label;

    static Optional<Qualifier> find(int p2) {
      return Stream.of(values()).filter(qualifier -> qualifier.p2 == p2).findFirst();
    }

    String label() {
      return label;
    }
  }

  static boolean isCommand(RbelApduCommandFacet command) {
    return RbelSicctCommand.find(unsignedByte(command.getCla()), unsignedByte(command.getIns()))
        .filter(RbelSicctCommand.EHEALTH_TERMINAL_AUTHENTICATE::equals)
        .isPresent();
  }

  /** The display message (Application Label DO) is text, not an arbitrary byte string. */
  static void annotateCommand(RbelApduCommandFacet command) {
    Optional.ofNullable(command.getData())
        .map(data -> data.findRbelPathMembers("$.." + DISPLAY_MESSAGE_TAG))
        .stream()
        .flatMap(List::stream)
        .forEach(EhealthTerminalAuthenticate::decodeDisplayMessage);
  }

  private static void decodeDisplayMessage(RbelElement displayMessage) {
    displayMessage
        .seekValue(byte[].class)
        .ifPresent(
            bytes -> {
              final Charset charset =
                  isUtf8(bytes) ? StandardCharsets.UTF_8 : StandardCharsets.ISO_8859_1;
              displayMessage.removeFacetsOfType(RbelValueFacet.class);
              displayMessage.addFacet(RbelValueFacet.of(new String(bytes, charset)));
              displayMessage.setCharset(Optional.of(charset));
            });
  }

  private static boolean isUtf8(byte[] bytes) {
    try {
      StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes));
      return true;
    } catch (CharacterCodingException e) {
      return false;
    }
  }

  /** The response data depends on the qualifier (P2) of the command it answers. */
  static void annotateResponse(RbelApduCommandFacet command, RbelApduResponseFacet response) {
    final RbelElement data = response.getData();
    if (data == null) {
      return;
    }
    data.addFacet(new RbelBinaryFacet());
    Qualifier.find(unsignedByte(command.getP2()))
        .ifPresent(
            qualifier -> {
              switch (qualifier) {
                case CREATE -> annotateSignature(data);
                case VALIDATE ->
                    data.addFacet(
                        data.getSize() == SHA_256_LENGTH
                            ? RbelNoteFacet.info("SHA-256 hash of challenge and shared secret")
                            : RbelNoteFacet.warn(
                                "Expected a SHA-256 hash of %d bytes, got %d bytes"
                                    .formatted(SHA_256_LENGTH, data.getSize())));
                case ADD_CHALLENGE ->
                    data.addFacet(RbelNoteFacet.info("Challenge of the card terminal"));
                case ADD_RESPONSE -> data.addFacet(RbelNoteFacet.warn("Unexpected response data"));
              }
            });
  }

  private static void annotateSignature(RbelElement data) {
    final long size = data.getSize();
    if (size == RSA_SIGNATURE_LENGTH) {
      data.addFacet(RbelNoteFacet.info("RSA signature (EMSA-PSS) of the shared secret"));
    } else if (size % 2 == 0 && size <= MAX_ECDSA_SIGNATURE_LENGTH) {
      data.addFacet(RbelEcdsaPlainSignatureFacet.of(data));
      data.addFacet(
          RbelNoteFacet.info("ECDSA signature of the shared secret, r || s in plain format"));
    } else {
      data.addFacet(
          RbelNoteFacet.warn(
              "Unexpected signature length of %d bytes for the shared secret".formatted(size)));
    }
  }

  private static int unsignedByte(RbelElement element) {
    return Byte.toUnsignedInt(element.getRawContent()[0]);
  }
}
