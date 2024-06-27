/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.util.email_crypto.elliptic_curve;

public final class ByteTools {

  // Kein Konstruktor in Utility Klasse benötigt
  private ByteTools() {}

  public static byte[] sub(final byte[] from, final int offset, final int count) {

    if (from.length < (offset + count)) {
      throw new IndexOutOfBoundsException(
          ByteTools.class.getName() + " : Not enough data available");
    }

    byte[] ba = new byte[count];

    for (int i = 0; i < count; i++) {
      ba[i] = from[i + offset];
    }
    return ba;
  }
}
