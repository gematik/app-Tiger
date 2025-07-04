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
package de.gematik.rbellogger.converter;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelConverter;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.testutil.RbelElementAssertion;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RbelSmtpResponseConverterTest {

  private RbelConverter converter;

  @BeforeEach
  void init() {
    converter =
        RbelLogger.build(new RbelConfiguration().activateConversionFor("smtp")).getRbelConverter();
  }

  @Test
  void shouldConvertSingleLineSmtpResponse() {
    String status = "200";
    String body = "Everything is fine";
    String input = status + " " + body + "\r\n";
    RbelElement element = convertToRbelElement(input);

    RbelElementAssertion.assertThat(element)
        .extractChildWithPath("$.status")
        .hasStringContentEqualTo(status)
        .andTheInitialElement()
        .extractChildWithPath("$.body")
        .hasStringContentEqualTo(body);
  }

  @Test
  void shouldConvertSingleLineSmtpResponseWithoutBody() {
    String status = "200";
    String input = status + "\r\n";
    RbelElement element = convertToRbelElement(input);

    RbelElementAssertion.assertThat(element)
        .extractChildWithPath("$.status")
        .hasStringContentEqualTo(status)
        .andTheInitialElement()
        .doesNotHaveChildWithPath("$.body");
  }

  @Test
  void shouldConvertMultilineSmtpResponse() {
    String body = """
        line1\r
        line2\r
        line3\r
        """;
    var lines = body.split("\r\n");
    String status = "200";
    String response = generateResponse(status, lines);

    RbelElement element = convertToRbelElement(response);

    RbelElementAssertion.assertThat(element)
        .extractChildWithPath("$.status")
        .hasStringContentEqualTo(status)
        .andTheInitialElement()
        .extractChildWithPath("$.body")
        .hasStringContentEqualTo(body);
  }

  private static String generateResponse(String status, String[] lines) {
    StringBuilder response = new StringBuilder();
    for (int i = 0; i < lines.length; i++) {
      response.append(status);
      if (i < lines.length - 1) {
        response.append("-");
      } else {
        response.append(" ");
      }
      response.append(lines[i]);
      response.append("\r\n");
    }
    return response.toString();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "200 foobar",
        "200 \r\n",
        "200-foobar\r\n",
        "20\r\n",
        "2000\r\n",
        "20 foobar\r\n",
        "2000 foobar\r\n",
        "200-foobar\r\n201 barfoo\r\n",
        "200-foobar\r\n200\r\n"
      })
  void shouldRejectMalformedResponse(String input) {
    RbelElement element = convertToRbelElement(input);

    RbelElementAssertion.assertThat(element).doesNotHaveChildWithPath("$.status");
  }

  @ParameterizedTest
  @ValueSource(strings = {"foobar\r\n", "barfoo\r\nfoobar\r\n"})
  void shouldRenderSmtpResponses(String body) throws IOException {
    String status = "200";
    var lines = body.split("\r\n");
    String response = generateResponse(status, lines);
    RbelElement convertedMessage = convertToRbelElement(response);

    String convertedHtml = RbelHtmlRenderer.render(List.of(convertedMessage));
    FileUtils.writeStringToFile(
        new File("target/directHtml.html"), convertedHtml, StandardCharsets.UTF_8);

    assertThat(convertedHtml)
        .contains("SMTP Response")
        .contains("Status: </b>" + status)
        .contains("Body: </b>");
    Arrays.stream(lines).forEach(line -> assertThat(convertedHtml).contains(line));
  }

  private RbelElement convertToRbelElement(String request) {
    var sender = new RbelHostname("host1", 1);
    var receiver = new RbelHostname("host2", 2);
    return convertToRbelElement(request, sender, receiver);
  }

  private RbelElement convertToRbelElement(
      String input, RbelHostname sender, RbelHostname recipient) {
    return converter.parseMessage(
        input.getBytes(StandardCharsets.UTF_8),
        new RbelMessageMetadata().withSender(sender).withReceiver(recipient));
  }
}
