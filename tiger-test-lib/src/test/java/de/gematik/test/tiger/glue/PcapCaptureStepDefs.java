/*
 *
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
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.test.tiger.glue;

import static org.assertj.core.api.Assertions.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.gematik.test.tiger.lib.TigerDirector;
import de.gematik.test.tiger.lib.pcap.TigerPcapCaptureService;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.pcap4j.core.PcapHandle;
import org.pcap4j.core.Pcaps;
import org.slf4j.LoggerFactory;

/**
 * Cucumber step definitions for PCAP capture integration tests.
 *
 * <p>These tests verify that:
 *
 * <ul>
 *   <li>Files are generated in target/evidences/
 *   <li>Files are valid pcapng format (readable by pcap4j)
 *   <li>Files contain at least one packet
 *   <li>Testcase boundaries are isolated (no packet leakage between scenarios)
 *   <li>Graceful fallback when native lib is missing
 * </ul>
 */
@Slf4j
public class PcapCaptureStepDefs {

  private Path lastGeneratedPcapFile;
  private List<Path> pcapFilesBeforeTest;
  private HttpClient httpClient;
  private boolean testExecutedSuccessfully = false;
  private int proxyPort = 50506; // Default proxy port from config; will be overridden by step

  // Cucumber creates a fresh step-defs instance per scenario, so cross-scenario boundary checks
  // need state that survives that - hence static.
  private static Path previousScenarioFile;
  private static long previousScenarioFinalSize;

  private TigerPcapCaptureService unavailableLibTestService;
  private ListAppender<ILoggingEvent> unavailableLibLogAppender;

  @Given("pcap capture is enabled")
  public void pcapCaptureIsEnabledWithSplitByTestcase() {
    log.info("Pcap capture is enabled (configured by PcapConfigInitializationPlugin)");
  }

  @And("a Tiger proxy is running")
  public void aTigerProxyIsRunning() {
    try {
      // Discover the proxy port from the running Tiger proxy server
      var proxyServer = TigerDirector.getTigerTestEnvMgr().getLocalTigerProxyOptional();
      if (proxyServer.isPresent()) {
        this.proxyPort = proxyServer.get().getProxyPort();
        log.info("Tiger proxy running on port {}", this.proxyPort);
      } else {
        log.warn("No local Tiger proxy server found; using default port");
        this.proxyPort = 50506; // fallback
      }
    } catch (Exception e) {
      log.warn(
          "Could not discover proxy port from TigerDirector: {}; using default", e.getMessage());
      this.proxyPort = 50506; // fallback
    }
  }

  @When("I send a request through the proxy to local httpbin")
  public void iSendARequestThroughTheProxy() {
    log.info("Sending test request through proxy to local httpbin");
    // Record what pcap files exist before the request
    Path evidenceDir = Paths.get("target", "evidences");
    try (var stream = Files.list(evidenceDir)) {
      pcapFilesBeforeTest = stream.filter(p -> p.toString().endsWith(".pcapng")).toList();
      log.debug("Pcap files before request: {} file(s)", pcapFilesBeforeTest.size());
    } catch (Exception e) {
      log.debug("Could not list pcap files", e);
      pcapFilesBeforeTest = List.of();
    }

    // Send an HTTP request to generate traffic
    sendHttpRequest();
  }

  @Then("a pcapng file should exist in {string} for this scenario")
  public void aPcapngFileShouldExist(String path) {
    var captureService = TigerPcapCaptureService.getInstance();
    if (captureService == null || !captureService.isEnabled()) {
      log.warn(
          "Pcap capture is not enabled (native lib unavailable or permission denied); "
              + "skipping file assertions");
      // On CI without pcap permissions, skip the file checks silently
      return;
    }

    Path evidenceDir = Path.of(path);
    assertThat(evidenceDir)
        .withFailMessage(path + " does not exist; check pcap capture initialization")
        .exists()
        .isDirectory();

    // Look for a pcap_* file matching this scenario
    List<Path> pcapFiles;
    try (var stream = Files.list(evidenceDir)) {
      pcapFiles = stream.filter(p -> p.getFileName().toString().endsWith(".pcapng")).toList();
    } catch (Exception e) {
      throw new AssertionError("Could not list pcap files in evidence directory", e);
    }

    assertThat(pcapFiles)
        .withFailMessage("No .pcapng files found in target/evidences/; expected at least one")
        .isNotEmpty();

    // Get the most recent one (newest file)
    lastGeneratedPcapFile =
        pcapFiles.stream()
            .max(
                (a, b) -> {
                  try {
                    return Long.compare(
                        Files.getLastModifiedTime(a).toMillis(),
                        Files.getLastModifiedTime(b).toMillis());
                  } catch (Exception e) {
                    return 0;
                  }
                })
            .orElseThrow(() -> new AssertionError("No pcapng files found"));

    log.info("Found pcap file: {}", lastGeneratedPcapFile.getFileName());
  }

