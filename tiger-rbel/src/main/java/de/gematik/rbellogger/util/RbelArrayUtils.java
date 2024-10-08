/*
 * Copyright 2024 gematik GmbH
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
 */

package de.gematik.rbellogger.util;

import java.nio.charset.StandardCharsets;
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

  private static boolean startsWithIgnoreCaseInternal(
      byte[] content, byte[] prefix, int startInclusive) {
    var prefixString = new String(prefix, StandardCharsets.UTF_8);
    return new String(content, startInclusive, prefix.length, StandardCharsets.UTF_8)
        .equalsIgnoreCase(prefixString);
  }

  private static boolean startsWithInternal(byte[] content, byte[] searchContent, int startIndex) {
    for (int j = 0, k = startIndex; j < searchContent.length; j++, k++) {
      if (content[k] != searchContent[j]) {
        return false;
      }
    }
    return true;
  }

  public static boolean startsTrimmedWith(byte[] content, byte[] firstNonBlankBytes) {
    return startsTrimmedWith(content, firstNonBlankBytes, false);
  }

  public static boolean startsTrimmedWith(
      byte[] content, byte[] firstNonBlankBytes, boolean ignoreCase) {
    for (int i = 0; i < content.length; i++) {
      if (!Character.isWhitespace(content[i])) {
        if (i + firstNonBlankBytes.length > content.length) {
          return false;
        }
        if (ignoreCase) {
          return startsWithIgnoreCaseInternal(content, firstNonBlankBytes, i);
        }
        return startsWithInternal(content, firstNonBlankBytes, i);
      }
    }
    return false;
  }

  public static boolean endsTrimmedWith(byte[] content, byte[] lastNonBlankBytes) {
    return endsTrimmedWith(content, lastNonBlankBytes, false);
  }

  public static boolean endsTrimmedWith(
      byte[] content, byte[] lastNonBlankBytes, boolean ignoreCase) {
    for (int i = content.length; i-- > 0; ) {
      if (!Character.isWhitespace(content[i])) {
        var beginIndex = i - lastNonBlankBytes.length + 1;
        if (beginIndex < 0) {
          return false;
        }
        if (ignoreCase) {
          return startsWithIgnoreCaseInternal(content, lastNonBlankBytes, beginIndex);
        }
        return startsWithInternal(content, lastNonBlankBytes, beginIndex);
      }
    }
    return false;
  }
}
