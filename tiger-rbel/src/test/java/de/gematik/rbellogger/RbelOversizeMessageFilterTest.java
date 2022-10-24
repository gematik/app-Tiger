/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger;

import de.gematik.rbellogger.captures.RbelFileReaderCapturer;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Optional;

import static de.gematik.rbellogger.TestUtils.readCurlFromFileWithCorrectedLineBreaks;
import static org.assertj.core.api.Assertions.assertThat;

class RbelOversizeMessageFilterTest {

    private static RbelLogger rbelLogger;


    @BeforeAll
    static void initializeRbelLogger() throws IOException {
        final String oversizedRequest = readCurlFromFileWithCorrectedLineBreaks
            ("src/test/resources/sampleMessages/getRequest.curl")
            + "{\"foo\":\""+ RandomStringUtils.randomAlphabetic(50_000_000) +"\"}\r\n";
        rbelLogger = RbelLogger.build();
        rbelLogger.getRbelConverter()
            .parseMessage(oversizedRequest.getBytes(), null, null, Optional.empty());
    }

    @Test
    void oversizedMessageShouldNotBeParsed() {
        assertThat(rbelLogger.getMessageHistory().get(0).getFirst("body").get().getFacets())
            .isEmpty();
    }

    @Test
    void oversizedMessageShouldNotBeRendered() throws Exception {
        final String html = RbelHtmlRenderer.render(rbelLogger.getMessageHistory());

        FileUtils.writeStringToFile(new File("target/large.html"), html, StandardCharsets.UTF_8);

        assertThat(html)
            .hasSizeLessThan(1024 * 1024);
    }
}
