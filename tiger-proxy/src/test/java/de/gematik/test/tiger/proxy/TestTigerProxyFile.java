/*
 * Copyright (c) 2023 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerFileSaveInfo;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerFileSaveInfo.TigerFileSaveInfoBuilder;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.proxy.data.TracingMessagePairFacet;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@Slf4j
@TestInstance(Lifecycle.PER_CLASS)
@ResetTigerConfiguration
class TestTigerProxyFile extends AbstractTigerProxyTest {
    private static final String TGR_FILENAME = "target/reconstruction.tgr";

    @Test
    void saveToFileAndReadAgain_pairsShouldBeReconstructed() {
        executeFileWritingAndReadingTest(
            otherProxy -> {
                await().atMost(2, TimeUnit.SECONDS)
                    .until(() -> otherProxy.getRbelMessages().size() >= 4);
                assertThat(
                    otherProxy.getRbelLogger().getMessageHistory().getLast()
                        .getFacetOrFail(TracingMessagePairFacet.class)
                        .getRequest().findElement("$.path").get().getRawStringContent())
                    .isEqualTo("/faabor");
            },
            TigerFileSaveInfo.builder(),
            () -> {
                proxyRest.get("http://backend/foobar").asJson();
                proxyRest.get("http://backend/faabor").asJson();
                fileHasNLines(TGR_FILENAME, 4);
            });
    }

    @Test
    void filterFileForRequest_pairShouldBeIntact() {
        executeFileWritingAndReadingTest(
            otherProxy -> {
                await()
                    .atMost(2, TimeUnit.SECONDS)
                    .until(otherProxy::isFileParsed);
                assertThat(otherProxy.getRbelLogger().getMessageHistory().getFirst().findElement("$.path")
                    .get().getRawStringContent()).isEqualTo("/faabor");
                assertThat(otherProxy.getRbelLogger().getMessageHistory())
                    .hasSize(2);
            },
            TigerFileSaveInfo.builder()
                .readFilter("message.url =$ 'faabor'"),
            () -> {
                proxyRest.get("http://backend/foobar").asJson();
                proxyRest.get("http://backend/faabor").asJson();
                fileHasNLines(TGR_FILENAME, 4);
            });
    }

    @Test
    void filterFileForResponse_pairShouldBeIntact() {
        executeFileWritingAndReadingTest(
            otherProxy -> {
                await()
                    .atMost(2, TimeUnit.SECONDS)
                        .until(otherProxy::isFileParsed);
                assertThat(otherProxy.getRbelLogger().getMessageHistory().getFirst().findElement("$.path")
                        .get().getRawStringContent()).isEqualTo("/faabor");
                assertThat(otherProxy.getRbelLogger().getMessageHistory())
                    .hasSize(2);
            },
            TigerFileSaveInfo.builder()
                .readFilter("message.statusCode == '404'"),
            () -> {
                proxyRest.get("http://backend/foobar").asJson();
                proxyRest.get("http://backend/faabor").asJson();
                fileHasNLines(TGR_FILENAME, 4);
            });
    }

    @Test
    void filterFileForRequestWithRequestFilter_pairShouldBeIntact() {
        executeFileWritingAndReadingTest(
            otherProxy -> {
                await()
                    .atMost(2, TimeUnit.SECONDS)
                        .until(otherProxy::isFileParsed);
                assertThat(otherProxy.getRbelLogger().getMessageHistory().getFirst().findElement("$.path")
                        .get().getRawStringContent()).isEqualTo("/foobar");
                assertThat(otherProxy.getRbelLogger().getMessageHistory())
                    .hasSize(2);
            },
            TigerFileSaveInfo.builder()
                .readFilter("request.url !$ 'faabor'"),
            () -> {
                proxyRest.get("http://backend/foobar").asJson();
                proxyRest.get("http://backend/faabor").asJson();
                fileHasNLines(TGR_FILENAME, 4);
            });
    }

    @Test
    void errorWhileReadingTgrFile_expectStartupError() {
        final TigerProxyConfiguration configuration = TigerProxyConfiguration.builder()
            .fileSaveInfo(TigerFileSaveInfo.builder()
                .sourceFile("pom.xml")
                .build())
            .build();
        assertThatThrownBy(() -> {
            final TigerProxy proxy = new TigerProxy(configuration);
            await()
                .atMost(200, TimeUnit.SECONDS)
                .until(proxy::isFileParsed);
        })
            .isNotInstanceOf(ConditionTimeoutException.class);
    }

    private void executeFileWritingAndReadingTest(Consumer<TigerProxy> executeFileWritingAndReadingTest,
        TigerFileSaveInfoBuilder fileReaderInfoBuilder, Runnable generateTraffic) {
        FileUtils.deleteQuietly(new File(TGR_FILENAME));
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + fakeBackendServerPort)
                .build()))
            .fileSaveInfo(TigerFileSaveInfo.builder()
                .writeToFile(true)
                .clearFileOnBoot(true)
                .filename(TGR_FILENAME)
                .build())
            .build());

        generateTraffic.run();

        try (final TigerProxy otherProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .fileSaveInfo(fileReaderInfoBuilder
                .sourceFile(TGR_FILENAME)
                .build())
            .build())) {
            executeFileWritingAndReadingTest.accept(otherProxy);
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