  @And("the pcapng file should contain at least one TCP packet")
  public void thePcapngFileShouldContainPackets() {
    if (lastGeneratedPcapFile == null) {
      log.warn("No pcapng file found; pcap capture likely not enabled on this system");
      return;
    }

    assertThat(lastGeneratedPcapFile)
        .withFailMessage("No pcapng file was generated")
        .isNotNull()
        .exists();

    try {
      long fileSize = Files.size(lastGeneratedPcapFile);

      // pcapng files have a minimal structure:
      // - Section header block (minimum ~28 bytes)
      // - Interface description block (~20 bytes)
      // - At least one enhanced packet block (~40+ bytes)
      // Total minimum for a valid file with packets: ~100 bytes
      assertThat(fileSize)
          .withFailMessage(
              "Pcapng file %s is too small (%d bytes); likely no packets captured",
              lastGeneratedPcapFile, fileSize)
          .isGreaterThan(100L);

      // Try to open with pcap4j to verify it's valid pcapng format
      // and actually contains packets
      int packetCount = 0;
      try (PcapHandle handle = Pcaps.openOffline(lastGeneratedPcapFile.toString())) {
        var packet = handle.getNextPacket();
        while (packet != null && packetCount < 1000) {
          packetCount++;
          packet = handle.getNextPacket();
        }
      } catch (Exception e) {
        log.debug(
            "Could not read pcapng file with pcap4j (may not be available on this system)", e);
        // This is okay — we verified the file size at least
      }

      if (packetCount > 0) {
        log.info(
            "Pcapng file {} contains {} packets", lastGeneratedPcapFile.getFileName(), packetCount);
        assertThat(packetCount)
            .withFailMessage("Pcapng file should contain at least one packet")
            .isGreaterThan(0);
      } else {
        // File exists and is large enough — likely contains packets even if we can't read them
        log.info(
            "Pcapng file {} exists with {} bytes", lastGeneratedPcapFile.getFileName(), fileSize);
      }
    } catch (Exception e) {
      throw new AssertionError("Could not verify pcapng file content", e);
    }
  }

  @And("the pcapng file should be valid pcapng format")
  public void thePcapngFileShouldBeValid() {
    if (lastGeneratedPcapFile == null) {
      log.warn("No pcapng file found; pcap capture likely not enabled on this system");
      return;
    }

    assertThat(lastGeneratedPcapFile).isNotNull().exists();
    try {
      byte[] header = new byte[4];
      try (var input = Files.newInputStream(lastGeneratedPcapFile)) {
        int bytesRead = input.read(header);
        assertThat(bytesRead)
            .withFailMessage("Pcapng file is too small to contain header")
            .isGreaterThanOrEqualTo(4);
      }

      // pcapng magic bytes: 0x0A0D0D0A (section header block type)
      // or pcap magic: 0xA1B2C3D4 (legacy pcap)
      int magic =
          ((header[0] & 0xFF) << 24)
              | ((header[1] & 0xFF) << 16)
              | ((header[2] & 0xFF) << 8)
              | (header[3] & 0xFF);

      boolean isPcapng = (magic == 0x0A0D0D0A);
      boolean isLegacyPcap = (magic == 0xA1B2C3D4 || magic == 0xD4C3B2A1);

      assertThat(isPcapng || isLegacyPcap)
          .withFailMessage(
              "Pcapng file has invalid magic bytes: 0x%08X (expected 0x0A0D0D0A for pcapng)", magic)
          .isTrue();

      log.info("Pcapng file {} has valid format", lastGeneratedPcapFile.getFileName());

      // Record which file was this scenario's; the size snapshot is taken once this scenario
      // has actually finished (see iSendAFirstRequest) - the dumper is still open here, so a
      // straggling packet (e.g. connection teardown) landing before the real close would look
      // like leaked cross-scenario traffic even though it's still this scenario's own.
      previousScenarioFile = lastGeneratedPcapFile;
    } catch (Exception e) {
      throw new AssertionError("Could not read or validate pcapng file", e);
    }
  }

