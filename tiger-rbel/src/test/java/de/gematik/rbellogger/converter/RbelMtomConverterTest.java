/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.converter.initializers.RbelKeyFolderInitializer;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class RbelMtomConverterTest {

    private RbelLogger rbelLogger;

    @BeforeEach
    public void init() throws IOException {
        rbelLogger = RbelLogger.build(new RbelConfiguration()
                .setActivateAsn1Parsing(false)
                .addInitializer(new RbelKeyFolderInitializer("src/test/resources")));

        String rawSavedVauMessages = FileUtils.readFileToString(new File("src/test/resources/vauEpa2Flow.rawHttpDump"));
        Stream.of(rawSavedVauMessages.split("\n\n"))
                .map(Base64.getDecoder()::decode)
                .forEach(msgBytes -> rbelLogger.getRbelConverter().parseMessage(msgBytes, null, null, Optional.of(ZonedDateTime.now())));
    }

    @Test
    public void shouldRenderCleanHtml() throws IOException {
        FileUtils.writeStringToFile(new File("target/vauEpa2.html"),
                RbelHtmlRenderer.render(rbelLogger.getMessageHistory()));
    }

    @Test
    @DisplayName("MTOM XML - should be parsed correctly")
    public void mtomXml_shouldBeParsedCorrectly() {
        assertThat(rbelLogger.getMessageHistory().get(34)
                .findRbelPathMembers("$..Envelope.soap").get(0)
                .getRawStringContent())
                .isEqualTo("http://www.w3.org/2003/05/soap-envelope");

        assertThat(rbelLogger.getMessageHistory().get(34)
            .findRbelPathMembers("$..EncryptionMethod.Algorithm")
            .stream()
            .map(RbelElement::getRawStringContent)
            .collect(Collectors.toList()))
            .containsExactlyInAnyOrder("http://www.w3.org/2009/xmlenc11#aes256-gcm", "http://www.w3.org/2009/xmlenc11#aes256-gcm");
    }
}
