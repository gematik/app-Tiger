/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter;

import static de.gematik.rbellogger.TestUtils.readCurlFromFileWithCorrectedLineBreaks;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelHttpHeaderFacet;
import de.gematik.rbellogger.data.facet.RbelHttpMessageFacet;
import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class MessageConverterTest {

  @Test
  public void convertMessage_shouldGiveCorrectType() throws IOException {
    final String curlMessage =
        readCurlFromFileWithCorrectedLineBreaks(
            "src/test/resources/sampleMessages/jsonMessage.curl");

    final RbelElement convertedMessage =
        RbelLogger.build().getRbelConverter().convertElement(curlMessage, null);

    assertThat(convertedMessage.getFacet(RbelHttpResponseFacet.class)).isPresent();
  }

  @Test
  public void noReasonPhrase_shouldGiveEmptyOptional() throws IOException {
    final String curlMessage =
        readCurlFromFileWithCorrectedLineBreaks(
            "src/test/resources/sampleMessages/jsonMessage.curl");

    final RbelElement convertedMessage =
        RbelLogger.build().getRbelConverter().convertElement(curlMessage, null);

    assertThat(
            convertedMessage
                .getFacet(RbelHttpResponseFacet.class)
                .get()
                .getReasonPhrase()
                .getRawStringContent())
        .isEqualTo(null);
  }

  @Test
  public void convertMessage_shouldGiveHeaderFields() throws IOException {
    final String curlMessage =
        readCurlFromFileWithCorrectedLineBreaks(
            "src/test/resources/sampleMessages/jsonMessage.curl");

    final RbelElement convertedMessage =
        RbelLogger.build().getRbelConverter().convertElement(curlMessage, null);

    final Map<String, RbelElement> elementMap =
        convertedMessage
            .getFacetOrFail(RbelHttpMessageFacet.class)
            .getHeader()
            .getFacetOrFail(RbelHttpHeaderFacet.class);
    assertThat(elementMap).hasSize(3);
    assertThat(elementMap.get("Content-Type").getRawStringContent())
        .isEqualTo("application/json; charset=latin1");
  }
}
