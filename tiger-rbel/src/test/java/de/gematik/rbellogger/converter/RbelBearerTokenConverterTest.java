/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static de.gematik.rbellogger.TestUtils.readCurlFromFileWithCorrectedLineBreaks;
import static org.assertj.core.api.Assertions.assertThat;

class RbelBearerTokenConverterTest {

    @Test
    void shouldFindJwtInBearerHeaderAttributer() throws IOException {
        final String curlMessage = readCurlFromFileWithCorrectedLineBreaks
            ("src/test/resources/sampleMessages/bearerToken.curl");

        final RbelLogger logger = RbelLogger.build();
        final RbelElement convertedMessage = logger.getRbelConverter()
            .parseMessage(curlMessage.getBytes(StandardCharsets.UTF_8), null, null, Optional.of(ZonedDateTime.now()));

        assertThat(convertedMessage.findRbelPathMembers("$.header.Authorization.BearerToken"))
            .isNotEmpty();
    }

    @Test
    void shouldRenderBearerToken() throws IOException {
        final String curlMessage = readCurlFromFileWithCorrectedLineBreaks
            ("src/test/resources/sampleMessages/bearerToken.curl");

        final RbelLogger logger = RbelLogger.build();
        logger.getRbelConverter().parseMessage(curlMessage.getBytes(StandardCharsets.UTF_8), null, null, Optional.of(ZonedDateTime.now()));

        final String renderingOutput = RbelHtmlRenderer.render(logger.getMessageHistory());
        assertThat(renderingOutput)
            .contains("Carvalho")
            .contains("https://idp.zentral.idp.splitdns.ti-dienste.de");
        FileUtils.writeStringToFile(new File("target/bearerToken.html"), renderingOutput);
    }
}
