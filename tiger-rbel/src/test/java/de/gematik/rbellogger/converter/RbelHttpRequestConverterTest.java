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

package de.gematik.rbellogger.converter;

import static de.gematik.rbellogger.TestUtils.readAndConvertCurlMessage;
import static de.gematik.rbellogger.testutil.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelHttpMessageFacet;
import de.gematik.rbellogger.data.facet.RbelHttpRequestFacet;
import de.gematik.rbellogger.data.facet.RbelNoteFacet;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RbelHttpRequestConverterTest {

  private final RbelConverter rbelConverter = RbelLogger.build().getRbelConverter();

  @Test
  @DisplayName("should convert CURL request with defunct linebreaks")
  void shouldConvertCurlRequestWithDefunctLinebreaks() {
    final RbelElement rbelElement =
        new RbelElement(
            ("GET /auth/realms/idp/.well-known/openid-configuration HTTP/1.1\n"
                    + "Accept: */*\n"
                    + "Host: localhost:8080\n"
                    + "Connection: Keep-Alive\n"
                    + "User-Agent: Apache-HttpClient/4.5.12 (Java/11.0.8)\n"
                    + "Accept-Encoding: gzip,deflate\n\n")
                .getBytes(StandardCharsets.UTF_8),
            null);

    new RbelHttpRequestConverter().consumeElement(rbelElement, rbelConverter);

    assertThat(rbelElement)
        .hasFacet(RbelHttpRequestFacet.class)
        .hasFacet(RbelHttpMessageFacet.class);
  }

  @ParameterizedTest
  @CsvSource({
    "'User-Agent: Value1\r\nUser-Agent: Value2', 2", // 2 weil 2x User-Agent
    "'User-Agent: Value1, Value2\r\n', 3" // 3 weil 2x User-Agent und 1x User-Agent: Value1, Value2
  })
  void doubleHeaderValueRequest(String userAgentHeader, int expectedSize) {
    final RbelElement rbelElement =
        new RbelElement(
            ("GET /auth/realms/idp/.well-known/openid-configuration HTTP/1.1\r\n"
                    + userAgentHeader
                    + "\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8),
            null);

    rbelConverter.convertElement(rbelElement);

    assertThat(rbelElement).getChildrenWithPath("$.header.User-Agent..").hasSize(expectedSize);
    assertThat(rbelElement)
        .getChildrenWithPath("$.header..")
        .extracting(RbelElement::getRawStringContent)
        .contains("Value1", "Value2");
    assertThat(rbelElement)
        .matchesJexlExpression("$.header.User-Agent.. == 'Value1'")
        .matchesJexlExpression("$.header.User-Agent.. == 'Value2'");
  }

  @ParameterizedTest
  @CsvSource(
      textBlock =
          """
'User-Agent: Value1\r\nUser-Agent: Value2', 2
'User-Agent: Value1, Value2\r\n', 3
""")
  void doubleHeaderValueResponse(String userAgentHeader, int expectedSize) {
    final RbelElement rbelElement =
        new RbelElement(
            ("HTTP/1.1 200\r\n" + userAgentHeader + "\r\n\r\n").getBytes(StandardCharsets.UTF_8),
            null);

    rbelConverter.convertElement(rbelElement);

    assertThat(rbelElement).getChildrenWithPath("$.header.User-Agent..").hasSize(expectedSize);
    assertThat(rbelElement)
        .getChildrenWithPath("$.header..")
        .extracting(RbelElement::getRawStringContent)
        .contains("Value1", "Value2");
    assertThat(rbelElement)
        .matchesJexlExpression("$.header.User-Agent.. == 'Value1'")
        .matchesJexlExpression("$.header.User-Agent.. == 'Value2'");
  }

  @Test
  void defunctChunkedMessage_shouldParseIncorrectlyAndAddErrorNote() throws IOException {
    final RbelElement rbelElement =
        readAndConvertCurlMessage("src/test/resources/sampleMessages/illegalChunkedMessage.curl");

    assertThat(rbelElement)
        .hasFacet(RbelNoteFacet.class)
        .extractFacet(RbelNoteFacet.class)
        .matches(note -> note.getStyle() == RbelNoteFacet.NoteStyling.ERROR, "note is error")
        .matches(
            note -> note.getValue().contains("Exception during conversion"),
            "note value contains information about exception during conversion");
  }

  @Test
  void shouldNotConvertAcceptList() {
    final RbelElement rbelElement =
        new RbelElement(("GET,PUT,POST\"").getBytes(StandardCharsets.UTF_8), null);

    new RbelHttpRequestConverter().consumeElement(rbelElement, rbelConverter);

    assertThat(rbelElement).doesNotHaveFacet(RbelHttpRequestFacet.class);
  }

  @Test
  void httpVersionShouldBeParsed() {
    assertThat(
            rbelConverter.convertElement("HTTP/1.0 200\r\n" + "Some-Header: Value\r\n\r\n", null))
        .extractChildWithPath("$.httpVersion")
        .hasStringContentEqualTo("HTTP/1.0");

    assertThat(
            rbelConverter.convertElement("HTTP/1.1 200\r\n" + "Some-Header: Value\r\n\r\n", null))
        .extractChildWithPath("$.httpVersion")
        .hasStringContentEqualTo("HTTP/1.1");

    assertThat(
            rbelConverter.convertElement(
                "GET /foo/bar HTTP/1.0\r\n" + "Some-Header: Value\r\n\r\n", null))
        .extractChildWithPath("$.httpVersion")
        .hasStringContentEqualTo("HTTP/1.0");
  }
}
