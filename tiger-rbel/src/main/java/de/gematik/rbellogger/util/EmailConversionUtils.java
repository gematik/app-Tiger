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

import de.gematik.rbellogger.data.RbelElement;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EmailConversionUtils {
  public static final String CRLF = "\r\n";
  public static final byte[] CRLF_BYTES = CRLF.getBytes();
  public static final String CRLF_DOT_CRLF = CRLF + "." + CRLF;
  private static final byte[] DOT_BYTE = ".".getBytes();

  public static RbelElement createChildElement(RbelElement parent, String value) {
    return new RbelElement(value.getBytes(StandardCharsets.UTF_8), parent);
  }

  public static RbelElement parseMailBody(
      RbelElement element, List<RbelContent> lines, int startLine) {
    if (lines.size() > startLine + 1) {
      var body = extractBodyAndRemoveStuffedDots(lines, startLine);
      return RbelElement.builder().content(body).parentNode(element).build();
    }
    return null;
  }

  public static RbelContent removeStuffedDot(RbelContent line) {
    return line.startsWith(DOT_BYTE) ? line.subArray(1, line.size()) : line;
  }

  private static RbelContent extractBodyAndRemoveStuffedDots(
      List<RbelContent> lines, int startLine) {
    RbelContent baseContent = lines.get(0).getBaseContent();
    assert lines.stream().allMatch(line -> line.getBaseContent() == baseContent);
    var bodyLines =
        getMiddleLines(lines, startLine).map(EmailConversionUtils::removeStuffedDot).toList();
    int bodyLength = computeTotalLength(bodyLines.stream());
    int originalLength = computeTotalLength(getMiddleLines(lines, startLine));
    if (bodyLength == originalLength) {
      // no stuffed dots found ==> we can reference the original content
      int firstLineLength = lines.get(0).size();
      return baseContent.subArray(
          firstLineLength, firstLineLength + originalLength - CRLF_BYTES.length);
    }
    return mergeLines(bodyLines);
  }

  private static Integer computeTotalLength(Stream<RbelContent> bodyLinesStream) {
    return bodyLinesStream.map(RbelContent::size).reduce(0, Integer::sum);
  }

  private static Stream<RbelContent> getMiddleLines(List<RbelContent> lines, int startLine) {
    return lines.stream().skip(startLine).limit(lines.size() - 1L - startLine);
  }

  public static RbelContent mergeLines(List<RbelContent> lines) {
    RbelContent content =
        RbelContent.builder()
            .content(lines.stream().map(RbelContent::toByteArray).toList())
            .build();
    // last CRLF needs to be cut off because it belongs to the CRLF_DOT_CRLF sequence
    return content.subArray(0, content.size() - CRLF_BYTES.length);
  }

  public static String duplicateDotsAtLineBegins(String input) {
    return Stream.of(input.split("\r\n", -1))
        .map(line -> line.startsWith(".") ? "." + line : line)
        .collect(Collectors.joining("\r\n"));
  }
}
