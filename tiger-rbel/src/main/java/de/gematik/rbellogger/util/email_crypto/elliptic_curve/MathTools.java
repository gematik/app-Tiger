/*
 * Copyright (c) 2024 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.rbellogger.util.email_crypto.elliptic_curve;

/** Klasse beinhaltet mathematische Funktionen die in der Simulation benutzt werden. */
public final class MathTools {

  /** Konstruktor in Utility Klasse nicht benötigt. */
  private MathTools() {}

  /**
   * XOR Verknüpfung zweier Byte-Arrays
   *
   * @pre Beide Arrays müssen die selbe Länge aufweisen
   * @param a - Das erste Byte-Array [in/out]
   * @param b - Das zweite Byte-Array
   */
  public static void xor(final byte[] a, final byte[] b) {
    if (a.length != b.length) {
      throw new IllegalArgumentException("Lengths don't match");
    }
    for (int i = 0; i < a.length; i++) {
      a[i] ^= b[i];
    }
  }
}
