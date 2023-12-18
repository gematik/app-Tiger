/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.util;

public class BinaryClassifier {

  private BinaryClassifier() {}

  private static final int BYTES_TO_CHECK = 100;

  public static boolean isBinary(byte[] data) {
    for (int pos = 0; pos < BYTES_TO_CHECK && pos < data.length; pos++) {
      // CR LF
      if (data[pos] == 0xA || data[pos] == 0xD) {
        continue;
      }
      if (data[pos] < 0x20) {
        return true;
      }
    }
    return false;
  }
}
