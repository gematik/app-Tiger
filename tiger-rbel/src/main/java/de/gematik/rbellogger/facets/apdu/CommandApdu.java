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

import java.util.Arrays;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;

/** A command APDU split into its ISO 7816-4 fields (header, Lc, data, Le). */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CommandApdu {

  public static final int HEADER_LENGTH = 4;
  private static final int SHORT_MAX_NE = 256;
  private static final int EXTENDED_MAX_NE = 65536;

  byte[] bytes;
  ApduCase apduCase;
  int lcLength;
  int nc;
  int leLength;
  int ne;

  public static Optional<CommandApdu> parse(byte[] apdu) {
    final int length = apdu.length;
    if (length < HEADER_LENGTH) {
      return Optional.empty();
    }
    if (length == HEADER_LENGTH) {
      return of(apdu, ApduCase.CASE_1, 0, 0, 0, 0);
    }
    final int b5 = Byte.toUnsignedInt(apdu[4]);
    if (length == 5) {
      return of(apdu, ApduCase.CASE_2S, 0, 0, 1, shortNe(b5));
    }
    if (b5 != 0) {
      if (length == 5 + b5) {
        return of(apdu, ApduCase.CASE_3S, 1, b5, 0, 0);
      }
      if (length == 6 + b5) {
        return of(apdu, ApduCase.CASE_4S, 1, b5, 1, shortNe(apdu[length - 1]));
      }
      return Optional.empty();
    }
    if (length < 7) {
      return Optional.empty();
    }
    if (length == 7) {
      return of(apdu, ApduCase.CASE_2E, 0, 0, 3, extendedNe(apdu, 5));
    }
    final int nc = readUnsignedShort(apdu, 5);
    if (nc == 0) {
      return Optional.empty();
    }
    if (length == 7 + nc) {
      return of(apdu, ApduCase.CASE_3E, 3, nc, 0, 0);
    }
    if (length == 9 + nc) {
      return of(apdu, ApduCase.CASE_4E, 3, nc, 2, extendedNe(apdu, length - 2));
    }
    return Optional.empty();
  }

  private static Optional<CommandApdu> of(
      byte[] apdu, ApduCase apduCase, int lcLength, int nc, int leLength, int ne) {
    return Optional.of(new CommandApdu(apdu, apduCase, lcLength, nc, leLength, ne));
  }

  private static int shortNe(int le) {
    final int value = le & 0xFF;
    return value == 0 ? SHORT_MAX_NE : value;
  }

  private static int extendedNe(byte[] apdu, int offset) {
    final int value = readUnsignedShort(apdu, offset);
    return value == 0 ? EXTENDED_MAX_NE : value;
  }

  private static int readUnsignedShort(byte[] apdu, int offset) {
    return (Byte.toUnsignedInt(apdu[offset]) << 8) | Byte.toUnsignedInt(apdu[offset + 1]);
  }

  public int getCla() {
    return Byte.toUnsignedInt(bytes[0]);
  }

  public int getIns() {
    return Byte.toUnsignedInt(bytes[1]);
  }

  public int getP1() {
    return Byte.toUnsignedInt(bytes[2]);
  }

  public int getP2() {
    return Byte.toUnsignedInt(bytes[3]);
  }

  public int getDataOffset() {
    return HEADER_LENGTH + lcLength;
  }

  public int getLeOffset() {
    return getDataOffset() + nc;
  }

  public byte[] getData() {
    return Arrays.copyOfRange(bytes, getDataOffset(), getLeOffset());
  }

  /** The command cases of ISO 7816-4, chapter 5.1; S = short, E = extended length fields. */
  public enum ApduCase {
    CASE_1,
    CASE_2S,
    CASE_3S,
    CASE_4S,
    CASE_2E,
    CASE_3E,
    CASE_4E;

    public String label() {
      return name().substring("CASE_".length());
    }
  }
}
