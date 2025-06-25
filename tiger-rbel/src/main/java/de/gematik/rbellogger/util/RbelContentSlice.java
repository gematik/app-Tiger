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
 */

package de.gematik.rbellogger.util;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.builder.HashCodeBuilder;

@AllArgsConstructor
class RbelContentSlice extends RbelContent {

  @Getter private final RbelContentBase baseContent;
  private final int start;
  private int end;

  @Override
  public void truncate(int usedBytes) {
    if (usedBytes < 0 || usedBytes > size()) {
      throw new IllegalArgumentException(
          String.format("Invalid truncate size: %d, content size: %d", usedBytes, size()));
    }
    end = start + usedBytes;
  }

  @Override
  public int size() {
    return end - start;
  }

  @Override
  public boolean isEmpty() {
    return size() == 0;
  }

  @Override
  public boolean isNull() {
    return baseContent.isNull();
  }

  @Override
  public byte[] toByteArray() {
    return baseContent.toByteArray(start, end);
  }

  private void checkRange(int from, int to) {
    checkRange(from, to, size());
  }

  private void checkIndex(int index) {
    if (index < 0 || index >= size()) {
      throw new IndexOutOfBoundsException(index);
    }
  }

  @Override
  public RbelContent subArray(int from, int to) {
    checkRange(from, to);
    if (from == 0 && to == size()) {
      return this;
    }
    return baseContent.subArrayWithoutChecks(from + start, to + start);
  }

  @Override
  public InputStream toInputStream() {
    return baseContent.toInputStream(start, end);
  }

  @Override
  public byte[] toByteArray(int from, int to) {
    checkRange(from, to);
    return baseContent.toByteArrayWithoutChecks(start + from, start + to);
  }

  @Override
  public byte get(int index) {
    checkIndex(index);
    return baseContent.getWithoutChecks(index + start);
  }

  @Override
  public int indexOf(byte o) {
    if (isEmpty()) {
      return -1;
    }
    return indexOf(o, 0);
  }

  @Override
  public int indexOf(byte o, int startIndex) {
    checkIndex(startIndex);
    return Math.max(-1, baseContent.indexOf(o, start + startIndex, end) - start);
  }

  @Override
  public int indexOf(byte[] searchContent) {
    return indexOf(searchContent, 0);
  }

  public int indexOf(byte[] searchContent, int startIndex) {
    checkIndex(startIndex);
    return Math.max(-1, baseContent.indexOf(searchContent, start + startIndex, end) - start);
  }

  @Override
  public boolean startsWith(byte[] prefix) {
    if (isEmpty()) {
      return prefix.length == 0;
    }
    return startsWith(prefix, 0);
  }

  public boolean startsWith(byte[] searchContent, int startIndex) {
    checkIndex(startIndex);
    return baseContent.startsWith(searchContent, start + startIndex);
  }

  @Override
  public boolean startsTrimmedWith(byte[] firstNonBlankBytes) {
    if (isEmpty()) {
      return firstNonBlankBytes.length == 0;
    }
    return startsTrimmedWith(firstNonBlankBytes, 0);
  }

  public boolean startsTrimmedWith(byte[] firstNonBlankBytes, int startIndex) {
    checkIndex(startIndex);
    return baseContent.startsTrimmedWith(firstNonBlankBytes, start + startIndex);
  }

  @Override
  public boolean startsTrimmedWithIgnoreCase(byte[] firstNonBlankBytes, Charset charset) {
    return startsTrimmedWithIgnoreCase(firstNonBlankBytes, charset, 0);
  }

  public boolean startsTrimmedWithIgnoreCase(
      byte[] firstNonBlankBytes, Charset charset, int startIndex) {
    checkIndex(startIndex);
    return baseContent.startsTrimmedWithIgnoreCase(firstNonBlankBytes, charset, start + startIndex);
  }

  @Override
  public boolean endsTrimmedWith(byte[] lastNonBlankBytes) {
    return endsTrimmedWith(lastNonBlankBytes, size());
  }

  public boolean endsTrimmedWith(byte[] lastNonBlankBytes, int endIndex) {
    checkRange(0, endIndex);
    return baseContent.endsTrimmedWith(lastNonBlankBytes, start + endIndex);
  }

  @Override
  public boolean endsTrimmedWithIgnoreCase(byte[] lastNonBlankBytes, Charset charset) {
    return endsTrimmedWithIgnoreCase(lastNonBlankBytes, charset, size());
  }

  public boolean endsTrimmedWithIgnoreCase(
      byte[] lastNonBlankBytes, Charset charset, int endIndex) {
    checkRange(0, endIndex);
    return baseContent.endsTrimmedWithIgnoreCase(lastNonBlankBytes, charset, start + endIndex);
  }

  @Override
  public boolean endsWith(byte[] postfix) {
    return endsWith(postfix, size());
  }

  public boolean endsWith(byte[] postfix, int endIndex) {
    checkRange(0, endIndex);
    return baseContent.endsWith(postfix, start + endIndex);
  }

  @Override
  public int countOccurrencesUpTo(byte toBeFound, int maxAllowedCount) {
    return baseContent.countOccurrencesUpTo(toBeFound, maxAllowedCount, start, end);
  }

  public boolean contains(byte[] searchContent) {
    return baseContent.indexOf(searchContent, start, end) >= 0;
  }

  @Override
  public int getChunkSize() {
    return baseContent.getChunkSize();
  }

  @Override
  public List<RbelContent> split(byte[] delimiter) {
    return baseContent.split(delimiter, start, end);
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
    return new HashCodeBuilder(17, 37).append(size()).append(toByteArray()).toHashCode();
  }

  public String toString() {
    return "RbelContentSlice{" + "start=" + start + ", end=" + end + "}:" + toReadableString();
  }
}
