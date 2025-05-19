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

import de.gematik.rbellogger.data.RbelElement;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EmailConversionUtils {
  public static final String CRLF = "\r\n";
  public static final byte[] CRLF_BYTES = CRLF.getBytes();
  public static final String CRLF_DOT_CRLF = CRLF + "." + CRLF;

  public static RbelElement createChildElement(RbelElement parent, String value) {
    return new RbelElement(value.getBytes(StandardCharsets.UTF_8), parent);
  }

  public static RbelElement parseMailBody(RbelElement element, String[] lines, int startLine) {
    if (lines.length - startLine + 1 > 2) {
      var body = extractBodyAndRemoveStuffedDots(lines, startLine);
      return createChildElement(element, body);
    }
    return null;
  }

  private static String extractBodyAndRemoveStuffedDots(String[] lines, int startLine) {
    return Arrays.asList(lines).subList(startLine, lines.length - 2).stream()
        .map(EmailConversionUtils::removeStuffedDot)
        .collect(Collectors.joining(CRLF));
  }

  public static String removeStuffedDot(String line) {
    return line.startsWith(".") ? line.substring(1) : line;
  }

  public static String duplicateDotsAtLineBegins(String input) {
    return Stream.of(input.split("\r\n", -1))
        .map(line -> line.startsWith(".") ? "." + line : line)
        .collect(Collectors.joining("\r\n"));
  }
}
