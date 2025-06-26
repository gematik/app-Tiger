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
import java.util.List;
import javax.annotation.Nullable;

@SuppressWarnings("java:S1610")
public abstract class RbelContent {
  public static RbelContent of(@Nullable byte[] content) {
    return RbelContentBase.of(content);
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

  public abstract boolean startsTrimmedWithIgnoreCase(byte[] firstNonBlankBytes, Charset charset);

  public abstract boolean endsTrimmedWithIgnoreCase(byte[] lastNonBlankBytes, Charset charset);

  public abstract int countOccurrencesUpTo(byte toBeFound, int maxAllowedCount);

  public abstract int getChunkSize();

  public RbelContentBase getBaseContent() {
    return (RbelContentBase) this;
  }

  public abstract List<RbelContent> split(byte[] delimiter);

  public abstract boolean contains(byte[] crlfBytes);

  public abstract void truncate(int usedBytes);

  public String toReadableString() {
    return new String(toByteArray(), StandardCharsets.UTF_8);
  }
}