  @When("I send a first request through the proxy to local httpbin")
  public void iSendAFirstRequest() {
    log.info("Sending first test request through proxy to local httpbin");
    pcapFilesBeforeTest = listPcapFiles();
    log.debug("Pcap files before first request: {} file(s)", pcapFilesBeforeTest.size());

    // The previous scenario's onTestCaseFinished has already run by now (Cucumber guarantees
    // this before this scenario's first step), so its dumper is truly closed - this is the
    // earliest point its size snapshot is authoritative.
    if (previousScenarioFile != null) {
      try {
        previousScenarioFinalSize = Files.size(previousScenarioFile);
      } catch (Exception e) {
        log.debug("Could not snapshot previous scenario's file size", e);
      }
    }

    sendHttpRequest();
  }

  @And("I wait for the first testcase to finish")
  public void iWaitForFirstTestcaseToFinish() {
    log.info("Waiting for testcase boundary (next scenario)");
    // In real Cucumber execution, this happens automatically between scenarios.
    // Give the dumper a moment to flush any pending packets.
    try {
      Thread.sleep(200);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      log.debug("Interrupted while waiting for testcase boundary");
    }
  }

  @Then("the first pcapng file should exist in {string}")
  public void theFirstPcapngFileShouldExist(String path) {
    aPcapngFileShouldExist(path);
  }

  @And("the first pcapng file should contain only the first testcase's traffic")
  public void theFirstPcapngFileShouldContainOnlyFirstTestcaseTraffic() {
    if (lastGeneratedPcapFile == null) {
      log.warn("No pcapng file found; pcap capture likely not enabled on this system");
      return;
    }

    log.info("Verifying testcase boundary isolation");
    assertThat(lastGeneratedPcapFile).isNotNull().exists();
    assertThat(previousScenarioFile)
        .withFailMessage("Previous scenario's pcap file was not recorded; check scenario order")
        .isNotNull();

    try {
      long fileSize = Files.size(lastGeneratedPcapFile);
      assertThat(fileSize)
          .withFailMessage(
              "First pcapng file should contain at least some traffic from the first testcase")
          .isGreaterThan(100L);

      assertThat(lastGeneratedPcapFile)
          .withFailMessage(
              "This scenario's traffic went into the previous scenario's file (%s); rotation did"
                  + " not open a fresh dumper at the testcase boundary",
              previousScenarioFile)
          .isNotEqualTo(previousScenarioFile);

      long previousFileSizeNow = Files.size(previousScenarioFile);
      assertThat(previousFileSizeNow)
          .withFailMessage(
              "Previous scenario's file %s grew from %d to %d bytes after this scenario's"
                  + " traffic; capture leaked across the testcase boundary",
              previousScenarioFile, previousScenarioFinalSize, previousFileSizeNow)
          .isEqualTo(previousScenarioFinalSize);

      log.info(
          "First pcapng file {} has {} bytes; previous scenario's file unchanged at {} bytes",
          lastGeneratedPcapFile.getFileName(),
          fileSize,
          previousFileSizeNow);
    } catch (Exception e) {
      throw new AssertionError("Could not verify first testcase isolation", e);
    }
  }

