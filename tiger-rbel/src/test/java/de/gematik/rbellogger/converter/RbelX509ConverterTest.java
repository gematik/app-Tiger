/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter;

import static de.gematik.rbellogger.TestUtils.readCurlFromFileWithCorrectedLineBreaks;
import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.RbelOptions;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import java.io.File;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RbelX509ConverterTest {

    private RbelElement xmlMessage;

    @BeforeEach
    public void setUp() throws IOException {
        RbelOptions.activateRbelPathDebugging();
        xmlMessage = RbelLogger.build().getRbelConverter()
            .parseMessage(readCurlFromFileWithCorrectedLineBreaks
                    ("src/test/resources/sampleMessages/xmlMessage.curl").getBytes(), null, null,
                Optional.of(ZonedDateTime.now()));
    }

    @SneakyThrows
    @Test
    public void shouldRenderCleanHtml() {
        FileUtils.writeStringToFile(new File("target/x509Message.html"),
            RbelHtmlRenderer.render(List.of(xmlMessage)));
    }

    @SneakyThrows
    @Test
    public void shouldBeAccessibleViaRbelPath() {
        final RbelElement certificateElement = xmlMessage.findElement("$..[?(@.subject=~'.*TEST-ONLY.*')]").get();

        assertThat(certificateElement)
            .isEqualTo(xmlMessage.findElement(
                "$.body.RegistryResponse.RegistryErrorList.RegistryError.jwtTag.text.header.x5c.0.content").get());
    }
}
