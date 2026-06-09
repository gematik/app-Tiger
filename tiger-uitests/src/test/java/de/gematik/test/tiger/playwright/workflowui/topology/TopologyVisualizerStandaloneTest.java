/*
 * Copyright 2021-2026 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 *
 */
package de.gematik.test.tiger.playwright.workflowui.topology;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.*;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests for the standalone topology visualizer.
 *
 * <p>Mostly to take screenshots.
 */
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TopologyVisualizerStandaloneTest {

  private static final Path STANDALONE_JAR =
      findStandaloneJar(Paths.get("../tiger-topology-visualizer/target"));

  private Process serverProcess;
  private int serverPort;
  private Playwright playwright;
  private Browser browser;
  private Page page;

  @BeforeAll
  void startServerAndBrowser() throws Exception {
    serverPort = findFreePort();
    log.info("Starting standalone topology visualizer on port {}...", serverPort);
    serverProcess = startStandaloneJar(serverPort);
    waitForServerReady(serverPort);

    playwright = Playwright.create();
    // Using chromium explicitly
    // In some screenshots we remove the background, and that does
    // not work under firefox.
    browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    log.info("Browser launched, navigating to http://localhost:{}", serverPort);
  }

  @BeforeEach
  void newPage() {
    page = browser.newPage();
    page.setDefaultTimeout(30_000);
    page.navigate("http://localhost:" + serverPort);
  }

  @AfterEach
  void closePage() {
    if (page != null) {
      page.close();
    }
  }

  @AfterAll
  void cleanup() {
    if (browser != null) browser.close();
    if (playwright != null) playwright.close();
    if (serverProcess != null) {
      serverProcess.destroyForcibly();
      log.info("Standalone topology visualizer stopped.");
    }
  }

  @Test
  void pageLoadsAndShowsFileUploadArea() {
    PlaywrightAssertions.assertThat(page.locator(".p-fileupload")).isVisible();
    PlaywrightAssertions.assertThat(page.locator(".uploaded-files__empty"))
        .containsText("Choose one or more YAML files");
  }

  @ParameterizedTest
  @CsvSource({
    "tiger-proxy-with-connections, 5",
    "tiger-external-url, 2",
    "tiger-external-jar, 2",
    "tiger-httpbin, 2",
    "tiger-zion, 3",
    "tiger-docker, 3" // docker host is also a node
  })
  @Tag("screenshot")
  void uploadYamlAndScreenshotDiagram(String yamlName, int expectedNodeCount) {
    uploadFiles(Path.of("src/test/resources/topology-test-yamls/", yamlName + ".yaml"));

    var nodes = page.locator(".vue-flow__node");
    nodes.first().waitFor();

    int nodeCount = nodes.count();
    assertThat(nodeCount).isEqualTo(expectedNodeCount);
    screenshotDiagram("topology_" + yamlName + ".png");

    clearUploadedFiles();
  }

  // As standalone test because it uploads two files.
  @Test
  @Tag("screenshot")
  void uploadYamlComposeAndScreenshot() {
    var folder = Path.of("src/test/resources/topology-test-yamls");
    uploadFiles(folder.resolve("tiger-compose.yaml"), folder.resolve("docker-compose.yaml"));

    var nodes = page.locator(".vue-flow__node");
    nodes.first().waitFor();

    int nodeCount = nodes.count();
    assertThat(nodeCount).isEqualTo(5);
    screenshotDiagram("topology_tiger-compose.png");
    clearUploadedFiles();
  }

  void clearUploadedFiles() {
    var clearFilesButton = page.locator(".p-fileupload-cancel-button");
    clearFilesButton.click();
  }

  protected Path getPath(String file) {
    return Paths.get("..", "doc", "user_manual", "screenshots", file);
  }

  private static final String HIDE_OVERLAYS_CSS =
      ".vue-flow__minimap, .vue-flow__controls, .vue-flow__background { display: none !important; }";

  protected void screenshotDiagram(String outputFileName) {
    // hide file upload controls, so that we have more room to zoom in diagram
    var styleTag =
        page.addStyleTag(
            new Page.AddStyleTagOptions()
                .setContent(".p-fileupload-advanced { display: none !important; }"));
    fitDiagramToView();
    page.waitForTimeout(500);
    page.locator(".vue-flow")
        .screenshot(
            new Locator.ScreenshotOptions()
                .setOmitBackground(true)
                .setStyle(HIDE_OVERLAYS_CSS)
                .setPath(getPath(outputFileName)));
    styleTag.evaluate("el => el.remove()");
  }

  private void fitDiagramToView() {
    page.locator(".vue-flow__controls-fitview").click();
  }

  private void uploadFiles(Path... files) {
    var fileInput = page.locator("input[type='file']");
    fileInput.setInputFiles(files);
    page.locator(".uploaded-file__status--processed").first().waitFor();
  }

  private static int findFreePort() throws IOException {
    try (var socket = new java.net.ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }

  private static Process startStandaloneJar(int port) throws IOException {
    log.info("Starting JAR: {}", STANDALONE_JAR);
    var pb = new ProcessBuilder("java", "-jar", STANDALONE_JAR.toString(), "--server.port=" + port);
    pb.inheritIO();
    return pb.start();
  }

  private static void waitForServerReady(int port) {
    log.info("Waiting for server to be ready on port {}...", port);
    Awaitility.await()
        .atMost(60, TimeUnit.SECONDS)
        .pollInterval(1, TimeUnit.SECONDS)
        .until(
            () -> {
              try {
                var conn =
                    (HttpURLConnection)
                        URI.create("http://localhost:" + port).toURL().openConnection();
                conn.setConnectTimeout(1000);
                conn.setReadTimeout(1000);
                conn.setRequestMethod("GET");
                int code = conn.getResponseCode();
                conn.disconnect();
                return code == 200;
              } catch (Exception e) {
                return false;
              }
            });
    log.info("Server is ready on port {}", port);
  }

  private static Path findStandaloneJar(Path targetDir) {
    try (var files = Files.list(targetDir)) {
      return files
          .filter(p -> p.getFileName().toString().endsWith("-standalone.jar"))
          .findFirst()
          .orElseThrow(
              () ->
                  new IllegalStateException(
                      "Standalone JAR not found in "
                          + targetDir.toAbsolutePath()
                          + ". Run 'mvn package -DskipTests' in tiger-topology-visualizer first."));
    } catch (IOException e) {
      throw new IllegalStateException(
          "Could not scan for standalone JAR in " + targetDir.toAbsolutePath(), e);
    }
  }
}
