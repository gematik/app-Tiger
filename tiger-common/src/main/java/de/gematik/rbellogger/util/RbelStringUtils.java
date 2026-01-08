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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RbelStringUtils {
  public static String bytesToStringWithoutNonPrintableCharacters(byte[] content, int maxLength) {
    return StringUtils.abbreviate(bytesToStringWithoutNonPrintableCharacters(content), maxLength);
  }

  public static String bytesToStringWithoutNonPrintableCharacters(byte[] content) {
    return new String(content)
        .replace("\r\n", "<CRLF>")
        .replace("\n", "<LF>")
        .replace("\r", "<CR>");
  }

  public static <T> InputStream mapAndJoinAsInputStream(
      Collection<T> elements, Function<T, String> converter, String delimiter) {
    var streams =
        elements.stream()
            .map(converter)
            .flatMap(s -> Stream.of(s, delimiter))
            .limit(Math.max(elements.size() * 2L - 1, 0)) // avoid trailing delimiter
            .map(s -> s.getBytes(StandardCharsets.UTF_8))
            .map(ByteArrayInputStream::new);

    return new SequenceInputStream(iteratorToEnumeration(streams.iterator()));
  }

  /**
   * Generic utility method to convert an Iterator to an Enumeration. Useful for legacy APIs that
   * require Enumeration (like SequenceInputStream).
   */
  public static <T> Enumeration<T> iteratorToEnumeration(Iterator<T> iterator) {
    return new Enumeration<>() {
      @Override
      public boolean hasMoreElements() {
        return iterator.hasNext();
      }

      @Override
      public T nextElement() {
        return iterator.next();
      }
    };
  }
}
