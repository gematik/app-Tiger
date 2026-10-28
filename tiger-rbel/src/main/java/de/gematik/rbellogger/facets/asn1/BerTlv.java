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
package de.gematik.rbellogger.facets.asn1;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The structure of a BER encoded data object (ITU-T X.690): tag, position and, if constructed, the
 * nested data objects. Values are not decoded. Parsing is strict: the input has to consist of
 * complete data objects only, otherwise nothing is returned.
 */
public record BerTlv(
    int tag, int offset, int headerLength, int valueLength, int end, List<BerTlv> children) {

  private static final int MAX_TAG_LENGTH = 3;
  private static final int MAX_LENGTH_BYTES = 4;
  private static final int MAX_DEPTH = 64;
  private static final int INDEFINITE_LENGTH = 0x80;
  private static final int END_OF_CONTENTS_LENGTH = 2;

  public boolean isConstructed() {
    return children != null;
  }

  public boolean isUniversal() {
    return (firstTagByte() & 0xC0) == 0;
  }

  public int valueOffset() {
    return offset + headerLength;
  }

  public String tagHex() {
    final String hex = Integer.toHexString(tag).toUpperCase();
    return hex.length() % 2 == 0 ? hex : "0" + hex;
  }

  private int firstTagByte() {
    if (tag > 0xFFFF) {
      return tag >>> 16;
    }
    return tag > 0xFF ? tag >>> 8 : tag;
  }

  /** Parses a series of data objects that has to fill {@code bytes} completely. */
  public static Optional<List<BerTlv>> parseAll(byte[] bytes) {
    if (bytes.length == 0) {
      return Optional.empty();
    }
    return parseSequence(bytes, 0, bytes.length, false, 0);
  }

  private static Optional<List<BerTlv>> parseSequence(
      byte[] bytes, int from, int to, boolean untilEndOfContents, int depth) {
    if (depth > MAX_DEPTH) {
      return Optional.empty();
    }
    final List<BerTlv> result = new ArrayList<>();
    int position = from;
    while (position < to) {
      if (untilEndOfContents && isEndOfContents(bytes, position, to)) {
        return Optional.of(result);
      }
      final Optional<BerTlv> next = parseObject(bytes, position, to, depth);
      if (next.isEmpty()) {
        return Optional.empty();
      }
      result.add(next.get());
      position = next.get().end();
    }
    return untilEndOfContents || result.isEmpty() ? Optional.empty() : Optional.of(result);
  }

  private static boolean isEndOfContents(byte[] bytes, int position, int to) {
    return position + 1 < to && bytes[position] == 0 && bytes[position + 1] == 0;
  }

  private static Optional<BerTlv> parseObject(byte[] bytes, int offset, int to, int depth) {
    final int firstTagByte = Byte.toUnsignedInt(bytes[offset]);
    if (firstTagByte == 0x00 || firstTagByte == 0xFF) {
      return Optional.empty();
    }
    final boolean constructed = (firstTagByte & 0x20) != 0;
    int position = offset;
    int tag = firstTagByte;
    if ((firstTagByte & 0x1F) == 0x1F) {
      int nextByte;
      do {
        position++;
        if (position >= to || position - offset >= MAX_TAG_LENGTH) {
          return Optional.empty();
        }
        nextByte = Byte.toUnsignedInt(bytes[position]);
        tag = (tag << 8) | nextByte;
      } while ((nextByte & 0x80) != 0);
    }
    position++;
    if (position >= to) {
      return Optional.empty();
    }
    final int firstLengthByte = Byte.toUnsignedInt(bytes[position++]);
    final int valueOffset;
    final int headerLength;
    if (firstLengthByte == INDEFINITE_LENGTH) {
      if (!constructed) {
        return Optional.empty();
      }
      valueOffset = position;
      headerLength = valueOffset - offset;
      final int parsedTag = tag;
      return parseSequence(bytes, valueOffset, to, true, depth + 1)
          .map(
              children -> {
                final int valueEnd =
                    children.isEmpty() ? valueOffset : children.get(children.size() - 1).end();
                return new BerTlv(
                    parsedTag,
                    offset,
                    headerLength,
                    valueEnd - valueOffset,
                    valueEnd + END_OF_CONTENTS_LENGTH,
                    children);
              });
    }
    long valueLength;
    if (firstLengthByte < 0x80) {
      valueLength = firstLengthByte;
    } else {
      final int lengthBytes = firstLengthByte & 0x7F;
      if (lengthBytes > MAX_LENGTH_BYTES || position + lengthBytes > to) {
        return Optional.empty();
      }
      valueLength = 0;
      for (int i = 0; i < lengthBytes; i++) {
        valueLength = (valueLength << 8) | Byte.toUnsignedInt(bytes[position++]);
      }
    }
    valueOffset = position;
    if (valueLength > to - valueOffset) {
      return Optional.empty();
    }
    headerLength = valueOffset - offset;
    final int length = (int) valueLength;
    final int end = valueOffset + length;
    final int parsedTag = tag;
    if (!constructed) {
      return Optional.of(new BerTlv(parsedTag, offset, headerLength, length, end, null));
    }
    if (length == 0) {
      return Optional.of(new BerTlv(parsedTag, offset, headerLength, 0, end, List.of()));
    }
    return parseSequence(bytes, valueOffset, end, false, depth + 1)
        .map(children -> new BerTlv(parsedTag, offset, headerLength, length, end, children));
  }
}
