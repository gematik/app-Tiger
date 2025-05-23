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
package de.gematik.rbellogger.util.email_crypto.elliptic_curve;

import java.util.Arrays;

/**
 * Die Klasse enthält einige Methoden um {@link String}s für die entsprechenden Anforderungen zu
 * manipulieren und konvertieren.
 *
 * @author cdh
 */
public final class StringTools {

  private static final int RADIX_HEX_16 = 16;
  private static final int RADIX_BIN_2 = 2;

  /** Alle im Hexadezimal dargestellte Zahlen und Zeichen. */
  private static final char[] HEXCHAR = {
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
  };

  private StringTools() {}

  /**
   * Methode konvertiert ein Byte-Array in einen hexadezimalen String.
   *
   * @param b - Byte-Array
   * @return Die hexadezimale Darstellung des Byte-Arrays
   */
  public static String toHexString(final byte[] b) {
    return toHexString(b, false);
  }

  /**
   * Methode konvertiert ein Byte-Array in einen hexadezimalen String.
   *
   * @param b - Byte-Array
   * @param stripLeadingZeroes - Boolean der angibt ob führende 0en aus dem Wert entfernt werden
   *     sollen.
   * @return Die hexadezimale Darstellung des Byte-Arrays
   */
  public static String toHexString(final byte[] b, final boolean stripLeadingZeroes) {
    if (b == null) {
      return null;
    }

    StringBuilder sb = new StringBuilder(b.length * RADIX_BIN_2);
    boolean zeroIsLeading = stripLeadingZeroes;
    for (int i = 0; i < b.length; i++) {
      if ((b[i] == 0x00 && !zeroIsLeading) || b[i] != 0x00) {
        zeroIsLeading = false;
        // look up high nibble char
        sb.append(HEXCHAR[(b[i] & 0xf0) >>> 4]); // fill left with zero bits

        // look up low nibble char
        sb.append(HEXCHAR[b[i] & 0x0f]);
      }
    }
    return sb.toString();
  }

  /**
   * Methode konvertiert einen hexadezimalen String in ein Byte-Array
   *
   * @param hexStr - Der Hexstring
   * @return Die Byte-Array Darstellung WR 27.01.2014 extension for odd string length
   */
  public static byte[] toByteArray(final String hexStr) {
    int offset = 0;
    int length = (hexStr.length() + 1) / RADIX_BIN_2;
    byte[] bArray = new byte[length];
    if (1 == hexStr.length() % 2) {
      offset = 1;
      bArray[0] = Byte.parseByte(hexStr.substring(0, 1), RADIX_HEX_16);
    }
    for (int i = offset, j = offset; i < length; i++, j += 2) {
      bArray[i] =
          (byte)
              (Byte.parseByte(hexStr.substring(j, j + 1), RADIX_HEX_16) << 4
                  | (0xff & Byte.parseByte(hexStr.substring(j + 1, j + 2), RADIX_HEX_16)));
    }
    return bArray;
  }

  /**
   * Methode füllt einen String mit 0en bis die gewünschte Länge erreicht ist. Die 0en werden an den
   * <b>Anfang</b> des Strings gesetzt.
   *
   * @param length - Die gewünschte Stringlänge
   * @param oldString - Der String
   * @return Der String mit der gewünschten Länge<br>
   *     z.B.: 5, "ab" &rarr; "000ab"<br>
   *     2, "abba" &rarr; "abba"
   */
  public static String fillWithZerosBefore(final int length, final String oldString) {
    if (oldString.length() >= length) {
      return oldString;
    }
    StringBuilder sb = new StringBuilder();
    char[] filler = new char[length - oldString.length()];
    Arrays.fill(filler, 0, filler.length, '0');
    sb.append(filler);
    sb.append(oldString);
    return sb.toString();
  }
}
