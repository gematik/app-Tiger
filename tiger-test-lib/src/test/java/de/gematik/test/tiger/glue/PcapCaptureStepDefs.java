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

import static de.gematik.rbellogger.util.MemoryConstants.KB;
import static org.assertj.core.api.Assertions.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.gematik.test.tiger.lib.TigerDirector;
import de.gematik.test.tiger.lib.pcap.ScenarioPcapCaptureService;
import de.gematik.test.tiger.proxy.handler.pcap.PcapCaptureEngine;
import de.gematik.test.tiger.proxy.handler.pcap.PcapNgFileReader;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.pcap4j.packet.namednumber.DataLinkType;
import org.slf4j.LoggerFactory;

/**
 * Steps for the pcap capture integration tests. Every scenario finishes its own capture ({@link
 * #thisScenariosCaptureIsFinished()}) and looks at exactly that file, since the file only exists
 * once the capture is closed.
 */
@Slf4j
public class PcapCaptureStepDefs {

  private static final int PCAPNG_SECTION_HEADER_MAGIC = 0x0A0D0D0A;
  private static final int TCP = 6;

  private HttpClient httpClient;
  private boolean testExecutedSuccessfully = false;
  private int proxyPort = 50506; // Default proxy port from config; will be overridden by step

  // Set by "this scenario's capture is finished"; null if capture is not available on this system.
  private Path finishedCaptureFile;
  private int finishedCapturePackets;
  private long finishedCaptureBytes;

