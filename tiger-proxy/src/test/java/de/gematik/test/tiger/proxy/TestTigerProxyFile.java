/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import static de.gematik.rbellogger.data.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.TracingMessagePairFacet;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerFileSaveInfo;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerFileSaveInfo.TigerFileSaveInfoBuilder;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerRoute;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.proxy.certificate.TlsFacet;
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
  private static final String TGR_FILENAME = "target/reconstruction";

  @Test
  void saveToFileAndReadAgain_pairsShouldBeReconstructed() {
    executeFileWritingAndReadingTest(
        otherProxy -> {
          await().atMost(2, TimeUnit.SECONDS).until(() -> otherProxy.getRbelMessages().size() >= 4);
          assertThat(
                  otherProxy
                      .getRbelLogger()
                      .getMessageHistory()
                      .getLast()
                      .getFacetOrFail(TracingMessagePairFacet.class)
                      .getRequest()
                      .findElement("$.path")
                      .get()
                      .getRawStringContent())
              .isEqualTo("/faabor");
        },
        TigerFileSaveInfo.builder(),
        () -> {
          proxyRest.get("http://backend/foobar").asJson();
          proxyRest.get("http://backend/faabor").asJson();
          fileHasNLines(TGR_FILENAME + "1.tgr", 4);
        },
        TGR_FILENAME + "1.tgr");
  }

  @Test
  void filterFileForRequest_pairShouldBeIntact() {
    executeFileWritingAndReadingTest(
        otherProxy -> {
          await().atMost(2, TimeUnit.SECONDS).until(otherProxy::isFileParsed);
          assertThat(
                  otherProxy
                      .getRbelLogger()
                      .getMessageHistory()
                      .getFirst()
                      .findElement("$.path")
                      .get()
                      .getRawStringContent())
              .isEqualTo("/faabor");
          assertThat(otherProxy.getRbelLogger().getMessageHistory()).hasSize(2);
        },
        TigerFileSaveInfo.builder().readFilter("message.path == '/faabor'"),
        () -> {
          proxyRest.get("http://backend/foobar").asJson();
          proxyRest.get("http://backend/faabor").asJson();
          fileHasNLines(TGR_FILENAME + "2.tgr", 4);
        },
        TGR_FILENAME + "2.tgr");
  }

  @Test
  void filterFileForResponse_pairShouldBeIntact() {
    executeFileWritingAndReadingTest(
        otherProxy -> {
          await().atMost(2, TimeUnit.SECONDS).until(otherProxy::isFileParsed);
          assertThat(
                  otherProxy
                      .getRbelLogger()
                      .getMessageHistory()
                      .getFirst()
                      .findElement("$.path")
                      .get()
                      .getRawStringContent())
              .isEqualTo("/faabor");
          assertThat(otherProxy.getRbelLogger().getMessageHistory()).hasSize(2);
        },
        TigerFileSaveInfo.builder().readFilter("message.statusCode == '404'"),
        () -> {
          proxyRest.get("http://backend/foobar").asJson();
          proxyRest.get("http://backend/faabor").asJson();
          fileHasNLines(TGR_FILENAME + "3.tgr", 4);
        },
        TGR_FILENAME + "3.tgr");
  }

  @Test
  void filterFileForRequestWithRequestFilter_pairShouldBeIntact() {
    executeFileWritingAndReadingTest(
        otherProxy -> {
          await().atMost(2, TimeUnit.SECONDS).until(otherProxy::isFileParsed);
          assertThat(
                  otherProxy
                      .getRbelLogger()
                      .getMessageHistory()
                      .getFirst()
                      .findElement("$.path")
                      .get()
                      .getRawStringContent())
              .isEqualTo("/foobar");
          assertThat(otherProxy.getRbelLogger().getMessageHistory()).hasSize(2);
        },
        TigerFileSaveInfo.builder().readFilter("request.url !$ 'faabor'"),
        () -> {
          proxyRest.get("http://backend/foobar").asJson();
          proxyRest.get("http://backend/faabor").asJson();
          fileHasNLines(TGR_FILENAME + "4.tgr", 4);
        },
        TGR_FILENAME + "4.tgr");
  }

  @Test
  void saveTlsConnection_detailsShouldBeIntactAfterReadingFromFile() {
    executeFileWritingAndReadingTest(
        otherProxy -> {
          await().atMost(2, TimeUnit.SECONDS).until(otherProxy::isFileParsed);
          final RbelElement request = otherProxy.getRbelLogger().getMessageHistory().getFirst();
          assertThat(request)
              .hasFacet(TlsFacet.class)
              .extractChildWithPath("$.cipherSuite")
              .hasStringContentEqualTo("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256");
          assertThat(request)
              .extractChildWithPath("$.tlsVersion")
              .hasStringContentEqualTo("TLSv1.2");
        },
        TigerFileSaveInfo.builder(),
        () -> {
          proxyRest.get("https://backend/foobar").asJson();
          proxyRest.get("https://backend/faabor").asJson();
          fileHasNLines(TGR_FILENAME + "5.tgr", 4);
        },
        TGR_FILENAME + "5.tgr");
  }

  @Test
  void errorWhileReadingTgrFile_expectStartupError() {
    final TigerProxyConfiguration configuration =
        TigerProxyConfiguration.builder()
            .fileSaveInfo(TigerFileSaveInfo.builder().sourceFile("pom.xml").build())
            .build();
    assertThatThrownBy(
            () -> {
              final TigerProxy proxy = new TigerProxy(configuration);
              await().atMost(200, TimeUnit.SECONDS).until(proxy::isFileParsed);
            })
        .isNotInstanceOf(ConditionTimeoutException.class);
  }

  private void executeFileWritingAndReadingTest(
      Consumer<TigerProxy> executeFileWritingAndReadingTest,
      TigerFileSaveInfoBuilder fileReaderInfoBuilder,
      Runnable generateTraffic,
      String tgrFilename) {
    FileUtils.deleteQuietly(new File(tgrFilename));
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("http://backend")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .fileSaveInfo(
                TigerFileSaveInfo.builder()
                    .writeToFile(true)
                    .clearFileOnBoot(true)
                    .filename(tgrFilename)
                    .build())
            .build());

    generateTraffic.run();

    try (final TigerProxy otherProxy =
        new TigerProxy(
            TigerProxyConfiguration.builder()
                .fileSaveInfo(fileReaderInfoBuilder.sourceFile(tgrFilename).build())
                .build())) {
      executeFileWritingAndReadingTest.accept(otherProxy);
      FileUtils.deleteQuietly(new File(tgrFilename));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void fileHasNLines(String filename, int lines) {
    File f = new File(filename);
    await()
        .atMost(10, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .until(() -> f.exists() && FileUtils.readLines(f, StandardCharsets.UTF_8).size() >= lines);
  }
}
