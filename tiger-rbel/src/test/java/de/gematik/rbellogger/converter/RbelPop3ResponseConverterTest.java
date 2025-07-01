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

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.facets.pop3.RbelPop3ResponseFacet;
import de.gematik.rbellogger.testutil.RbelElementAssertion;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RbelPop3ResponseConverterTest extends AbstractResponseConverterTest {

  @Test
  void shouldConvertListHeader() {
    String request = "LIST\r\n";
    String status = "+OK";
    String count = "2";
    String size = "320";
    String header = count + " messages (" + size + " octets)";
    String response = status + " " + header + "\r\n1 100\r\n2 220\r\n.\r\n";

    convertToRbelElement("+OK greeting\r\n");
    RbelElement element = convertMessagePair(request, response);

    checkListOrStatResponse(element, status, header, count, size)
        .andTheInitialElement()
        .hasChildWithPath("$.pop3Body");
  }

  @Test
  void shouldConvertListHeaderForSingleListRequest() {
    String request = "LIST 1\r\n";
    String status = "+OK";
    String response = status + " " + "1 100\r\n";

    convertToRbelElement("+OK greeting\r\n");
    RbelElement element = convertMessagePair(request, response);

    RbelElementAssertion.assertThat(element)
        .extractChildWithPath("$.pop3Status")
        .hasStringContentEqualTo(status)
        .andTheInitialElement()
        .hasStringContentEqualToAtPosition("$.pop3Header", "1 100")
        .andTheInitialElement()
        .doesNotHaveChildWithPath("$.pop3Body");
  }

  @Test
  void shouldConvertListHeaderWithoutSize() {
    String request = "LIST\r\n";
    String status = "+OK";
    String count = "2";
    String header = count + " messages:";
    String response = status + " " + header + "\r\n1 100\r\n2 220\r\n.\r\n";

    convertToRbelElement("+OK greeting\r\n");
    RbelElement element = convertMessagePair(request, response);
    RbelElementAssertion.assertThat(element)
        .extractChildWithPath("$.pop3Status")
        .hasStringContentEqualTo(status)
        .andTheInitialElement()
        .extractChildWithPath("$.pop3Header")
        .hasStringContentEqualTo(header)
        .andTheInitialElement()
        .extractChildWithPath("$.pop3Header.count")
        .hasStringContentEqualTo(count)
        .andTheInitialElement()
        .doesNotHaveChildWithPath("$.pop3Header.size")
        .andTheInitialElement()
        .hasChildWithPath("$.pop3Body");
  }

  @Test
  void shouldConvertStatHeader() {
    String request = "STAT\r\n";
    String status = "+OK";
    String count = "2";
    String size = "320";
    String header = count + " " + size;
    String response = status + " " + header + "\r\n";
    convertToRbelElement("+OK greeting\r\n");
    RbelElement element = convertMessagePair(request, response);

    checkListOrStatResponse(element, status, header, count, size)
        .andTheInitialElement()
        .doesNotHaveChildWithPath("$.pop3Body");
  }

  private static RbelElementAssertion checkListOrStatResponse(
      RbelElement element, String status, String header, String count, String size) {
    return RbelElementAssertion.assertThat(element)
        .extractChildWithPath("$.pop3Status")
        .hasStringContentEqualTo(status)
        .andTheInitialElement()
        .extractChildWithPath("$.pop3Header")
        .hasStringContentEqualTo(header)
        .andTheInitialElement()
        .extractChildWithPath("$.pop3Header.count")
        .hasStringContentEqualTo(count)
        .andTheInitialElement()
        .extractChildWithPath("$.pop3Header.size")
        .hasStringContentEqualTo(size);
  }

  @ParameterizedTest
  @ValueSource(strings = {"LIST", "STAT"})
  void shouldRejectMalformedHeader(String command) {
    String request = command + "\r\n";
    String status = "+OK";
    String header = "foobar foobar";
    String response = status + " " + header + "\r\n";

    convertToRbelElement("+OK greeting\r\n");
    RbelElement element = convertMessagePair(request, response);

    RbelElementAssertion.assertThat(element).doesNotHaveFacet(RbelPop3ResponseFacet.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"CAPA", "RETR 1", "UIDL", "TOP 2 20"})
  void shouldAcceptMultilineWithoutHeader(String command) {
    String request = command + "\r\n";
    String status = "+OK";
    String body = "foobar foobar";
    String response = status + "\r\n" + body + "\r\n.\r\n";

    convertToRbelElement("+OK greeting\r\n");
    RbelElement element = convertMessagePair(request, response);
    RbelElementAssertion.assertThat(element)
        .extractChildWithPath("$.pop3Status")
        .hasStringContentEqualTo(status)
        .andTheInitialElement()
        .doesNotHaveChildWithPath("$.pop3Header")
        .andTheInitialElement()
        .extractChildWithPath("$.pop3Body")
        .hasStringContentEqualTo(body);
  }

  @ParameterizedTest
  @ValueSource(strings = {"CAPA", "RETR 1", "TOP 5 10"})
  void shouldAcceptMultilineWithEmptyHeader(String command) {
    String request = command + "\r\n";
    String status = "+OK";
    String body = "foobar foobar";
    String header = " ";
    String response = status + " " + header + "\r\n" + body + "\r\n.\r\n";
    convertToRbelElement("+OK greeting\r\n");
    RbelElement element = convertMessagePair(request, response);
    RbelElementAssertion.assertThat(element)
        .extractChildWithPath("$.pop3Status")
        .hasStringContentEqualTo(status)
        .andTheInitialElement()
        .doesNotHaveChildWithPath("$.pop3Header")
        .andTheInitialElement()
        .extractChildWithPath("$.pop3Body")
        .hasStringContentEqualTo(body);
  }

  @ParameterizedTest
  @ValueSource(strings = {"USER x@y.de", "PASS xzy"})
  void shouldAcceptSingleLineWithoutHeader(String command) {
    String request = command + "\r\n";
    String status = "+OK";
    String response = status + "\r\n";
    RbelElement element = convertMessagePair(request, response);
    RbelElementAssertion.assertThat(element)
        .extractChildWithPath("$.pop3Status")
        .hasStringContentEqualTo(status)
        .andTheInitialElement()
        .doesNotHaveChildWithPath("$.pop3Header")
        .andTheInitialElement()
        .doesNotHaveChildWithPath("$.pop3Body");
  }

  @ParameterizedTest
  @ValueSource(strings = {"CAPA", "RETR 1", "TOP 2 10"})
  void shouldRejectMissingBody(String command) {
    String request = command + "\r\n";
    String status = "+OK";
    String response = status + "\r\n";

    convertToRbelElement("+OK greeting\r\n");
    RbelElement element = convertMessagePair(request, response);

    RbelElementAssertion.assertThat(element).doesNotHaveFacet(RbelPop3ResponseFacet.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"+OK", "-ERR"})
  void shouldConvertSingleLinePop3OkResponse(String status) {
    String header = "foobar foobar";
    String input = status + " " + header + "\r\n";
    RbelElement element = convertToRbelElement(input);

    RbelElementAssertion.assertThat(element)
        .extractChildWithPath("$.pop3Status")
        .hasStringContentEqualTo(status)
        .andTheInitialElement()
        .extractChildWithPath("$.pop3Header")
        .hasStringContentEqualTo(header)
        .andTheInitialElement()
        .doesNotHaveChildWithPath("$.pop3Body");
  }

  @Test
  void shouldRejectSingleLineMessageNotEndingWithCrLf() {
    RbelElement element = convertToRbelElement("+OK foobar foobar");
    assertThat(element.hasFacet(RbelPop3ResponseFacet.class)).isFalse();
  }

  @Test
  void shouldConvertMultilinePop3OkResponse() {
    String status = "+OK";
    String header = "foobar foobar";
    String body =
        """
        .blablabla
        .bubl\r
        foobar\r
        .\r
        blabla
        """;
    String input = status + " " + header + "\r\n" + duplicateDotsAtLineBegins(body) + "\r\n.\r\n";

    convertToRbelElement("+OK greeting\r\n");
    convertToRbelElement("RETR 1\r\n");
    RbelElement element = convertToRbelElement(input);
    RbelElementAssertion.assertThat(element)
        .extractChildWithPath("$.pop3Status")
        .hasStringContentEqualTo(status)
        .andTheInitialElement()
        .extractChildWithPath("$.pop3Header")
        .hasStringContentEqualTo(header)
        .andTheInitialElement()
        .extractChildWithPath("$.pop3Body")
        .hasStringContentEqualTo(body);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "+OK foobar foobar\r", //      missing LF
        "-ERR foobar foobar", //       missing CRLF
        "-ERROR foobar foobar\r\n", // invalid status
        "+OK-STATUS foobar\r\n", //    invalid status
        "-ERR\r\n", //                 missing info
      })
  void shouldRejectMalformedPop3Response(String input) {
    RbelElement element = convertToRbelElement(input);
    assertThat(element.hasFacet(RbelPop3ResponseFacet.class)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "+OK Capability list follows\r\nUSER\r\nUIDL\r\nTOP\r\n.\r\n",
        "+OK\r\nUSER\r\nUIDL\r\nTOP\r\n.\r\n",
      })
  void afterCapaCommand_ResponseWithCrLfDotCrLf_isPop3Response(String capaResponse) {

    var request = "CAPA\r\n";
    var capaResponseElement = convertMessagePair(request, capaResponse);

    RbelElementAssertion.assertThat(capaResponseElement).hasFacet(RbelPop3ResponseFacet.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "+OK Capability list follows\r\n",
        "+OK\r\n",
      })
  void afterCapaCommand_ResponseWithoutCrlfCrlfIsRejected(String capaResponse) {

    convertToRbelElement("+OK greeting\r\n");
    var request = "CAPA\r\n";
    var capaResponseElement = convertMessagePair(request, capaResponse);

    RbelElementAssertion.assertThat(capaResponseElement)
        .doesNotHaveFacet(RbelPop3ResponseFacet.class);
  }

  private static String duplicateDotsAtLineBegins(String input) {
    return Stream.of(input.split("\r\n", -1))
        .map(line -> line.startsWith(".") ? "." + line : line)
        .collect(Collectors.joining("\r\n"));
  }
}
