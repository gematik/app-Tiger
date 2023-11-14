/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.util;

import java.util.Arrays;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RbelArrayUtils {

  public static boolean startsWith(byte[] array, byte[] prefix) {
    if (array == prefix) {
      return true;
    }
    if (array == null || prefix == null) {
      return false;
    }
    int prefixLength = prefix.length;

    if (prefix.length > array.length) {
      return false;
    }

    for (int i = 0; i < prefixLength; i++) {
      if (array[i] != prefix[i]) {
        return false;
      }
    }

    return true;
  }

  public static byte[] sliceArrayAtMarker(byte[] array, byte[] marker, int searchOffset) {
    if (array == marker || array == null || marker == null) {
      throw new IllegalArgumentException();
    }
    int markerLength = marker.length;

    if (markerLength > array.length) {
      throw new IllegalArgumentException();
    }

    final int indexOf = indexOf(array, marker, searchOffset);
    return Arrays.copyOfRange(array, indexOf, indexOf + markerLength);
  }

  public static int indexOf(byte[] outerArray, byte[] smallerArray, int searchOffset) {
    for (int i = searchOffset; i < outerArray.length - smallerArray.length + 1; ++i) {
      boolean found = true;
      for (int j = 0; j < smallerArray.length; ++j) {
        if (outerArray[i + j] != smallerArray[j]) {
          found = false;
          break;
        }
      }
      if (found) {
        return i;
      }
    }
    return -1;
  }
}