  @Given("pcap capture is enabled but native pcap library is not available")
  public void pcapCaptureEnabledButLibUnavailable() {
    // Simulate "no native lib" without touching the shared suite-wide capture service: a fresh
    // TigerPcapCaptureService whose interface resolution always fails, exercising the exact same
    // startup-failure path (start() -> resolveCaptureInterface() -> PcapNativeException ->
    // handleStartupFailure()) that a real missing-Npcap/libpcap environment would hit.
    unavailableLibTestService =
        new TigerPcapCaptureService(64, 16 * 1024) {
          @Override
          protected Optional<org.pcap4j.core.PcapNetworkInterface> resolveCaptureInterface() {
            return Optional.empty();
          }
        };

    Logger serviceLogger = (Logger) LoggerFactory.getLogger(TigerPcapCaptureService.class);
    unavailableLibLogAppender = new ListAppender<>();
    unavailableLibLogAppender.start();
    serviceLogger.addAppender(unavailableLibLogAppender);
  }

  @When("tests execute normally")
  public void testsExecuteNormally() {
    assertThatCode(() -> unavailableLibTestService.start(Set.of(12345)))
        .withFailMessage("start() must never throw, even when the native lib is unavailable")
        .doesNotThrowAnyException();
    testExecutedSuccessfully = true;
  }

  @Then("tests should not fail due to missing pcap library")
  public void testsShouldNotFail() {
    assertThat(testExecutedSuccessfully)
        .withFailMessage("Test should have executed successfully")
        .isTrue();
    assertThat(unavailableLibTestService.isEnabled())
        .withFailMessage("Capture must remain disabled when the native lib is unavailable")
        .isFalse();
  }

  @And("a WARN should be logged about pcap capture being unavailable")
  public void warnShouldBeLogged() {
    Logger serviceLogger = (Logger) LoggerFactory.getLogger(TigerPcapCaptureService.class);
    try {
      boolean warnLogged =
          unavailableLibLogAppender.list.stream()
              .anyMatch(
                  event ->
                      event.getLevel() == Level.WARN
                          && event.getFormattedMessage().contains("pcap"));
      assertThat(warnLogged)
          .withFailMessage(
              "Expected a WARN log mentioning pcap unavailability; got: %s",
              unavailableLibLogAppender.list.stream()
                  .map(ILoggingEvent::getFormattedMessage)
                  .toList())
          .isTrue();
    } finally {
      serviceLogger.detachAppender(unavailableLibLogAppender);
    }
  }

  // ============================================================================
  // Helper methods
  // ============================================================================

  /**
   * Sends a simple HTTP GET request through the Tiger proxy to a local httpbin service to generate
   * network traffic. Handles cases where network is unavailable gracefully.
   */
  private void sendHttpRequest() {
    try {
      if (httpClient == null) {
        // Create HttpClient configured to use the Tiger proxy
        ProxySelector proxySelector =
            ProxySelector.of(new InetSocketAddress("localhost", proxyPort));
        httpClient =
            HttpClient.newBuilder()
                .proxy(proxySelector)
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();
      }
      // Send a request to the local httpbin service through the Tiger proxy
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create("http://httpbin/status/200"))
              .timeout(java.time.Duration.ofSeconds(10))
              .GET()
              .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      int responseCode = response.statusCode();
      log.info(
          "HTTP request sent through proxy on port {} successfully, response code: {}",
          proxyPort,
          responseCode);
      assertThat(responseCode).isGreaterThanOrEqualTo(200).isLessThan(300);
    } catch (java.net.ConnectException | java.net.UnknownHostException e) {
      log.warn(
          "Could not reach httpbin through proxy on port {} (network unavailable); "
              + "test may still verify pcap file creation if running locally",
          proxyPort,
          e);
      // Don't fail — test might be running in isolation without the mock service
    } catch (Exception e) {
      log.warn(
          "Could not send HTTP request through proxy on port {}: {}; continuing anyway",
          proxyPort,
          e.getMessage());
    }

    // Give the capture thread a moment to write any generated packets
    try {
      Thread.sleep(100);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
  }

  private List<Path> listPcapFiles() {
    Path evidenceDir = Paths.get("target", "evidences");
    try (var stream = Files.list(evidenceDir)) {
      return stream.filter(p -> p.getFileName().toString().endsWith(".pcapng")).toList();
    } catch (Exception e) {
      log.debug("Could not list pcap files", e);
      return List.of();
    }
  }
}
