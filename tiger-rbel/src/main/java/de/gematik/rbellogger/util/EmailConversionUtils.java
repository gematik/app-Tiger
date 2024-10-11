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
import de.gematik.rbellogger.exceptions.RbelConversionException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EmailConversionUtils {
  public static final String CRLF = "\r\n";
  public static final String CRLF_DOT_CRLF = CRLF + "." + CRLF;

  public static RbelElement createChildElement(RbelElement parent, String value) {
    return new RbelElement(value.getBytes(StandardCharsets.UTF_8), parent);
  }

  public static boolean endsWithCrLf(byte[] c) {
    return c[c.length - 2] == '\r' && c[c.length - 1] == '\n';
  }

  public static RbelElement parseMailBody(RbelElement element, String[] lines) {
    if (lines.length > 2) {
      var body = extractBodyAndRemoveStuffedDots(lines);
      return createChildElement(element, body);
    }
    return null;
  }

  private static String extractBodyAndRemoveStuffedDots(String[] lines) {
    return Arrays.asList(lines).subList(1, lines.length - 2).stream()
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

  public static boolean hasCompleteLines(byte[] content, int requiredCount) {
    if (requiredCount < 0) {
      throw new RbelConversionException(
          "hasCompleteLines needs non-negative requiredCount, but got: " + requiredCount);
    }
    int linesEndingInCrLf = 0;
    // searchIndex is the next index from which to search for the next
    // occurrence of CRLF in content
    int searchIndex = 0;
    // searchIndex == content.length - 1 ==> CRLF not possible anymore
    while (searchIndex + 1 < content.length) {
      int crIndex = ArrayUtils.indexOf(content, (byte) '\r', searchIndex);
      if (crIndex < 0) {
        // No CR found, so no more CRLF possible after searchIndex
        break;
      }
      // After finding a CR, we check for following LF and if found, increase
      // the searchIndex to the index after the found CRLF
      if (crIndex + 1 < content.length && content[crIndex + 1] == '\n') {
        ++linesEndingInCrLf;
        if (linesEndingInCrLf > requiredCount) {
          return false;
        }
        searchIndex = crIndex + 2;
      } else {
        // CR found, but no following LF ==> not a proper line ending
        ++searchIndex;
      }
    }
    return linesEndingInCrLf == requiredCount;
  }
}
