/*
 * Copyright (c) 2024 gematik GmbH
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

package de.gematik.test.tiger.testenvmgr.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockserver.model.HttpRequest.request;

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.jcip.annotations.NotThreadSafe;
import org.apache.commons.io.FileUtils;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.mock.Expectation;
import org.mockserver.model.Delay;
import org.mockserver.model.HttpResponse;
import org.mockserver.netty.MockServer;

@Slf4j
@ResetTigerConfiguration
@NotThreadSafe
class TestEnvDownload {
  private static final Path DOWNLOAD_FOLDER_PATH = Path.of("target", "jarDownloadTest");

  private static MockServer mockServer;
  private static MockServerClient mockServerClient;
  private static Expectation downloadExpectation;
  private static byte[] winstoneBytes;
  private static byte[] tigerProxyBytes;

  @BeforeEach
  public void cleanDownloadFolder() throws IOException {
    log.info("Cleaning download folder...");
    if (DOWNLOAD_FOLDER_PATH.toFile().exists()) {
      FileUtils.deleteDirectory(DOWNLOAD_FOLDER_PATH.toFile());
    } else {
      FileUtils.forceMkdir(DOWNLOAD_FOLDER_PATH.toFile());
    }
  }

  @BeforeEach
  public void startServer() throws IOException {
    if (mockServer != null) {
      return;
    }
    log.info("Booting MockServer...");
    // this is necessary to actually find the downloads later on in the mockserver event log
    ConfigurationProperties.maxLogEntries(10);
    mockServer = new MockServer();
    mockServerClient = new MockServerClient("localhost", mockServer.getLocalPort());

    final File winstoneFile = new File("target/winstone.jar");
    if (!winstoneFile.exists()) {
      throw new RuntimeException(
          "winstone.jar not found in target-folder. "
              + "Did you run mvn generate-test-resources? (It should be downloaded automatically)");
    }
    winstoneBytes = FileUtils.readFileToByteArray(winstoneFile);
    tigerProxyBytes =
        FileUtils.readFileToByteArray(
            Files.walk(Path.of("..", "tiger-standalone-proxy", "target"))
                .filter(p -> p.toString().endsWith(".jar"))
                .filter(p -> !p.toString().endsWith("-javadoc.jar"))
                .filter(p -> !p.toString().endsWith("-sources.jar"))
                .map(Path::toFile)
                .findAny()
                .orElseThrow());

    downloadExpectation =
        mockServerClient.when(request().withPath("/download"))
            .respond(req -> HttpResponse.response().withBody(winstoneBytes))[0];

    mockServerClient
        .when(request().withPath("/tiger/download"))
        .respond(req -> HttpResponse.response().withBody(tigerProxyBytes), Delay.seconds(2));
  }

  @SneakyThrows
  @Test
  void multipleParallelDownload_shouldNotInterfere() {
    loadConfigurationWithJarsLoadedFromUrls(
        "http://localhost:" + mockServer.getLocalPort() + "/tiger/download",
        "http://localhost:" + mockServer.getLocalPort() + "/download");
    createTestEnvMgrSafelyAndExecute(TigerTestEnvMgr::setUpEnvironment);

    assertThat(
            Files.walk(Path.of("target", "jarDownloadTest"))
                .map(Path::toFile)
                .filter(File::isFile)
                .filter(file -> file.length() > 1000)
                .collect(Collectors.toList()))
        .hasSize(2);
  }

  @SneakyThrows
  @Test
  void reboot_shouldNotRetriggerDownload() {
    var initialNumberOfDownloads = getNumberOfDownloads();

    loadConfigurationWithJarsLoadedFromUrls(
        "http://localhost:" + mockServer.getLocalPort() + "/download");

    createTestEnvMgrSafelyAndExecute(TigerTestEnvMgr::setUpEnvironment);
    createTestEnvMgrSafelyAndExecute(TigerTestEnvMgr::setUpEnvironment);

    assertThat(getNumberOfDownloads()).isEqualTo(initialNumberOfDownloads + 1);
  }

  @SneakyThrows
  @Test
  void twoIdenticalJars_onlyOneDownloadShouldBeTriggered() {
    int initialNumberOfDownloads = getNumberOfDownloads();

    loadConfigurationWithJarsLoadedFromUrls(
        "http://localhost:" + mockServer.getLocalPort() + "/download",
        "http://localhost:" + mockServer.getLocalPort() + "/download");

    createTestEnvMgrSafelyAndExecute(TigerTestEnvMgr::setUpEnvironment);

    assertThat(getNumberOfDownloads()).isEqualTo(initialNumberOfDownloads + 1);
  }

  @SneakyThrows
  @Test
  void failingStartupAfterSuccessfulDownload_shouldRetryOnNextBoot() {
    final Expectation failingExpectation =
        mockServerClient.when(request().withPath("/failDownload"))
            .respond(
                req ->
                    HttpResponse.response().withBody("not a jar".getBytes(StandardCharsets.UTF_8)))[
            0];

    int initialNumberOfDownloads = getNumberOfDownloads(failingExpectation);

    loadConfigurationWithJarsLoadedFromUrls(
        "http://localhost:" + mockServer.getLocalPort() + "/failDownload");

    assertThatThrownBy(() -> createTestEnvMgrSafelyAndExecute(TigerTestEnvMgr::setUpEnvironment))
        .isInstanceOf(TigerTestEnvException.class);

    assertThat(
            Files.walk(DOWNLOAD_FOLDER_PATH)
                .filter(path -> path.toString().endsWith("failDownload"))
                .collect(Collectors.toList()))
        .isEmpty();

    assertThatThrownBy(() -> createTestEnvMgrSafelyAndExecute(TigerTestEnvMgr::setUpEnvironment))
        .isInstanceOf(TigerTestEnvException.class);

    assertThat(getNumberOfDownloads(failingExpectation)).isEqualTo(initialNumberOfDownloads + 2);
  }

  private int getNumberOfDownloads() {
    return getNumberOfDownloads(downloadExpectation);
  }

  private int getNumberOfDownloads(Expectation expectation) {
    return mockServerClient.retrieveRecordedRequests(expectation.getHttpRequest()).length;
  }

  private void loadConfigurationWithJarsLoadedFromUrls(String... jarDownloadUrl) {
    System.clearProperty("TIGER_TESTENV_CFGFILE");
    TigerGlobalConfiguration.reset();
    TigerGlobalConfiguration.initialize();
    StringBuilder yamlSource =
        new StringBuilder(
            """
                    testenv:
                    cfgfile: src/test/resources/tiger-testenv.yaml
                    servers:
                    """);
    for (int i = 0; i < jarDownloadUrl.length; i++) {
      yamlSource.append(
          """
                externalJarServer%d:
                  type: externalJar
                  startupTimeoutSec: 50
                  source:
                  - %s
                  healthcheckUrl: http://127.0.0.1:${free.port.%d}
                  externalJarOptions:
                    workingDir: "target/jarDownloadTest"
                    arguments:
              """
              .formatted(i, jarDownloadUrl[i], 10 + i));
      if (jarDownloadUrl[i].contains("tiger")) {
        yamlSource.append("        - --server.port=${free.port.").append(10 + i).append("}\n");
      } else {
        yamlSource
            .append("        - --httpPort=${free.port.")
            .append(10 + i)
            .append("}\n")
            .append("        - --webroot=.\n");
      }
    }

    TigerGlobalConfiguration.readFromYaml(yamlSource.toString(), "tiger");
  }

  private void createTestEnvMgrSafelyAndExecute(
      ThrowingConsumer<TigerTestEnvMgr> testEnvMgrConsumer) {
    TigerTestEnvMgr envMgr = null;
    try {
      TigerGlobalConfiguration.initialize();
      envMgr = new TigerTestEnvMgr();
      testEnvMgrConsumer.accept(envMgr);
    } finally {
      if (envMgr != null) {
        envMgr.shutDown();
      }
    }
  }
}
