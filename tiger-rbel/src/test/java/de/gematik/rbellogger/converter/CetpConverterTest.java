/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter;

import static org.assertj.core.api.Assertions.assertThat;
import com.google.common.io.Files;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelCetpFacet;
import de.gematik.rbellogger.data.facet.RbelXmlFacet;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CetpConverterTest {

    private static RbelElement convertedMessage;
    private static final RbelConverter rbelConverter = RbelLogger.build().getRbelConverter();

    @SneakyThrows
    @BeforeAll
    public static void parseMessage() {
        byte[] cetpMessageAsBytes = Files.toByteArray(new File("src/test/resources/sampleMessages/cetp_ti_down.bin"));

        convertedMessage = rbelConverter
            .parseMessage(cetpMessageAsBytes, null, null, Optional.empty());
    }

    @Test
    void convertMessage_shouldGiveCetpFacet() {
        assertThat(convertedMessage.hasFacet(RbelCetpFacet.class))
            .isTrue();
        assertThat(convertedMessage.findElement("$.body")
            .get().hasFacet(RbelXmlFacet.class))
            .isTrue();
    }

    @Test
    void messageLengthShouldBeWrappedCorrectly() throws IOException {
        assertThat(convertedMessage.findElement("$.messageLength").get()
            .seekValue(Integer.class))
            .get()
            .isEqualTo(286);
        assertThat(convertedMessage.findElement("$.messageLength").get().getRawContent())
            .isEqualTo(new byte[] {0, 0, 1, 30});
        assertThat(convertedMessage.getFacetOrFail(RbelCetpFacet.class).getMessageLength().seekValue(Integer.class).get().intValue())
            .isEqualTo(286);
    }

    @Test
    void checkRendering() throws IOException {
        FileUtils.writeStringToFile(new File("target/cetpMessage.html"),
            RbelHtmlRenderer.render(rbelConverter.getMessageHistory()), StandardCharsets.UTF_8);
    }

    @SneakyThrows
    @Test
    void shouldRenderCleanHtmlCetp2() {
        assertThat(RbelHtmlRenderer.render(List.of(convertedMessage)))
            .isNotBlank();
    }
}
