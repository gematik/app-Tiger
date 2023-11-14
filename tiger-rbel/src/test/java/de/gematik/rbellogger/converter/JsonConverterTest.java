/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter;

import static de.gematik.rbellogger.TestUtils.readCurlFromFileWithCorrectedLineBreaks;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.facet.RbelJsonFacet;
import de.gematik.rbellogger.data.facet.RbelJwtFacet;
import de.gematik.rbellogger.data.facet.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JsonConverterTest {

  @Test
  void convertMessage_shouldGiveJsonBody() throws IOException {
    final String curlMessage =
        readCurlFromFileWithCorrectedLineBreaks(
            "src/test/resources/sampleMessages/jsonMessage.curl");

    final RbelElement convertedMessage =
        RbelLogger.build().getRbelConverter().convertElement(curlMessage, null);

    assertThat(convertedMessage.getFirst("body").get().hasFacet(RbelJsonFacet.class)).isTrue();
  }

  @Test
  void shouldRenderCleanHtml() throws IOException {
    final RbelElement convertedMessage =
        RbelLogger.build()
            .getRbelConverter()
            .convertElement(
                readCurlFromFileWithCorrectedLineBreaks(
                    "src/test/resources/sampleMessages/idpEncMessage.curl"),
                null);
    convertedMessage.addFacet(
        RbelTcpIpMessageFacet.builder()
            .receiver(RbelElement.wrap(null, convertedMessage, new RbelHostname("recipient", 1)))
            .sender(RbelElement.wrap(null, convertedMessage, new RbelHostname("sender", 1)))
            .build());
    assertThat(RbelHtmlRenderer.render(List.of(convertedMessage))).isNotBlank();
  }

  @Test
  void jsonMessageWithNestedJwt_shouldFindAndPresentNestedItems() throws IOException {
    final String curlMessage =
        readCurlFromFileWithCorrectedLineBreaks(
            "src/test/resources/sampleMessages/getChallenge.curl");

    final RbelElement convertedMessage =
        RbelLogger.build()
            .getRbelConverter()
            .parseMessage(curlMessage.getBytes(), null, null, Optional.of(ZonedDateTime.now()));

    assertThat(RbelHtmlRenderer.render(List.of(convertedMessage))).isNotBlank();

    assertThat(
            convertedMessage.getFirst("body").get().traverseAndReturnNestedMembers().stream()
                .filter(el -> el.hasFacet(RbelJwtFacet.class))
                .findAny())
        .isPresent();
  }
}
