/*
 *
 * Copyright 2021-2026 gematik GmbH
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
package de.gematik.rbellogger.facets.apdu;

import de.gematik.rbellogger.facets.apdu.ApduCommandDictionary.Description;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.bouncycastle.util.encoders.Hex;

/**
 * Smart card commands of ISO 7816-4 (interindustry class) and the proprietary commands of the
 * gematik COS (class 80).
 */
public enum CardCommand {
  ACTIVATE(false, 0x44),
  APPEND_RECORD(false, 0xE2),
  CHANGE_REFERENCE_DATA(false, 0x24),
  DEACTIVATE(false, 0x04),
  DELETE(false, 0xE4),
  DISABLE_VERIFICATION_REQUIREMENT(false, 0x26),
  ENABLE_VERIFICATION_REQUIREMENT(false, 0x28),
  ERASE_BINARY(false, 0x0E),
  EXTERNAL_AUTHENTICATE(false, 0x82),
  GENERAL_AUTHENTICATE(false, 0x86, 0x87),
  GENERATE_ASYMMETRIC_KEY_PAIR(false, 0x46),
  GET_CHALLENGE(false, 0x84),
  GET_DATA(false, 0xCA, 0xCB),
  GET_RESPONSE(false, 0xC0),
  INTERNAL_AUTHENTICATE(false, 0x88),
  MANAGE_CHANNEL(false, 0x70),
  MANAGE_SECURITY_ENVIRONMENT("MSE", false, 0x22),
  PERFORM_SECURITY_OPERATION("PSO", false, 0x2A),
  READ_BINARY(false, 0xB0, 0xB1),
  READ_RECORD(false, 0xB2, 0xB3),
  RESET_RETRY_COUNTER(false, 0x2C),
  SEARCH_RECORD(false, 0xA2),
  SELECT(false, 0xA4),
  TERMINATE_CARD_USAGE(false, 0xFE),
  TERMINATE_DF(false, 0xE6),
  TERMINATE_EF(false, 0xE8),
  UPDATE_BINARY(false, 0xD6, 0xD7),
  UPDATE_RECORD(false, 0xDC, 0xDD),
  VERIFY(false, 0x20, 0x21),
  WRITE_BINARY(false, 0xD0, 0xD1),
  WRITE_RECORD(false, 0xD2),
  FINGERPRINT(true, 0xFA),
  GET_PIN_STATUS(true, 0x20),
  GET_RANDOM(true, 0x84),
  LIST_PUBLIC_KEY(true, 0xCA),
  LOAD_APPLICATION(true, 0xEA);

  public static final ApduCommandDictionary DICTIONARY = CardCommand::describe;

  private static final Map<String, String> APPLICATION_NAMES =
      Map.of(
          "D2760001448000", "MF",
          "D27600000102", "DF.HCA",
          "A000000167455349474E", "DF.ESIGN",
          "D27600006601", "DF.QES");

  private static final Map<Integer, String> SECURITY_OPERATIONS =
      Map.of(
          0x9E9A, "COMPUTE DIGITAL SIGNATURE",
          0x8086, "DECIPHER",
          0x8680, "ENCIPHER",
          0x8E80, "COMPUTE CRYPTOGRAPHIC CHECKSUM",
          0x00A2, "VERIFY CRYPTOGRAPHIC CHECKSUM",
          0x00A8, "VERIFY DIGITAL SIGNATURE",
          0x00BE, "VERIFY CERTIFICATE");

  private static final Map<Integer, String> CONTROL_REFERENCE_TEMPLATES =
      Map.of(0xA4, "AT", 0xA6, "KAT", 0xAA, "HT", 0xB4, "CCT", 0xB6, "DST", 0xB8, "CT");

  private final String label;
  private final boolean proprietaryClass;
  private final int[] instructions;

  CardCommand(boolean proprietaryClass, int... instructions) {
    this(null, proprietaryClass, instructions);
  }

  CardCommand(String label, boolean proprietaryClass, int... instructions) {
    this.label = label != null ? label : name().replace('_', ' ');
    this.proprietaryClass = proprietaryClass;
    this.instructions = instructions;
  }

  public String label() {
    return label;
  }

  public static Optional<CardCommand> find(int cla, int ins) {
    if (cla == 0xFF) {
      return Optional.empty();
    }
    final boolean proprietary = (cla & 0x80) != 0;
    return Stream.of(values())
        .filter(cmd -> cmd.proprietaryClass == proprietary)
        .filter(cmd -> IntStream.of(cmd.instructions).anyMatch(i -> i == ins))
        .findFirst();
  }

  private static Optional<Description> describe(CommandApdu apdu) {
    return find(apdu.getCla(), apdu.getIns())
        .map(cmd -> new Description(cmd.label(), cmd.details(apdu)));
  }

  private String details(CommandApdu apdu) {
    return switch (this) {
      case SELECT -> selectDetails(apdu);
      case READ_BINARY, UPDATE_BINARY, ERASE_BINARY, WRITE_BINARY ->
          (apdu.getP1() & 0x80) != 0 && apdu.getIns() % 2 == 0
              ? "SFI %02X".formatted(apdu.getP1() & 0x1F)
              : "";
      case PERFORM_SECURITY_OPERATION ->
          SECURITY_OPERATIONS.getOrDefault((apdu.getP1() << 8) | apdu.getP2(), "");
      case MANAGE_SECURITY_ENVIRONMENT -> mseDetails(apdu);
      default -> "";
    };
  }

  private static String selectDetails(CommandApdu apdu) {
    final String identifier = Hex.toHexString(apdu.getData()).toUpperCase();
    if (apdu.getP1() == 0x04) {
      return APPLICATION_NAMES.getOrDefault(identifier, identifier);
    }
    return identifier;
  }

  private static String mseDetails(CommandApdu apdu) {
    final String template = CONTROL_REFERENCE_TEMPLATES.get(apdu.getP2());
    if ((apdu.getP1() & 0x0F) != 0x01 || template == null) {
      return "";
    }
    return "SET " + template;
  }
}
