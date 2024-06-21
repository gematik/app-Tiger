/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.facet.RbelPop3ResponseFacet;
import de.gematik.rbellogger.testutil.RbelElementAssertion;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RbelPop3ResponseConverterTest {

  private RbelConverter converter;

  @BeforeEach
  void init() {
    converter = RbelLogger.build().getRbelConverter();
  }

  @Test
  void shouldConvertListHeader() {
    String request = "LIST\r\n";
    String status = "+OK";
    String count = "2";
    String size = "320";
    String header = count + " messages (" + size + " octets)";
    String response = status + " " + header + "\r\n";
    RbelElement element = convertMessagePair(request, response);

    checkListOrStatResponseWithoutBody(element, status, header, count, size);
  }

  @Test
  void shouldConvertStatHeader() {
    String request = "STAT\r\n";
    String status = "+OK";
    String count = "2";
    String size = "320";
    String header = count + " " + size;
    String response = status + " " + header + "\r\n";
    RbelElement element = convertMessagePair(request, response);

    checkListOrStatResponseWithoutBody(element, status, header, count, size);
  }

  private static RbelElementAssertion checkListOrStatResponseWithoutBody(
      RbelElement element, String status, String header, String count, String size) {
    return RbelElementAssertion.assertThat(element)
        .extractChildWithPath("$.status")
        .hasStringContentEqualTo(status)
        .andTheInitialElement()
        .extractChildWithPath("$.header")
        .hasStringContentEqualTo(header)
        .andTheInitialElement()
        .extractChildWithPath("$.header.count")
        .hasStringContentEqualTo(count)
        .andTheInitialElement()
        .extractChildWithPath("$.header.size")
        .hasStringContentEqualTo(size)
        .andTheInitialElement()
        .doesNotHaveChildWithPath("$.body");
  }

  @ParameterizedTest
  @ValueSource(strings = {"LIST", "STAT"})
  void shouldRejectMalformedHeader(String command) {
    String request = command + "\r\n";
    String status = "+OK";
    String header = "foobar foobar";
    String response = status + " " + header + "\r\n";
    RbelElement element = convertMessagePair(request, response);

    RbelElementAssertion.assertThat(element).doesNotHaveFacet(RbelPop3ResponseFacet.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"CAPA", "RETR 1"})
  void shouldAcceptMultilineWithoutHeader(String command) {
    String request = command + "\r\n";
    String status = "+OK";
    String body = "foobar foobar";
    String response = status + "\r\n" + body + "\r\n.\r\n";
    RbelElement element = convertMessagePair(request, response);
    RbelElementAssertion.assertThat(element)
        .extractChildWithPath("$.status")
        .hasStringContentEqualTo(status)
        .andTheInitialElement()
        .doesNotHaveChildWithPath("$.header")
        .andTheInitialElement()
        .extractChildWithPath("$.body")
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
        .extractChildWithPath("$.status")
        .hasStringContentEqualTo(status)
        .andTheInitialElement()
        .doesNotHaveChildWithPath("$.header")
        .andTheInitialElement()
        .doesNotHaveChildWithPath("$.body");
  }

  @ParameterizedTest
  @ValueSource(strings = {"USER x@y.de", "PASS xzy"})
  void shouldRejectMultiline(String command) {
    String request = command + "\r\n";
    String status = "+OK";
    String body = "foobar foobar";
    String response = status + "\r\n" + body + "\r\n.\r\n";
    RbelElement element = convertMessagePair(request, response);

    RbelElementAssertion.assertThat(element).doesNotHaveFacet(RbelPop3ResponseFacet.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"CAPA", "RETR 1"})
  void shouldRejectMissingBody(String command) {
    String request = command + "\r\n";
    String status = "+OK";
    String response = status + "\r\n";
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
        .extractChildWithPath("$.status")
        .hasStringContentEqualTo(status)
        .andTheInitialElement()
        .extractChildWithPath("$.header")
        .hasStringContentEqualTo(header)
        .andTheInitialElement()
        .doesNotHaveChildWithPath("$.body");
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

    RbelElement element = convertToRbelElement(input);
    RbelElementAssertion.assertThat(element)
        .extractChildWithPath("$.status")
        .hasStringContentEqualTo(status)
        .andTheInitialElement()
        .extractChildWithPath("$.header")
        .hasStringContentEqualTo(header)
        .andTheInitialElement()
        .extractChildWithPath("$.body")
        .hasStringContentEqualTo(body);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "+OK foobar foobar\r\nfoobar foobar\r\n", // missing dot trailer
        "-ERR foobar foobar", //                     missing CRLF
        "-ERROR foobar foobar\r\n", //               invalid status
        "+OK-STATUS foobar\r\n", //                  invalid status
        "+OK\r\n", //                                missing info
        "-ERR\r\n", //                               missing info
        "-OK foobar\r\n.\r\nbarfoo\r\n" //           trailing content
      })
  void shouldRejectMalformedPop3Response(String input) {
    RbelElement element = convertToRbelElement(input);
    assertThat(element.hasFacet(RbelPop3ResponseFacet.class)).isFalse();
  }

  private RbelElement convertMessagePair(String request, String response) {
    var sender = new RbelHostname("host1", 1);
    var receiver = new RbelHostname("host2", 2);
    convertToRbelElement(request, sender, receiver);
    return convertToRbelElement(response, receiver, sender);
  }

  private RbelElement convertToRbelElement(String request) {
    var sender = new RbelHostname("host1", 1);
    var receiver = new RbelHostname("host2", 2);
    return convertToRbelElement(request, sender, receiver);
  }

  private RbelElement convertToRbelElement(String input, RbelHostname sender, RbelHostname recipient) {
    return converter.parseMessage(
        input.getBytes(StandardCharsets.UTF_8), sender, recipient, Optional.empty());
  }

  private static String duplicateDotsAtLineBegins(String input) {
    return Stream.of(input.split("\r\n", -1))
        .map(line -> line.startsWith(".") ? "." + line : line)
        .collect(Collectors.joining("\r\n"));
  }
}
