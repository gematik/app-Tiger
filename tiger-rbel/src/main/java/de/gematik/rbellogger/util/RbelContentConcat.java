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

import java.io.InputStream;
import java.io.SequenceInputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.IntPredicate;
import lombok.SneakyThrows;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Several contents of any size one after the other, without copying them. The parts must not be
 * changed afterwards.
 */
class RbelContentConcat extends RbelContent {

  private RbelContent[] parts;
  private int[] startOf;
  private int size;
  private WeakReference<byte[]> flat = new WeakReference<>(null);

  static RbelContent create(List<RbelContent> input) {
    List<Segment> runs = new ArrayList<>();
    for (RbelContent part : input) {
      for (Segment run : part.segments()) {
        addMerged(runs, run);
      }
    }
    if (runs.isEmpty()) {
      return RbelContent.of(new byte[0]);
    }
    return new RbelContentConcat(runs.stream().map(RbelContentConcat::viewOf).toList());
  }

  private static void addMerged(List<Segment> runs, Segment run) {
    int last = runs.size() - 1;
    boolean continuesTheLast =
        last >= 0 && runs.get(last).base() == run.base() && runs.get(last).end() == run.start();
    if (continuesTheLast) {
      Segment before = runs.get(last);
      runs.set(last, new Segment(run.base(), before.start(), before.size() + run.size()));
    } else {
      runs.add(run);
    }
  }

  private static RbelContent viewOf(Segment run) {
    return new RbelContentSlice(run.base(), run.start(), run.end());
  }

  private RbelContentConcat(List<RbelContent> nonEmptyParts) {
    parts = nonEmptyParts.toArray(RbelContent[]::new);
    startOf = new int[parts.length];
    int total = 0;
    for (int i = 0; i < parts.length; i++) {
      startOf[i] = total;
      total = Math.addExact(total, parts[i].size());
    }
    size = total;
  }

  @Override
  List<Segment> segments() {
    List<Segment> runs = new ArrayList<>(parts.length);
    for (RbelContent part : parts) {
      runs.addAll(part.segments());
    }
    return runs;
  }

  private int partIndexAt(int index) {
    int found = Arrays.binarySearch(startOf, index);
    return found >= 0 ? found : -found - 2;
  }

