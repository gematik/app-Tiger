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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntPredicate;
import javax.annotation.Nullable;

@SuppressWarnings("java:S1610")
public abstract class RbelContent {
  public static RbelContent of(@Nullable byte[] content) {
    return RbelContentBase.of(content);
  }

  public static RbelContent wrap(byte[] content) {
    return RbelContentBase.wrap(content);
  }

  public static RbelContent of(String content, Charset charset) {
    return wrap(content.getBytes(charset));
  }

  public static RbelContent concat(RbelContent... parts) {
    return concat(Arrays.asList(parts));
  }

  public static RbelContent concat(List<RbelContent> parts) {
    return RbelContentConcat.create(parts);
  }

  public static RbelContent of(List<RbelContent> content) {
    return RbelContentBase.of(content);
  }

  public static RbelContent from(InputStream stream) throws IOException {
    return RbelContentBase.from(stream);
  }

  public static RbelContentBase.RbelContentBaseBuilder builder() {
    return RbelContentBase.builder();
  }

  static void checkRange(int from, int to, int size) {
    if (from < 0 || from > size) {
      throw new IndexOutOfBoundsException(MessageFormat.format("from: {0}", from));
    }
    if (to < 0 || to > size) {
      throw new IndexOutOfBoundsException(MessageFormat.format("to: {0}", to));
    }
    if (to < from) {
      throw new IndexOutOfBoundsException(MessageFormat.format("from: {0}, to: {1}", from, to));
    }
  }

  public abstract int size();

  public boolean isEmpty() {
    return size() == 0;
  }

  public abstract boolean isNull();

  public abstract byte[] toByteArray();

  public abstract InputStream toInputStream();

  public byte[] toByteArray(int from, int to) {
    return subArray(from, to).toByteArray();
  }

  public abstract RbelContent subArray(int from, int to);

  public abstract byte get(int index);

  public abstract int indexOf(byte o);

  public abstract int indexOf(byte o, int startIndex);

  public abstract int indexOf(byte[] searchContent);

  public abstract int indexOf(byte[] searchContent, int startIndex);

  public abstract boolean startsWith(byte[] prefix);

  public abstract boolean startsWith(byte[] prefix, int startIndex);

  public abstract boolean endsWith(byte[] postfix);

  public abstract boolean startsTrimmedWith(byte[] firstNonBlankBytes);

  public abstract boolean endsTrimmedWith(byte[] lastNonBlankBytes);

  public abstract boolean startsWithIgnoreCase(byte[] firstBytes, Charset charset);

  public abstract boolean startsTrimmedWithIgnoreCase(byte[] firstNonBlankBytes, Charset charset);

  public abstract boolean endsTrimmedWithIgnoreCase(byte[] lastNonBlankBytes, Charset charset);

  public abstract int countOccurrencesUpTo(byte toBeFound, int maxAllowedCount);

  public abstract int getChunkSize();

  // Backwards-compatible signature: historically callers expected this method to return
  // RbelContentBase. Keep that return type to avoid NoSuchMethodError when older
  // compiled callers (or other modules) call this method on the base class.
  public RbelContentBase getBaseContent() {
    if (this instanceof RbelContentBase) {
      return (RbelContentBase) this;
    }
    // For other subclasses (e.g. RbelContentSlice) an overriding method is generated
    // (via Lombok) that returns the correct base content. If we reach here, it's
    // unexpected: throw a clear exception.
    throw new UnsupportedOperationException(
        "getBaseContent not implemented for " + this.getClass());
  }

  public abstract List<RbelContent> split(byte[] delimiter);

  /**
   * The index of the first byte in {@code [from, to)} of this content that the predicate accepts,
   * or -1. Nothing is copied, and the search ends at the first match.
   */
  public int indexOfFirst(IntPredicate matches, int from, int to) {
    int offset = offsetInBase();
    int found = getBaseContent().indexOfFirstInBase(matches, offset + from, offset + to);
    return found < 0 ? -1 : found - offset;
  }

  /** Position of this content in its base content; the base itself starts at 0. */
  public int offsetInBase() {
    return 0;
  }

  record Segment(RbelContentBase base, int start, int size) {

    int end() {
      return start + size;
    }
  }

  List<Segment> segments() {
    return isEmpty() ? List.of() : List.of(new Segment(getBaseContent(), offsetInBase(), size()));
  }

  public abstract boolean contains(byte[] crlfBytes);

  public abstract void truncate(int usedBytes);

  public String toReadableString() {
    return toReadableString(StandardCharsets.UTF_8);
  }

  public String toReadableString(Charset charset) {
    return new String(toByteArray(), charset);
  }
}