  private ScenarioPcapCaptureService unavailableLibTestService;
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
    } catch (RuntimeException e) {
      log.warn(
          "Could not discover proxy port from TigerDirector: {}; using default", e.getMessage());
      this.proxyPort = 50506; // fallback
    }
  }

  @When("I send a request through the proxy to local httpbin")
  public void iSendARequestThroughTheProxy() {
    log.info("Sending test request through proxy to local httpbin");
    sendHttpRequest();
  }

  @When("I send a first request through the proxy to local httpbin")
  public void iSendAFirstRequest() {
    log.info("Sending first test request through proxy to local httpbin");
    sendHttpRequest();
  }

  @And("I send a second request through the proxy to local httpbin")
  public void iSendASecondRequest() {
    log.info("Sending second test request through proxy to local httpbin");
    sendHttpRequest();
  }

  /**
   * Finishes this scenario's capture right now: closes the file it is being written to, so that it
   * is complete and in place. (At the end of a scenario Tiger does the same; a scenario's steps
   * cannot wait for that.)
   */
  @And("this scenario's capture is finished")
  public void thisScenariosCaptureIsFinished() throws IOException {
    var captureService = ScenarioPcapCaptureService.getInstance();
    if (captureService == null || !captureService.isEnabled()) {
      log.warn(
          "Pcap capture is not enabled (native lib unavailable or permission denied); "
              + "skipping file assertions");
      // On CI without pcap permissions, skip the file checks silently
      finishedCaptureFile = null;
      return;
    }

    Path scenarioFile =
        captureService
            .currentScenarioFile()
            .orElseThrow(
                () ->
                    new AssertionError(
                        "This scenario is not being captured into a file of its own; is"
                            + " splitByTestcase enabled?"));
    captureService.closeDumper(scenarioFile);

    finishedCaptureFile = scenarioFile;
    finishedCapturePackets = readFrames(scenarioFile).size();
    finishedCaptureBytes = Files.size(scenarioFile);
    log.info(
        "Finished capture {}: {} packets, {} bytes",
        scenarioFile.getFileName(),
        finishedCapturePackets,
        finishedCaptureBytes);
  }

  @Then("a pcapng file should exist in {string} for this scenario")
  public void aPcapngFileShouldExist(String path) {
    if (finishedCaptureFile == null) {
      log.warn("No finished capture file; pcap capture likely not enabled on this system");
      return;
    }

    Path evidenceDir = Path.of(path).toAbsolutePath().normalize();
    assertThat(evidenceDir)
        .withFailMessage(path + " does not exist; check pcap capture initialization")
        .exists()
        .isDirectory();
    assertThat(finishedCaptureFile.toAbsolutePath().normalize().getParent())
        .withFailMessage("This scenario's capture file is not in %s", evidenceDir)
        .isEqualTo(evidenceDir);
    assertThat(finishedCaptureFile)
        .withFailMessage("This scenario's capture file does not exist after its capture finished")
        .exists()
        .hasFileName(finishedCaptureFile.getFileName().toString());
    assertThat(finishedCaptureFile.getFileName().toString()).endsWith(".pcapng");
    log.info("Found this scenario's pcap file: {}", finishedCaptureFile.getFileName());
  }

  @And("the pcapng file should contain at least one TCP packet")
  public void thePcapngFileShouldContainPackets() throws IOException {
    if (finishedCaptureFile == null) {
      log.warn("No finished capture file; pcap capture likely not enabled on this system");
      return;
    }

    long tcpPackets =
        readFrames(finishedCaptureFile).stream().filter(PcapCaptureStepDefs::isTcp).count();

    assertThat(tcpPackets)
        .withFailMessage(
            "Capture file %s has no TCP packet, although this scenario sent a request through the"
                + " proxy (%d packets in all)",
            finishedCaptureFile, finishedCapturePackets)
        .isPositive();
    log.info(
        "Pcapng file {} contains {} TCP packets", finishedCaptureFile.getFileName(), tcpPackets);
  }

  @And("the pcapng file should be valid pcapng format")
  public void thePcapngFileShouldBeValid() throws IOException {
    if (finishedCaptureFile == null) {
      log.warn("No finished capture file; pcap capture likely not enabled on this system");
      return;
    }

    byte[] fileStart = new byte[4];
    try (var input = Files.newInputStream(finishedCaptureFile)) {
      assertThat(input.read(fileStart))
          .withFailMessage("Pcapng file is too small to contain a header")
          .isEqualTo(4);
    }
    // pcapng's section header block type, which every pcapng file starts with
    int magic =
        ((fileStart[3] & 0xFF) << 24)
            | ((fileStart[2] & 0xFF) << 16)
            | ((fileStart[1] & 0xFF) << 8)
            | (fileStart[0] & 0xFF);
    assertThat(magic)
        .withFailMessage(
            "Capture file has invalid magic bytes: 0x%08X (expected 0x0A0D0D0A for pcapng)", magic)
        .isEqualTo(PCAPNG_SECTION_HEADER_MAGIC);

    // ... and it is well-formed all the way through, not just at its start.
    assertThat(readFrames(finishedCaptureFile)).hasSize(finishedCapturePackets);
    log.info("Pcapng file {} has valid format", finishedCaptureFile.getFileName());
  }

  @Then("the finished capture file should not have grown")
  public void theFinishedCaptureFileShouldNotHaveGrown() throws IOException {
    if (finishedCaptureFile == null) {
      log.warn("No finished capture file; pcap capture likely not enabled on this system");
      return;
    }

    assertThat(finishedCapturePackets)
        .withFailMessage(
            "The capture file %s had no packets when it was finished, so that nothing was added"
                + " to it afterwards proves nothing",
            finishedCaptureFile)
        .isPositive();
    assertThat(Files.size(finishedCaptureFile))
        .withFailMessage(
            "Capture file %s grew from %d to %d bytes after its capture was finished; later"
                + " traffic leaked into it",
            finishedCaptureFile, finishedCaptureBytes, Files.size(finishedCaptureFile))
        .isEqualTo(finishedCaptureBytes);
    assertThat(readFrames(finishedCaptureFile)).hasSize(finishedCapturePackets);
  }

  @Given("pcap capture is enabled but native pcap library is not available")
  public void pcapCaptureEnabledButLibUnavailable() {
    // Simulate "no native lib" without touching the shared suite-wide capture service: a fresh
    // ScenarioPcapCaptureService whose interface resolution always fails, exercising the exact same
    // startup-failure path (start() -> resolveCaptureInterface() -> PcapNativeException ->
    // handleStartupFailure()) that a real missing-Npcap/libpcap environment would hit.
    unavailableLibTestService =
        new ScenarioPcapCaptureService(64, 16 * KB) {
          @Override
          protected java.util.List<org.pcap4j.core.PcapNetworkInterface>
              resolveCaptureInterfaces() {
            return java.util.List.of();
          }
        };

    Logger serviceLogger = (Logger) LoggerFactory.getLogger(PcapCaptureEngine.class);
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
    Logger serviceLogger = (Logger) LoggerFactory.getLogger(PcapCaptureEngine.class);
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
    } catch (IOException e) {
      log.warn(
          "Could not send HTTP request through proxy on port {}: {}; continuing anyway",
          proxyPort,
          e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    // Give the capture thread a moment to write any generated packets
    try {
      Thread.sleep(300);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
  }

  /** A captured frame and the link type of the interface it came from. */
  private record Frame(int linkType, byte[] data) {}

  /** All frames of the pcapng file, in file order — read in pure Java, no native library. */
  private static List<Frame> readFrames(Path file) throws IOException {
    List<Frame> frames = new ArrayList<>();
    try (PcapNgFileReader reader = new PcapNgFileReader(file)) {
      for (PcapNgFileReader.Packet packet = reader.next(); packet != null; packet = reader.next()) {
        frames.add(new Frame(reader.interfaceOf(packet.interfaceId()).linkType(), packet.data()));
      }
    }
    return frames;
  }

  /**
   * Whether the frame holds an IPv4 or IPv6 TCP segment, whatever the link type of the interface it
   * was captured on: Ethernet on Linux, BSD loopback on Windows and macOS.
   */
  private static boolean isTcp(Frame frame) {
    int ipOffset;
    if (frame.linkType() == DataLinkType.EN10MB.value()) {
      ipOffset = 14;
    } else if (frame.linkType() == DataLinkType.NULL.value()) {
      ipOffset = 4;
    } else if (frame.linkType() == DataLinkType.RAW.value()) {
      ipOffset = 0;
    } else {
      return false;
    }
    byte[] data = frame.data();
    if (data.length <= ipOffset + 9) {
      return false;
    }
    int ipVersion = (data[ipOffset] & 0xF0) >> 4;
    if (ipVersion == 4) {
      return (data[ipOffset + 9] & 0xFF) == TCP; // IPv4 protocol field
    }
    return ipVersion == 6 && (data[ipOffset + 6] & 0xFF) == TCP; // IPv6 next header
  }
}