  private void checkIndex(int index) {
    if (index < 0 || index >= size) {
      throw new IndexOutOfBoundsException(index);
    }
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public boolean isNull() {
    return false;
  }

  @Override
  public void truncate(int usedBytes) {
    if (usedBytes < 0 || usedBytes > size) {
      throw new IndexOutOfBoundsException("Invalid size: " + usedBytes + ", current size: " + size);
    }
    if (usedBytes == size) {
      return;
    }
    if (usedBytes == 0) {
      parts = new RbelContent[0];
      startOf = new int[0];
    } else {
      int last = partIndexAt(usedBytes - 1);
      parts = Arrays.copyOf(parts, last + 1);
      parts[last] = parts[last].subArray(0, usedBytes - startOf[last]);
      startOf = Arrays.copyOf(startOf, last + 1);
    }
    size = usedBytes;
    flat = new WeakReference<>(null);
  }

  @Override
  public byte get(int index) {
    checkIndex(index);
    int part = partIndexAt(index);
    return parts[part].get(index - startOf[part]);
  }

  @Override
  public byte[] toByteArray() {
    if (parts.length == 1) {
      return parts[0].toByteArray();
    }
    byte[] result = flat.get();
    if (result == null) {
      result = copyOfRange(0, size);
      flat = new WeakReference<>(result);
    }
    return result;
  }

  @Override
  public byte[] toByteArray(int from, int to) {
    checkRange(from, to, size);
    return from == 0 && to == size ? toByteArray() : copyOfRange(from, to);
  }

  @SneakyThrows
  private byte[] copyOfRange(int from, int to) {
    byte[] result = new byte[to - from];
    if (result.length == 0) {
      return result;
    }
    int filled = 0;
    for (int i = partIndexAt(from); filled < result.length; i++) {
      int partFrom = Math.max(0, from - startOf[i]);
      int partTo = Math.min(parts[i].size(), to - startOf[i]);
      try (InputStream stream = parts[i].subArray(partFrom, partTo).toInputStream()) {
        filled += stream.readNBytes(result, filled, partTo - partFrom);
      }
    }
    return result;
  }

  @Override
  public RbelContent subArray(int from, int to) {
    checkRange(from, to, size);
    if (from == 0 && to == size) {
      return this;
    }
    if (from == to) {
      return RbelContent.of(new byte[0]);
    }
    int first = partIndexAt(from);
    int last = partIndexAt(to - 1);
    if (first == last) {
      // wrapped, so that truncating the result cannot reach the part that others share
      return new RbelContentConcat(
          List.of(parts[first].subArray(from - startOf[first], to - startOf[first])));
    }
    var covered = new ArrayList<RbelContent>(last - first + 1);
    covered.add(parts[first].subArray(from - startOf[first], parts[first].size()));
    covered.addAll(Arrays.asList(parts).subList(first + 1, last));
    covered.add(parts[last].subArray(0, to - startOf[last]));
    return new RbelContentConcat(covered);
  }

  @Override
  public InputStream toInputStream() {
    final Iterator<RbelContent> iterator = Arrays.asList(parts).iterator();
    return new SequenceInputStream(
        new Enumeration<>() {
          @Override
          public boolean hasMoreElements() {
            return iterator.hasNext();
          }

          @Override
          public InputStream nextElement() {
            return iterator.next().toInputStream();
          }
        });
  }

  @Override
  public int indexOf(byte o) {
    return indexOf(o, 0);
  }

  @Override
  public int indexOf(byte o, int startIndex) {
    if (startIndex >= size) {
      return -1;
    }
    int from = Math.max(0, startIndex);
    for (int i = partIndexAt(from); i < parts.length; i++) {
      int found = parts[i].indexOf(o, Math.max(0, from - startOf[i]));
      if (found >= 0) {
        return startOf[i] + found;
      }
    }
    return -1;
  }

  @Override
  public int indexOf(byte[] searchContent) {
    return indexOf(searchContent, 0);
  }

  @Override
  public int indexOf(byte[] searchContent, int startIndex) {
    Objects.requireNonNull(searchContent, "searchContent");
    if (searchContent.length == 0) {
      return startIndex;
    }
    int i = Math.max(0, startIndex);
    while (i + searchContent.length <= size) {
      int candidate = indexOf(searchContent[0], i);
      if (candidate < 0 || candidate + searchContent.length > size) {
        return -1;
      }
      if (startsWith(searchContent, candidate)) {
        return candidate;
      }
      i = candidate + 1;
    }
    return -1;
  }

  @Override
  public int indexOfFirst(IntPredicate matches, int from, int to) {
    if (parts.length == 0 || from >= Math.min(to, size)) {
      return -1;
    }
    for (int i = partIndexAt(Math.max(0, from)); i < parts.length && startOf[i] < to; i++) {
      int found =
          parts[i].indexOfFirst(
              matches, Math.max(0, from - startOf[i]), Math.min(parts[i].size(), to - startOf[i]));
      if (found >= 0) {
        return startOf[i] + found;
      }
    }
    return -1;
  }

  @Override
  public boolean startsWith(byte[] prefix) {
    return prefix != null && prefix.length <= size && startsWith(prefix, 0);
  }

  @Override
  public boolean startsWith(byte[] prefix, int startIndex) {
    if (startIndex < 0 || startIndex + prefix.length > size) {
      return false;
    }
    for (int i = 0; i < prefix.length; i++) {
      if (get(startIndex + i) != prefix[i]) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean endsWith(byte[] postfix) {
    return postfix.length <= size && startsWith(postfix, size - postfix.length);
  }

  @Override
  public boolean startsWithIgnoreCase(byte[] firstBytes, Charset charset) {
    return firstBytes.length <= size && equalsIgnoreCaseAt(0, firstBytes, charset);
  }

  @Override
  public boolean startsTrimmedWith(byte[] firstNonBlankBytes) {
    return startsTrimmedWith(firstNonBlankBytes, null);
  }

  @Override
  public boolean startsTrimmedWithIgnoreCase(byte[] firstNonBlankBytes, Charset charset) {
    return startsTrimmedWith(firstNonBlankBytes, charset);
  }

  private boolean startsTrimmedWith(byte[] bytes, Charset charset) {
    for (int i = 0; i < size; i++) {
      if (!Character.isWhitespace(get(i))) {
        if (i + bytes.length > size) {
          return false;
        }
        return charset == null ? startsWith(bytes, i) : equalsIgnoreCaseAt(i, bytes, charset);
      }
    }
    return false;
  }

  @Override
  public boolean endsTrimmedWith(byte[] lastNonBlankBytes) {
    return endsTrimmedWith(lastNonBlankBytes, null);
  }

  @Override
  public boolean endsTrimmedWithIgnoreCase(byte[] lastNonBlankBytes, Charset charset) {
    return endsTrimmedWith(lastNonBlankBytes, charset);
  }

  private boolean endsTrimmedWith(byte[] bytes, Charset charset) {
    for (int i = size - 1; i >= 0; i--) {
      if (!Character.isWhitespace(get(i))) {
        int begin = i - bytes.length + 1;
        if (begin < 0) {
          return false;
        }
        return charset == null
            ? startsWith(bytes, begin)
            : equalsIgnoreCaseAt(begin, bytes, charset);
      }
    }
    return false;
  }

  private boolean equalsIgnoreCaseAt(int start, byte[] bytes, Charset charset) {
    return new String(toByteArray(start, start + bytes.length), charset)
        .equalsIgnoreCase(new String(bytes, charset));
  }

  @Override
  public int countOccurrencesUpTo(byte toBeFound, int maxAllowedCount) {
    int foundCount = 0;
    int searchIndex = 0;
    while (searchIndex < size) {
      int next = indexOf(toBeFound, searchIndex);
      if (next < 0) {
        return foundCount;
      }
      foundCount++;
      if (foundCount == maxAllowedCount) {
        return foundCount;
      }
      searchIndex = next + 1;
    }
    return foundCount;
  }

  @Override
  public int getChunkSize() {
    return parts.length > 0
        ? parts[0].getChunkSize()
        : RbelContent.builder().build().getChunkSize();
  }

  @Override
  public List<RbelContent> split(byte[] delimiter) {
    var result = new ArrayList<RbelContent>();
    int nextStart = 0;
    while (nextStart + delimiter.length <= size) {
      int found = indexOf(delimiter, nextStart);
      if (found < 0) {
        break;
      }
      result.add(subArray(nextStart, found + delimiter.length));
      nextStart = found + delimiter.length;
    }
    if (nextStart < size) {
      result.add(subArray(nextStart, size));
    }
    return result;
  }

  @Override
  public boolean contains(byte[] searchContent) {
    return indexOf(searchContent) >= 0;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;

    if (!(o instanceof RbelContent that)) return false;

    if (this.size() != that.size()) return false;

    return Arrays.equals(this.toByteArray(), that.toByteArray());
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37).append(size).append(toByteArray()).toHashCode();
  }

  @Override
  public String toString() {
    return "RbelContentConcat{parts=" + parts.length + ", size=" + size + "}:" + toReadableString();
  }
}
