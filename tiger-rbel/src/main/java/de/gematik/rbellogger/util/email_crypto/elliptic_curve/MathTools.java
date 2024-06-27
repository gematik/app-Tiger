/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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
