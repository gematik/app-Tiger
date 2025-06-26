/*
 * Copyright 2025 gematik GmbH
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

import com.google.common.base.Preconditions;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.*;
import java.util.function.BiPredicate;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.io.input.BoundedInputStream;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.tuple.Pair;

@Getter
class RbelContentBase extends RbelContent {
  private static final int DEFAULT_CHUNK_SIZE = 8 * 1024;

  private final int chunkSize;
  private int size = 0;
  private @Nullable ArrayList<byte[]> chunks;

  private WeakReference<byte[]> cachedByteArray = new WeakReference<>(null);

  public static RbelContentBase of(byte[] content) {
    return RbelContent.builder().content(content != null ? List.of(content) : null).build();
  }

  public static RbelContentBase of(List<RbelContent> contents) {
    var result = RbelContent.builder().build();
    for (RbelContent content : contents) {
      result.append(content);
    }
    return result;
  }

  public static RbelContentBase from(InputStream stream) throws IOException {
    RbelContentBase result = RbelContent.builder().build();
    do {
      byte[] bytes = stream.readNBytes(DEFAULT_CHUNK_SIZE);
      result.append(bytes);
      if (bytes.length < DEFAULT_CHUNK_SIZE) {
        break;
      }
    } while (true);
    return result;
  }

  @Builder
  private RbelContentBase(@Nullable Integer chunkSize, @Nullable Collection<byte[]> content) {
    this.chunkSize = chunkSize == null ? DEFAULT_CHUNK_SIZE : chunkSize;
    if (this.chunkSize <= 0) {
      throw new IllegalArgumentException("chunkSize must be positive");
    }
    if (content != null) {
      int length = 0;
      for (byte[] chunk : content) {
        length += chunk.length;
      }
      appendContent(content, length);
    }
  }

  public void truncate(int size) {
    if (size < 0 || size > this.size) {
      throw new IndexOutOfBoundsException(
          MessageFormat.format("Invalid size: {0}, current size: {1}", size, this.size));
    }
    this.size = size;
    deleteWeakReferences();
  }

  private byte[] getChunk(int index) {
    assert chunks != null;
    return chunks.get(index / chunkSize);
  }

  private int getIndexInChunk(int index) {
    return index % chunkSize;
  }

  private int getBeginIndexOfChunkContaining(int i) {
    return i - getIndexInChunk(i);
  }

  public boolean add(byte aByte) {
    byte[] lastChunk;
    int chunkIndex = getIndexInChunk(size);
    if (chunkIndex == 0) {
      lastChunk = new byte[chunkSize];
      if (chunks == null) {
        chunks = new ArrayList<>();
      }
      chunks.add(lastChunk);
    } else {
      assert chunks != null;
      assert size > 0;
      lastChunk = chunks.get(chunks.size() - 1);
      if (chunkIndex >= lastChunk.length) {
        byte[] newChunk = new byte[chunkSize];
        System.arraycopy(lastChunk, 0, newChunk, 0, lastChunk.length);
        chunks.set(chunks.size() - 1, newChunk);
        lastChunk = newChunk;
      }
    }
    lastChunk[chunkIndex] = aByte;
    size++;
    deleteWeakReferences();
    return true;
  }

  private void deleteWeakReferences() {
    if (cachedByteArray.get() != null) {
      cachedByteArray = new WeakReference<>(null);
    }
  }

  public void append(byte[] array) {
    if (chunks == null) {
      chunks = new ArrayList<>();
    }
    if (size % chunkSize == 0 && array.length <= chunkSize) {
      chunks.add(array);
      size += array.length;
    } else {
      int i = 0;
      while (i < array.length) {
        var targetIndexInChunk = getIndexInChunk(size);
        var copyLength = Math.min(chunkSize - targetIndexInChunk, array.length - i);
        byte[] chunk;
        if (targetIndexInChunk == 0) {
          // beginning of new chunk
          var newChunk = new byte[copyLength];
          chunks.add(newChunk);
          chunk = newChunk;
        } else {
          // middle/end of existing last chunk
          chunk = getChunk(size);
          if (chunk.length < chunkSize) {
            // copy existing bytes of short chunk
            // into full-sized chunk
            var newLastChunk = new byte[chunkSize];
            System.arraycopy(chunk, 0, newLastChunk, 0, targetIndexInChunk);
            chunks.set(chunks.size() - 1, newLastChunk);
            chunk = newLastChunk;
          }
        }
        System.arraycopy(array, i, chunk, targetIndexInChunk, copyLength);
        size += copyLength;
        i += copyLength;
      }
    }
    deleteWeakReferences();
  }

  public void append(RbelContent content) {
    if (content instanceof RbelContentBase contentBase) {
      appendContent(contentBase.getChunks(), content.size());
    } else {
      append(content.toByteArray());
    }
  }

  private void appendContent(Collection<byte[]> arrays, int bytesToAppend) {
    if (arrays != null && bytesToAppend >= 0) {
      int copied = 0;
      for (byte[] array : arrays) {
        byte[] appended;
        if (copied + array.length <= bytesToAppend) {
          appended = array;
        } else {
          appended = new byte[bytesToAppend - copied];
          System.arraycopy(array, 0, appended, 0, appended.length);
        }
        append(appended);
        copied += appended.length;
      }
    }
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public RbelContentBase getBaseContent() {
    return this;
  }

  @Override
  public List<RbelContent> split(byte[] delimiter) {
    return split(delimiter, 0, size);
  }

  public List<RbelContent> split(byte[] delimiter, int start, int end) {
    var parts = new ArrayList<RbelContent>();
    int nextStart = start;
    while (nextStart + delimiter.length <= end) {
      int foundIndex = indexOf(delimiter, nextStart);
      if (foundIndex < 0) {
        break;
      }
      var part = subArray(nextStart, foundIndex + delimiter.length);
      parts.add(part);
      nextStart = foundIndex + delimiter.length;
    }
    if (nextStart < end) {
      var part = subArray(nextStart, end);
      parts.add(part);
    }
    return parts;
  }

  @Override
  public boolean isEmpty() {
    return size == 0;
  }

  @Override
  public boolean isNull() {
    return chunks == null;
  }

  @Override
  public byte[] toByteArray() {
    var byteArray = cachedByteArray.get();
    if (byteArray == null) {
      byteArray = toByteArrayWithoutChecks(0, size);
      cachedByteArray = new WeakReference<>(byteArray);
    }
    return byteArray;
  }

  @Override
  public InputStream toInputStream() {
    return toInputStream(0, size);
  }

  @SneakyThrows
  public InputStream toInputStream(int start, int end) {
    var result =
        BoundedInputStream.builder()
            .setInputStream(
                new SequenceInputStream(
                    new Enumeration<>() {
                      final Iterator<byte[]> iterator =
                          chunks != null ? chunks.iterator() : Collections.emptyIterator();

                      @Override
                      public boolean hasMoreElements() {
                        return iterator.hasNext();
                      }

                      @Override
                      public InputStream nextElement() {
                        return new ByteArrayInputStream(iterator.next());
                      }
                    }))
            .setMaxCount(end)
            .get();
    long skipped = result.skip(start);
    while (skipped < start) {
      skipped += result.skip(start - skipped);
    }
    return result;
  }

  @Override
  public byte[] toByteArray(int from, int to) {
    checkRange(from, to);
    return toByteArrayWithoutChecks(from, to);
  }

  private void checkRange(int from, int to) {
    checkRange(from, to, size);
  }

  @Override
  public RbelContent subArray(int from, int to) {
    checkRange(from, to);
    return subArrayWithoutChecks(from, to);
  }

  RbelContent subArrayWithoutChecks(int from, int to) {
    if (from == 0 && to == size) {
      return this;
    }
    return new RbelContentSlice(this, from, to);
  }

  byte[] toByteArrayWithoutChecks(int from, int to) {
    if (from % chunkSize == 0 && from < size) {
      assert chunks != null;
      var chunk = chunks.get(from / chunkSize);
      if (chunk.length == to - from) {
        return chunk;
      }
    }
    byte[] result = new byte[to - from];
    int filled = 0;
    while (from < to) {
      byte[] currentChunk = getChunk(from);
      var indexInChunk = getIndexInChunk(from);
      int restLength;
      if ((to - 1) / chunkSize == from / chunkSize) {
        restLength = getIndexInChunk(to - 1) + 1 - indexInChunk;
      } else {
        restLength = chunkSize - indexInChunk;
      }
      System.arraycopy(currentChunk, indexInChunk, result, filled, restLength);
      filled += restLength;
      from += restLength;
    }
    return result;
  }

  @Override
  public byte get(int index) {
    if (index < 0 || index >= size) {
      throw new IndexOutOfBoundsException(index);
    }
    return getWithoutChecks(index);
  }

  byte getWithoutChecks(int index) {
    return getChunk(index)[getIndexInChunk(index)];
  }

  @Override
  public int indexOf(byte b) {
    return indexOf(b, 0);
  }

  @Override
  public int indexOf(byte b, int startIndex) {
    return indexOf(b, startIndex, size);
  }

  public int indexOf(byte b, int startIndex, int endIndex) {
    int i = startIndex;
    while (i < endIndex) {
      var chunk = getChunk(i);
      var foundIndexInChunk = ArrayUtils.indexOf(chunk, b, getIndexInChunk(i));
      var beginOfChunk = getBeginIndexOfChunkContaining(i);
      if (foundIndexInChunk >= 0) {
        var result = beginOfChunk + foundIndexInChunk;
        if (result < endIndex) {
          return result;
        } else {
          return -1;
        }
      } else {
        i = beginOfChunk + chunk.length; // begin of next chunk, if any
      }
    }
    return -1;
  }

  @Override
  public int indexOf(byte[] searchContent) {
    return indexOf(searchContent, 0);
  }

  public int indexOf(byte[] searchContent, int startIndex) {
    return indexOf(searchContent, startIndex, size);
  }

  public int indexOf(byte[] searchContent, int startIndex, int endIndex) {
    Preconditions.checkNotNull(searchContent, "searchContent");
    if (searchContent.length == 0) {
      return startIndex;
    } else {
      int i = startIndex;
      while (i + searchContent.length <= endIndex) {
        var possibleStartIndex = indexOf(searchContent[0], i, endIndex);
        if (possibleStartIndex < 0 || possibleStartIndex + searchContent.length > endIndex) {
          return -1;
        }
        if (searchContent.length == 1 || startsWith(searchContent, possibleStartIndex)) {
          return possibleStartIndex;
        }
        i = possibleStartIndex + 1;
      }
      return -1;
    }
  }

  public int lastIndexOf(byte o, int endIndex) {
    int i = endIndex - 1;
    while (i >= 0) {
      var chunk = getChunk(i);
      var foundIndex = ArrayUtils.lastIndexOf(chunk, o, getIndexInChunk(i));
      var beginOfChunk = getBeginIndexOfChunkContaining(i);
      if (foundIndex >= 0) {
        return beginOfChunk + foundIndex;
      } else {
        i = beginOfChunk - 1; // right at end of previous chunk, if any
      }
    }
    return -1;
  }

  @Override
  public boolean startsWith(byte[] prefix) {
    if (prefix == null) {
      return false;
    }
    int prefixLength = prefix.length;

    if (prefix.length > size) {
      return false;
    }

    for (int i = 0; i < prefixLength; i++) {
      if (getWithoutChecks(i) != prefix[i]) {
        return false;
      }
    }

    return true;
  }

  private boolean startsWithIgnoreCaseWithoutChecks(
      Pair<byte[], Charset> prefix, int startInclusive) {
    byte[] prefixBytes = prefix.getLeft();
    if (startInclusive + prefixBytes.length <= size) {
      Charset charset = prefix.getRight();
      var prefixString = new String(prefixBytes, charset);
      byte[] start = new byte[prefixBytes.length];
      for (int i = 0; i < start.length; i++) {
        start[i] = getWithoutChecks(startInclusive + i);
      }
      return new String(start, charset).equalsIgnoreCase(prefixString);
    }
    return false;
  }

  public boolean startsWith(byte[] searchContent, int startIndex) {
    for (int j = 0, k = startIndex; j < searchContent.length; j++, k++) {
      if (getWithoutChecks(k) != searchContent[j]) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean startsTrimmedWith(byte[] firstNonBlankBytes) {
    return startsTrimmedWith(firstNonBlankBytes, 0);
  }

  public boolean startsTrimmedWith(byte[] firstNonBlankBytes, int startIndex) {
    return startsTrimmedWith(
        firstNonBlankBytes, null, startIndex, (pair, start) -> startsWith(pair.getLeft(), start));
  }

  @Override
  public boolean startsTrimmedWithIgnoreCase(byte[] firstNonBlankBytes, Charset charset) {
    return startsTrimmedWithIgnoreCase(firstNonBlankBytes, charset, 0);
  }

  public boolean startsTrimmedWithIgnoreCase(
      byte[] firstNonBlankBytes, Charset charset, int startIndex) {
    return startsTrimmedWith(
        firstNonBlankBytes, charset, startIndex, this::startsWithIgnoreCaseWithoutChecks);
  }

  private boolean startsTrimmedWith(
      byte[] firstNonBlankBytes,
      Charset charset,
      int startIndex,
      BiPredicate<Pair<byte[], Charset>, Integer> doesArrayAtOffsetStartWith) {
    for (int i = startIndex; i < size; i++) {
      if (!Character.isWhitespace(getWithoutChecks(i))) {
        if (i + firstNonBlankBytes.length > size) {
          return false;
        }
        return doesArrayAtOffsetStartWith.test(Pair.of(firstNonBlankBytes, charset), i);
      }
    }
    return false;
  }

  @Override
  public boolean endsTrimmedWith(byte[] lastNonBlankBytes) {
    return endsTrimmedWith(lastNonBlankBytes, size);
  }

  public boolean endsTrimmedWith(byte[] lastNonBlankBytes, int endIndexExclusive) {
    return endsTrimmedWith(
        lastNonBlankBytes,
        null,
        endIndexExclusive,
        (pair, index) -> endsWith(pair.getLeft(), index));
  }

  @Override
  public boolean endsTrimmedWithIgnoreCase(byte[] lastNonBlankBytes, Charset charset) {
    return endsTrimmedWithIgnoreCase(lastNonBlankBytes, charset, size);
  }

  public boolean endsTrimmedWithIgnoreCase(
      byte[] lastNonBlankBytes, Charset charset, int endIndex) {
    return endsTrimmedWith(
        lastNonBlankBytes, charset, endIndex, this::endsWithIgnoreCaseWithoutChecks);
  }

  private boolean endsTrimmedWith(
      byte[] lastNonBlankBytes,
      Charset charset,
      int endIndexExclusive,
      BiPredicate<Pair<byte[], Charset>, Integer> doesArrayAtOffsetEndWith) {
    for (int i = endIndexExclusive - 1; i >= 0; i--) {
      if (!Character.isWhitespace(getWithoutChecks(i))) {
        var beginIndex = i - lastNonBlankBytes.length + 1;
        if (beginIndex < 0) {
          return false;
        }
        return doesArrayAtOffsetEndWith.test(Pair.of(lastNonBlankBytes, charset), i + 1);
      }
    }
    return false;
  }

  @Override
  public boolean endsWith(byte[] postfix) {
    return endsWith(postfix, size);
  }

  public boolean endsWith(byte[] postfix, int endIndexExclusive) {
    if (postfix.length > endIndexExclusive) {
      return false;
    }
    for (int postfixIndex = postfix.length - 1, contentIndex = endIndexExclusive - 1;
        postfixIndex >= 0;
        --postfixIndex, --contentIndex) {
      if (postfix[postfixIndex] != getWithoutChecks(contentIndex)) {
        return false;
      }
    }
    return true;
  }

  private boolean endsWithIgnoreCaseWithoutChecks(Pair<byte[], Charset> suffix, int endExclusive) {
    byte[] suffixBytes = suffix.getLeft();
    if (endExclusive - suffixBytes.length >= 0) {
      Charset charset = suffix.getRight();
      var suffixString = new String(suffixBytes, charset);
      byte[] end = new byte[suffixBytes.length];
      for (int i = 0; i < end.length; i++) {
        end[i] = getWithoutChecks(endExclusive - end.length + i);
      }
      return new String(end, charset).equalsIgnoreCase(suffixString);
    }
    return false;
  }

  @Override
  public int countOccurrencesUpTo(byte toBeFound, int maxAllowedCount) {
    return countOccurrencesUpTo(toBeFound, maxAllowedCount, 0, size);
  }

  public int countOccurrencesUpTo(byte toBeFound, int maxAllowedCount, int start, int end) {
    int foundCount = 0;
    int searchIndex = start;
    while (searchIndex < end) {
      int nextFoundIndex = indexOf(toBeFound, searchIndex, end);
      if (nextFoundIndex < 0) {
        return foundCount; // no more occurrences to be found
      } else {
        foundCount++;
        if (foundCount == maxAllowedCount) {
          return foundCount; // no need to search more than max allowed
        }
        searchIndex = nextFoundIndex + 1;
      }
    }
    return foundCount; // fewer found than max allowed
  }

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

  public String toString() {
    return "RbelContentBase{" + "size=" + size + "}:" + toReadableString();
  }
}
