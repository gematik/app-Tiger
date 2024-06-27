/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.util.email_crypto.elliptic_curve;

/**
 * Klasse zur Generierung von ISO7816 Padding
 *
 * @author hve
 */
public final class Padding {

  private Padding() {}

  /**
   * Fügt den bestehenden Inputdaten Paddinginformation gemäß ISO7816 hinzu.
   *
   * @param in Die Inputdaten
   * @param n Offset zu den Inputdaten
   * @return Die gepaddeten Daten
   */
  public static byte[] addIsoPadding(final byte[] in, final int n) {
    // (N000.900)
    byte[] out = StringTools.toByteArray(StringTools.toHexString(in) + "80");

    if ((out.length % n) == 0) {
      return out;
    } else {
      out =
          StringTools.toByteArray(
              StringTools.toHexString(out)
                  + StringTools.toHexString(new byte[n - (out.length % n)]));
    }

    return out;
  }

  /**
   * Ermittelt die ISO7816 Padding Bytes von Inputdaten
   *
   * @param in Die Inputdaten
   * @return Die Länge der Paddinginformation @BcException
   */
  public static int countIsoPaddingByte(final byte[] in) throws BcException {
    // (N001.000)
    int len = in.length;

    // (N001.000) a.
    if (0 != (len % in.length)) {
      throw new BcException(Padding.class.getName() + " : (N001.000) a. : len not a multiply of n");
    }

    // (N001.000) b.
    if (0 == len) {
      throw new BcException(Padding.class.getName() + " : (N001.000) b. : len is zero");
    }

    while (0 == in[len - 1]) {
      len--;
    }

    // (N001.000) d.
    if ((byte) 0x80 != in[len - 1]) {
      throw new BcException(Padding.class.getName() + " : (N001.000) d. : LSbyte of is not '80'");
    }

    return in.length - len + 1;
  }
}
