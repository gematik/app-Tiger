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
import static de.gematik.test.tiger.common.config.TigerConfigurationKeys.LENIENT_HTTP_PARSING;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelHttpMessageFacet;
import de.gematik.rbellogger.data.facet.RbelHttpRequestFacet;
import de.gematik.rbellogger.data.facet.RbelNoteFacet;
import de.gematik.rbellogger.data.facet.RbelNoteFacet.NoteStyling;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RbelHttpRequestConverterTest {

  private final RbelConverter lenientRbelConverter;
  private final RbelConverter rbelConverter = RbelLogger.build().getRbelConverter();

  public RbelHttpRequestConverterTest() {
    LENIENT_HTTP_PARSING.putValue(true);
    lenientRbelConverter = RbelLogger.build().getRbelConverter();
    LENIENT_HTTP_PARSING.clearValue();
  }

  @ParameterizedTest
  @CsvSource({
    "'User-Agent: Value1\r\nUser-Agent: Value2', 2", // 2 weil 2x User-Agent
    "'User-Agent: Value1, Value2', 3" // 3 weil 2x User-Agent und 1x User-Agent: Value1, Value2
  })
  void doubleHeaderValueRequest(String userAgentHeader, int expectedSize) {
    final RbelElement rbelElement =
        new RbelElement(
            ("GET /auth/realms/idp/.well-known/openid-configuration HTTP/1.1\r\n"
                    + "Host: localhost\r\n"
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
'User-Agent: Value1, Value2', 3
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

    new RbelHttpRequestConverter(new RbelConfiguration())
        .consumeElement(rbelElement, rbelConverter);

    assertThat(rbelElement).doesNotHaveFacet(RbelHttpRequestFacet.class);
  }

  @ParameterizedTest
  @CsvSource({
    "'HTTP/1.0 200\r\nSome-Header: Value\r\n\r\n', 'HTTP/1.0'",
    "'HTTP/1.1 200\r\nSome-Header: Value\r\n\r\n', 'HTTP/1.1'",
    "'GET /foo/bar HTTP/1.0\r\nSome-Header: Value\r\n\r\n', 'HTTP/1.0'",
  })
  void httpVersionShouldBeParsed(String message, String httpVersion) {
    assertThat(rbelConverter.convertElement(message, null))
        .extractChildWithPath("$.httpVersion")
        .hasStringContentEqualTo(httpVersion);
  }

  @ParameterizedTest
  @CsvSource({
    "'DELETE /foo/bar HTTP/1.1\n"
        + "Host: localhost:8080\n"
        + "Connection: Keep-Alive\n\n"
        + "', Non-standard line endings detected. Expected CRLF, but found",
    "'HTTP/1.1 408\n"
        + "Host: localhost:8080\n"
        + "Connection: Keep-Alive\n\n"
        + "', Non-standard line endings detected. Expected CRLF, but found"
  })
  // Non CRLF-Line endings are not allowed in HTTP messages, but should be accepted
  // (https://www.rfc-editor.org/rfc/rfc2616#section-19.3)
  void testDefectLineBreaks(String defunctMessage, String errorMessageContains) {
    assertThat(
            lenientRbelConverter.parseMessage(
                defunctMessage.getBytes(), null, null, Optional.empty()))
        .hasFacet(RbelHttpMessageFacet.class);
    assertThat(rbelConverter.parseMessage(defunctMessage.getBytes(), null, null, Optional.empty()))
        .hasFacet(RbelHttpMessageFacet.class);
    assertThat(rbelConverter.parseMessage(defunctMessage.getBytes(), null, null, Optional.empty()))
        .extractFacet(RbelNoteFacet.class)
        .hasFieldOrPropertyWithValue("style", NoteStyling.INFO)
        .extracting("value")
        .asString()
        .contains(errorMessageContains);
  }

  @ParameterizedTest
  @CsvSource({
    "'GET /foo/bar HTTP/1.0\r\n"
        + "Some-Header: Value', 'No body found in HTTP message (Does the message contain correct"
        + " line breaks?)'",
    "'HTTP/1.1 402\r\n"
        + "Some-Header: Value', 'Unable to determine end of HTTP header. Does the header end with"
        + " double CRLF?'",
    "'OPTIONS /foo/bar HTTP/1.0\r\n"
        + "Some-Header: Value\r\n"
        + "', 'No body found in HTTP message (Does the message contain correct line breaks?)'",
    "'HTTP/1.1 404 Not Found\r\n"
        + "Some-Header: Value\r\n"
        + "', 'Unable to determine end of HTTP header. Does the header end with double CRLF?'"
  })
  void testBasicHttpErrors(String defunctMessage, String errorMessageContains) {
    assertThat(lenientRbelConverter.convertElement(defunctMessage, null))
        .hasFacet(RbelHttpMessageFacet.class);
    assertThat(rbelConverter.convertElement(defunctMessage, null))
        .hasFacet(RbelHttpMessageFacet.class);
    val convertedMessage =
        rbelConverter.parseMessage(defunctMessage.getBytes(), null, null, Optional.empty());
    assertThat(convertedMessage.getNotes())
        .extracting("value")
        .asString()
        .contains(errorMessageContains);
  }

  @ParameterizedTest
  @CsvSource({
    "'PUT /foo/bar HTTP/1.0\r\n"
        + "Some-Header: Value\r\n\r\n"
        + "Some body, but no content-length defined', Did not find content-length or"
        + " transfer-encoding header",
    "'HTTP/1.1 406\r\n"
        + "Some-Header: Value\r\n\r\n"
        + "Some body, but no content-length defined', Did not find content-length or"
        + " transfer-encoding header",
    "'PATCH /foo/bar HTTP/1.1\r\n"
        + "Connection: Keep-Alive\r\n\r\n"
        + "', HTTP/1.1 request does not contain Host header"
  })
  // These errors only stop the parsing if the message is send via TCP (otherwise the message can
  // still be safely parsed)
  void testTcpHttpErrors(String defunctMessage, String errorMessageContains) {
    assertThat(lenientRbelConverter.convertElement(defunctMessage, null))
        .hasFacet(RbelHttpMessageFacet.class);
    assertThat(rbelConverter.convertElement(defunctMessage, null))
        .extractFacet(RbelNoteFacet.class)
        .hasFieldOrPropertyWithValue("style", NoteStyling.INFO)
        .extracting("value")
        .asString()
        .contains(errorMessageContains);
  }
}
