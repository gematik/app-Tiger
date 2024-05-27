/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelPop3ResponseFacet;
import de.gematik.rbellogger.testutil.RbelElementAssertion;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RbelPop3ResponseConverterTest {

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

  private static RbelElement convertToRbelElement(String input) {
    return RbelLogger.build().getRbelConverter().convertElement(input, null);
  }

  private static String duplicateDotsAtLineBegins(String input) {
    return Stream.of(input.split("\r\n", -1))
        .map(line -> line.startsWith(".") ? "." + line : line)
        .collect(Collectors.joining("\r\n"));
  }
}
