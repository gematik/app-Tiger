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

import com.google.common.base.Preconditions;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiPredicate;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Getter;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;

@Getter
public class RbelContent {
  private static final int DEFAULT_CHUNK_SIZE = 8 * 1024;

  private final int chunkSize;
  private int size = 0;
  private @Nullable ArrayList<byte[]> chunks;

  private WeakReference<byte[]> cachedByteArray = new WeakReference<>(null);

  public static RbelContent of(@Nullable byte[] content) {
    return builder().content(content != null ? List.of(content) : null).build();
  }

  public static RbelContent from(InputStream stream) throws IOException {
    RbelContent result = RbelContent.builder().build();
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
  private RbelContent(@Nullable Integer chunkSize, @Nullable Collection<byte[]> content) {
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
    appendContent(content.getChunks(), content.size);
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

  public int size() {
    return size;
  }

  public boolean isEmpty() {
    return size == 0;
  }

  public boolean isNull() {
    return chunks == null;
  }

  public String toString() {
    return MessageFormat.format(
        "{0}({1} chunks, size={2}, chunkSize={3})",
        this.getClass().getSimpleName(), chunks != null ? chunks.size() : "no", size, chunkSize);
  }

  public byte[] toByteArray() {
    var byteArray = cachedByteArray.get();
    if (byteArray == null) {
      byteArray = subArrayWithoutChecks(0, size);
      cachedByteArray = new WeakReference<>(byteArray);
    }
    return byteArray;
  }

  public InputStream toInputStream() {
    return new SequenceInputStream(
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
        });
  }

  public byte[] subArray(int from, int to) {
    if (from < 0 || from > size) {
      throw new IndexOutOfBoundsException(MessageFormat.format("from: {0}", from));
    }
    if (to < 0 || to > size) {
      throw new IndexOutOfBoundsException(MessageFormat.format("to: {0}", to));
    }
    if (to < from) {
      throw new IndexOutOfBoundsException(MessageFormat.format("from: {0}, to: {1}", from, to));
    }
    return subArrayWithoutChecks(from, to);
  }

  private byte[] subArrayWithoutChecks(int from, int to) {
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

  public byte get(int index) {
    if (index < 0 || index >= size) {
      throw new IndexOutOfBoundsException(index);
    }
    return getWithoutChecks(index);
  }

  private byte getWithoutChecks(int index) {
    return getChunk(index)[getIndexInChunk(index)];
  }

  public int indexOf(byte o) {
    return indexOf(o, 0);
  }

  public int indexOf(byte o, int startIndex) {
    int i = startIndex;
    while (i < size) {
      var chunk = getChunk(i);
      var foundIndexInChunk = ArrayUtils.indexOf(chunk, o, getIndexInChunk(i));
      var beginOfChunk = getBeginIndexOfChunkContaining(i);
      if (foundIndexInChunk >= 0) {
        var result = beginOfChunk + foundIndexInChunk;
        if (result < size) {
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

  public int indexOf(byte[] searchContent) {
    return indexOf(searchContent, 0);
  }

  public int indexOf(byte[] searchContent, int startIndex) {
    Preconditions.checkNotNull(searchContent, "searchContent");
    if (searchContent.length == 0) {
      return startIndex;
    } else {
      int i = startIndex;
      while (i + searchContent.length <= size) {
        var possibleStartIndex = indexOf(searchContent[0], i);
        if (possibleStartIndex < 0 || possibleStartIndex + searchContent.length > size) {
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

  public int lastIndexOf(byte o) {
    return lastIndexOf(o, size);
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

  private boolean startsWith(byte[] searchContent, int startIndex) {
    for (int j = 0, k = startIndex; j < searchContent.length; j++, k++) {
      if (getWithoutChecks(k) != searchContent[j]) {
        return false;
      }
    }
    return true;
  }

  public boolean startsTrimmedWith(byte[] firstNonBlankBytes) {
    return startsTrimmedWith(
        firstNonBlankBytes, null, (pair, startIndex) -> startsWith(pair.getLeft(), startIndex));
  }

  public boolean startsTrimmedWithIgnoreCase(byte[] firstNonBlankBytes, Charset charset) {
    return startsTrimmedWith(firstNonBlankBytes, charset, this::startsWithIgnoreCaseWithoutChecks);
  }

  private boolean startsTrimmedWith(
      byte[] firstNonBlankBytes,
      Charset charset,
      BiPredicate<Pair<byte[], Charset>, Integer> doesArrayAtOffsetStartWith) {
    for (int i = 0; i < size; i++) {
      if (!Character.isWhitespace(getWithoutChecks(i))) {
        if (i + firstNonBlankBytes.length > size) {
          return false;
        }
        return doesArrayAtOffsetStartWith.test(Pair.of(firstNonBlankBytes, charset), i);
      }
    }
    return false;
  }

  public boolean endsTrimmedWith(byte[] lastNonBlankBytes) {
    return endsTrimmedWith(
        lastNonBlankBytes, null, (pair, index) -> endsWith(pair.getLeft(), index));
  }

  public boolean endsTrimmedWithIgnoreCase(byte[] lastNonBlankBytes, Charset charset) {
    return endsTrimmedWith(lastNonBlankBytes, charset, this::endsWithIgnoreCaseWithoutChecks);
  }

  private boolean endsTrimmedWith(
      byte[] lastNonBlankBytes,
      Charset charset,
      BiPredicate<Pair<byte[], Charset>, Integer> doesArrayAtOffsetEndWith) {
    for (int i = size - 1; i >= 0; i--) {
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

  public int countOccurrencesUpTo(byte toBeFound, int maxAllowedCount) {
    int foundCount = 0;
    int searchIndex = 0;
    while (searchIndex < size()) {
      int nextFoundIndex = indexOf(toBeFound, searchIndex);
      if (nextFoundIndex < 0) {
        return foundCount; // no more dots to be found
      } else {
        foundCount++;
        if (foundCount == maxAllowedCount) {
          return foundCount; // no need to search more than max allowed
        }
        searchIndex = nextFoundIndex + 1;
      }
    }
    return foundCount; // fewer than max allowed found
  }
}
