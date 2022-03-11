/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.rbellogger.configuration.RbelFileSaveInfo;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerFileSaveInfo;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

class EpaVauParsingTest {

    @Test
    void shouldAddRecordIdFacetToAllHandshakeMessages() throws IOException {
        var tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .fileSaveInfo(TigerFileSaveInfo.builder()
                .sourceFile("src/test/resources/vauEpa2Flow.tgr")
                .build())
            .keyFolders(List.of("src/test/resources"))
            .activateVauAnalysis(true)
            .build());

        FileUtils.writeStringToFile(new File("target/vauFlow.html"),
            RbelHtmlRenderer.render(tigerProxy.getRbelLogger().getMessageHistory()), StandardCharsets.UTF_8);

        assertThat(tigerProxy.getRbelMessages().get(24).findElement("$.body.recordId"))
            .get()
            .extracting(RbelElement::getRawStringContent)
            .isEqualTo("X114428539");
        assertThat(tigerProxy.getRbelMessages().get(25).findElement("$.body.recordId"))
            .get()
            .extracting(RbelElement::getRawStringContent)
            .isEqualTo("X114428539");

        assertThat(tigerProxy.getRbelMessages().get(28).findElement("$.body.recordId"))
            .get()
            .extracting(RbelElement::getRawStringContent)
            .isEqualTo("X114428539");
        assertThat(tigerProxy.getRbelMessages().get(29).findElement("$.body.recordId"))
            .get()
            .extracting(RbelElement::getRawStringContent)
            .isEqualTo("X114428539");

        assertThat(tigerProxy.getRbelMessages().get(30).findElement("$.body.recordId"))
            .get()
            .extracting(RbelElement::getRawStringContent)
            .isEqualTo("X114428539");
        assertThat(tigerProxy.getRbelMessages().get(31).findElement("$.body.recordId"))
            .get()
            .extracting(RbelElement::getRawStringContent)
            .isEqualTo("X114428539");
    }

    @Test
    void verifyRiseTraffic() {
        var tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .fileSaveInfo(TigerFileSaveInfo.builder()
                .sourceFile("src/test/resources/rise-vau-log.tgr")
                .build())
            .activateVauAnalysis(true)
            .build());

        assertThat(tigerProxy.getRbelMessages().get(15).findElement("$.body.recordId"))
            .get()
            .extracting(RbelElement::getRawStringContent)
            .isEqualTo("Y243631459");
    }
}
