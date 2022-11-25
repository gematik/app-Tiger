/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerFileSaveInfo;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.proxy.data.TracingMessagePairFacet;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@Slf4j
@TestInstance(Lifecycle.PER_CLASS)
class TestTigerProxyFile extends AbstractTigerProxyTest {

    @Test
    void saveToFileAndReadAgain_pairsShouldBeReconstructed() throws IOException {
        final String TGR_FILENAME = "target/reconstruction.tgr";
        FileUtils.deleteQuietly(new File(TGR_FILENAME));
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .fileSaveInfo(TigerFileSaveInfo.builder()
                .writeToFile(true)
                .clearFileOnBoot(true)
                .filename(TGR_FILENAME)
                .build())
            .build());

        proxyRest.get("http://backend/foobar").asJson();
        proxyRest.get("http://backend/faabor").asJson();
        fileHasNLines(TGR_FILENAME, 4);

        try (final TigerProxy otherProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .fileSaveInfo(TigerFileSaveInfo.builder()
                .sourceFile(TGR_FILENAME)
                .build())
            .build())) {
            await().atMost(2, TimeUnit.SECONDS)
                    .until(() -> otherProxy.getRbelMessages().size() >= 4);
            assertThat(otherProxy.getRbelLogger().getMessageHistory().getLast().getFacetOrFail(TracingMessagePairFacet.class)
                .getRequest().findElement("$.path").get().getRawStringContent())
                .isEqualTo("/faabor");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void fileHasNLines(String filename, int lines) {
        await().atMost(2, TimeUnit.SECONDS)
            .pollInterval(100, TimeUnit.MILLISECONDS)
            .until(() -> FileUtils.readLines(new File(filename), StandardCharsets.UTF_8)
                .size() >= lines);
    }
}
