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
package de.gematik.rbellogger.util;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import lombok.SneakyThrows;

/**
 * Classifies byte content as binary or text. Reads as UTF-8 char-by-char: if decoding fails or a
 * non-printable control character (except TAB, CR, LF) is encountered, the content is binary. Fails
 * fast on the first problematic character.
 */
public class BinaryClassifier {

  private BinaryClassifier() {}

  private static final int CHARS_TO_CHECK = 512;
  private static final int MAX_BYTES_PER_CHAR = 4;

  public static boolean isBinary(byte[] data) {
    int limit = Math.min(data.length, CHARS_TO_CHECK);
    int position = 0;
    while (position < limit && data[position] >= 0) {
      if (isForbiddenControl(data[position])) {
        return true;
      }
      position++;
    }
    return position < limit && isBinaryUtf8(data, position, data.length - position, position);
  }

  /**
   * The same classification without copying the content. The chunks are searched in place for the
   * first byte that is not plain text, which ends the search for content that is binary early. Only
   * from the first non-ASCII character on, the bytes that the remaining characters can take are
   * copied for the decoder.
   */
  public static boolean isBinary(RbelContent data) {
    int size = data.size();
    int limit = Math.min(size, CHARS_TO_CHECK);
    int position =
        limit == 0 ? -1 : data.indexOfFirst(b -> b < 0 || isForbiddenControl(b), 0, limit);
    if (position < 0) {
      return false;
    }
    if (data.get(position) >= 0) {
      return true;
    }
    int end = Math.min(size, position + (CHARS_TO_CHECK - position) * MAX_BYTES_PER_CHAR);
    byte[] window = data.toByteArray(position, end);
    return isBinaryUtf8(window, 0, window.length, position);
  }

  private static boolean isBinaryUtf8(byte[] bytes, int offset, int length, int charsChecked) {
    var decoder = newUtf8Decoder();
    var chars = CharBuffer.allocate(CHARS_TO_CHECK - charsChecked);
    if (decoder.decode(ByteBuffer.wrap(bytes, offset, length), chars, true).isError()) {
      return true;
    }
    chars.flip();
    while (chars.hasRemaining()) {
      if (isForbiddenControl(chars.get())) {
        return true;
      }
    }
    return false;
  }

  private static CharsetDecoder newUtf8Decoder() {
    return StandardCharsets.UTF_8
        .newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);
  }

  private static boolean isForbiddenControl(int ch) {
    return Character.isISOControl(ch) && ch != '\t' && ch != '\n' && ch != '\r';
  }

  @SneakyThrows
  public static boolean isBinary(InputStream data) {
    try (var reader = new InputStreamReader(data, newUtf8Decoder())) {
      for (int pos = 0; pos < CHARS_TO_CHECK; pos++) {
        int ch = reader.read();
        if (ch == -1) {
          break;
        }
        if (isForbiddenControl(ch)) {
          return true;
        }
      }
    } catch (java.io.IOException e) {
      return true; // malformed UTF-8
    }
    return false;
  }
}
