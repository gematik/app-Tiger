/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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
import de.gematik.rbellogger.data.facet.RbelNoteFacet.NoteStyling;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
                    + "Accept-Encoding: gzip,deflate\n")
                .getBytes(StandardCharsets.UTF_8),
            null);

    new RbelHttpRequestConverter().consumeElement(rbelElement, rbelConverter);

    assertThat(rbelElement)
        .hasFacet(RbelHttpRequestFacet.class)
        .hasFacet(RbelHttpMessageFacet.class);
  }

  @Test
  void doubleHeaderValueRequest() {
    final RbelElement rbelElement =
        new RbelElement(
            ("GET /auth/realms/idp/.well-known/openid-configuration HTTP/1.1\r\n"
                    + "User-Agent: Value1\r\n"
                    + "User-Agent: Value2\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8),
            null);

    rbelConverter.convertElement(rbelElement);

    assertThat(rbelElement.findRbelPathMembers("$.header.User-Agent")).hasSize(2);
    assertThat(rbelElement.findRbelPathMembers("$.header.*"))
        .extracting(RbelElement::getRawStringContent)
        .containsExactly("Value1", "Value2");
  }

  @Test
  void doubleHeaderValueResponse() {
    final RbelElement rbelElement =
        new RbelElement(
            ("HTTP/1.1 200\r\n" + "User-Agent: Value1\r\n" + "User-Agent: Value2\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8),
            null);

    rbelConverter.convertElement(rbelElement);

    assertThat(rbelElement.findRbelPathMembers("$.header.User-Agent")).hasSize(2);
    assertThat(rbelElement.findRbelPathMembers("$.header.*"))
        .extracting(RbelElement::getRawStringContent)
        .containsExactly("Value1", "Value2");
  }

  @Test
  void defunctChunkedMessage_shouldParseCorrectlyAndAddNote() throws IOException {
    final RbelElement rbelElement =
        readAndConvertCurlMessage("src/test/resources/sampleMessages/illegalChunkedMessage.curl");

    assertThat(rbelElement)
        .hasFacet(RbelNoteFacet.class)
        .extractFacet(RbelNoteFacet.class)
        .matches(note -> note.getStyle() == NoteStyling.WARN)
        .matches(note -> note.getValue().contains("chunked"));
  }

  @Test
  void shouldNotConvertAcceptList() {
    final RbelElement rbelElement =
        new RbelElement(("GET,PUT,POST\"").getBytes(StandardCharsets.UTF_8), null);

    new RbelHttpRequestConverter().consumeElement(rbelElement, rbelConverter);

    assertThat(rbelElement).doesNotHaveFacet(RbelHttpRequestFacet.class);
  }
}
