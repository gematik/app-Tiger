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
package de.gematik.rbellogger.util;

import java.util.Arrays;
import java.util.Base64;
import javax.annotation.Nullable;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Lookup tables for the characters of the standard and the URL-safe Base64 alphabets. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Base64Alphabet {

  private static final String URL_SAFE_ALPHABET =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

  private static final byte[] URL_SAFE_VALUES = new byte[128];

  static {
    Arrays.fill(URL_SAFE_VALUES, (byte) -1);
    for (int value = 0; value < URL_SAFE_ALPHABET.length(); value++) {
      URL_SAFE_VALUES[URL_SAFE_ALPHABET.charAt(value)] = (byte) value;
    }
  }

  /** Whether the byte is a character of the standard or of the URL-safe alphabet. */
  public static boolean isBase64Character(int b) {
    return b >= 0
        && b < URL_SAFE_VALUES.length
        && (URL_SAFE_VALUES[b] >= 0 || b == '+' || b == '/');
  }

  /** The value of the byte in the URL-safe alphabet, or -1 if it is not a character of it. */
  public static int urlSafeValue(byte b) {
    return b >= 0 ? URL_SAFE_VALUES[b] : -1;
  }

  /**
   * Whether the content can be the Base64 text of either alphabet: nothing but its characters, with
   * at most two padding characters at the end, and not a single character more than a multiple of
   * four. Everything a strict decoder decodes is accepted, a few other texts are accepted as well.
   */
  public static boolean canBeBase64(RbelContent content) {
    int size = content.size();
    for (int padding = 0; padding < MAX_PADDING && size > 0 && content.get(size - 1) == '='; ) {
      size--;
      padding++;
    }
    if (size % 4 == 1) {
      return false;
    }
    return content.indexOfFirst(b -> !isBase64Character(b), 0, size) < 0;
  }

  /** The same as {@link #canBeBase64(RbelContent)}, for a text. */
  public static boolean canBeBase64(String text) {
    int size = text.length();
    for (int padding = 0; padding < MAX_PADDING && size > 0 && text.charAt(size - 1) == '='; ) {
      size--;
      padding++;
    }
    if (size % 4 == 1) {
      return false;
    }
    for (int i = 0; i < size; i++) {
      if (!isBase64Character(text.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  /**
   * The one decoder that a stream decoding of the content can succeed with, or null if there is
   * none. A stream decoder stops at the first padding character and ignores everything after it, so
   * unlike {@link #canBeBase64(RbelContent)} this accepts a padded text that is followed by a line
   * break. There is no decoder for content that has another character before the first padding
   * character, ends in a single stray character, or has characters of both alphabets. A text of the
   * characters that both alphabets share is decoded to the same bytes by both, so it is given the
   * standard decoder.
   */
  @Nullable
  public static Base64.Decoder streamDecoderFor(RbelContent content) {
    int firstOther = content.indexOfFirst(b -> !isBase64Character(b), 0, content.size());
    int end = firstOther < 0 ? content.size() : firstOther;
    if (firstOther < 0 ? end % 4 == 1 : content.get(firstOther) != '=') {
      return null;
    }
    boolean hasStandardCharacter = content.indexOfFirst(b -> b == '+' || b == '/', 0, end) >= 0;
    boolean hasUrlSafeCharacter = content.indexOfFirst(b -> b == '-' || b == '_', 0, end) >= 0;
    if (hasStandardCharacter && hasUrlSafeCharacter) {
      return null;
    }
    return hasUrlSafeCharacter ? Base64.getUrlDecoder() : Base64.getDecoder();
  }

  private static final int MAX_PADDING = 2;
}
