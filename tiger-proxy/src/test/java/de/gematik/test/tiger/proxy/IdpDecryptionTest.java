/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import de.gematik.rbellogger.captures.RbelFileReaderCapturer;
import de.gematik.rbellogger.key.RbelKeyManager;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

@ResetTigerConfiguration
class IdpDecryptionTest {

    @Test
    void shouldAddRecordIdFacetToAllHandshakeMessages() throws Exception {
        try (var tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .keyFolders(List.of("src/test/resources"))
            .activateEpaVauAnalysis(true)
            .build())) {

            tigerProxy.getRbelLogger().getRbelConverter().addPostConversionListener(RbelKeyManager.RBEL_IDP_TOKEN_KEY_LISTENER);
            final RbelFileReaderCapturer fileReaderCapturer = RbelFileReaderCapturer.builder()
                .rbelFile("src/test/resources/idpDecryption.tgr")
                .rbelConverter(tigerProxy.getRbelLogger().getRbelConverter())
                .build();
            fileReaderCapturer.initialize();
            fileReaderCapturer.close();

            await()
                .atMost(20, TimeUnit.SECONDS)
                .until(() -> tigerProxy.getRbelMessagesList().size() >= 22);

            final String htmlData = RbelHtmlRenderer.render(tigerProxy.getRbelLogger().getMessageHistory());
            FileUtils.writeStringToFile(new File("target/idpFlow.html"), htmlData, StandardCharsets.UTF_8);

            assertThat(tigerProxy.getRbelMessagesList().get(21)
                .findElement("$.body.access_token.content.encryptionInfo.decryptedUsingKeyWithId")
                .get().seekValue(String.class))
                .get()
                .isEqualTo("token_key");
        }
    }
}
