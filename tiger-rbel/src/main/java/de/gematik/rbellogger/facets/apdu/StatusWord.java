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

import java.util.Map;

/** Meanings of the status words of ISO 7816-4, chapter 5.6. */
public final class StatusWord {

  private static final Map<Integer, String> MEANINGS =
      Map.ofEntries(
          Map.entry(0x9000, "Normal processing"),
          Map.entry(0x6281, "Part of returned data may be corrupted"),
          Map.entry(0x6282, "End of file or record reached before reading Ne bytes"),
          Map.entry(0x6283, "Selected file deactivated"),
          Map.entry(0x6284, "File control information not formatted"),
          Map.entry(0x6285, "Selected file in termination state"),
          Map.entry(0x6400, "Execution error"),
          Map.entry(0x6581, "Memory failure"),
          Map.entry(0x6700, "Wrong length"),
          Map.entry(0x6881, "Logical channel not supported"),
          Map.entry(0x6882, "Secure messaging not supported"),
          Map.entry(0x6883, "Last command of the chain expected"),
          Map.entry(0x6884, "Command chaining not supported"),
          Map.entry(0x6900, "Command not allowed"),
          Map.entry(0x6981, "Command incompatible with file structure"),
          Map.entry(0x6982, "Security status not satisfied"),
          Map.entry(0x6983, "Authentication method blocked"),
          Map.entry(0x6984, "Reference data not usable"),
          Map.entry(0x6985, "Conditions of use not satisfied"),
          Map.entry(0x6986, "Command not allowed (no current EF)"),
          Map.entry(0x6987, "Expected secure messaging data objects missing"),
          Map.entry(0x6988, "Incorrect secure messaging data objects"),
          Map.entry(0x6A80, "Incorrect parameters in the command data field"),
          Map.entry(0x6A81, "Function not supported"),
          Map.entry(0x6A82, "File or application not found"),
          Map.entry(0x6A83, "Record not found"),
          Map.entry(0x6A84, "Not enough memory space in the file"),
          Map.entry(0x6A86, "Incorrect parameters P1-P2"),
          Map.entry(0x6A88, "Referenced data or reference data not found"),
          Map.entry(0x6A89, "File already exists"),
          Map.entry(0x6A8A, "DF name already exists"),
          Map.entry(0x6B00, "Wrong parameters P1-P2"),
          Map.entry(0x6D00, "Instruction code not supported or invalid"),
          Map.entry(0x6E00, "Class not supported"),
          Map.entry(0x6F00, "No precise diagnosis"));

  private StatusWord() {}

  public static String describe(int sw) {
    final String known = MEANINGS.get(sw);
    if (known != null) {
      return known;
    }
    final int sw1 = sw >> 8;
    final int sw2 = sw & 0xFF;
    if (sw1 == 0x61) {
      return sw2 + " further response bytes available";
    }
    if (sw1 == 0x6C) {
      return "Wrong Le, " + sw2 + " bytes available";
    }
    if (sw1 == 0x63 && (sw2 & 0xF0) == 0xC0) {
      return "Verification failed, " + (sw2 & 0x0F) + " retries left";
    }
    return switch (sw1) {
      case 0x62 -> "Warning, state of non-volatile memory unchanged";
      case 0x63 -> "Warning, state of non-volatile memory changed";
      case 0x64 -> "Execution error, state of non-volatile memory unchanged";
      case 0x65 -> "Execution error, state of non-volatile memory changed";
      case 0x66 -> "Security-related issue";
      case 0x68 -> "Functions in CLA not supported";
      case 0x69 -> "Command not allowed";
      case 0x6A -> "Wrong parameters P1-P2";
      default -> "Unknown status word";
    };
  }
}
