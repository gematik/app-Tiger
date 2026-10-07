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
package de.gematik.test.tiger.proxy.handler.pcap;

import static de.gematik.test.tiger.proxy.handler.pcap.PcapFiles.frame;
import static de.gematik.test.tiger.proxy.handler.pcap.PcapFiles.interfaceNamesIn;
import static de.gematik.test.tiger.proxy.handler.pcap.PcapFiles.markersIn;
import static de.gematik.test.tiger.testutils.pcap.FakePcapNetwork.LOOPBACK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.gematik.test.tiger.common.data.config.tigerproxy.PcapCaptureConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.client.TigerRemoteProxyClient;
import de.gematik.test.tiger.proxy.controller.PcapAdminController.CaptureStatusResponse;
import de.gematik.test.tiger.testutils.pcap.FakePcapNetwork;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("PcapCaptureHandlerImpl on a fake network")
class PcapCaptureHandlerImplOnFakeNetworkTest {

  private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

  @TempDir private Path tempDir;
  private final FakePcapNetwork network = new FakePcapNetwork();
  private final List<HttpServer> servers = new CopyOnWriteArrayList<>();
  private TigerProxy tigerProxy;
  private TigerProxyConfiguration proxyConfig;
  private FanOutPcapCaptureService engine;
  private PcapCaptureHandlerImpl handler;

  @BeforeEach
  void setUp() {
    tigerProxy = mock(TigerProxy.class);
    proxyConfig = new TigerProxyConfiguration();
    when(tigerProxy.getTigerProxyConfiguration()).thenReturn(proxyConfig);
    when(tigerProxy.getProxyPort()).thenReturn(8080);
    when(tigerProxy.getAdminPort()).thenReturn(9000);
    when(tigerProxy.getRoutes()).thenReturn(List.of());
    proxyConfig.setPcapCapture(PcapCaptureConfiguration.builder().build());
    handler = handlerOver(() -> new FakeNetworkFanOutService(network));
  }

  @AfterEach
  void tearDown() {
    if (engine != null) {
      engine.stop();
    }
    servers.forEach(server -> server.stop(0));
  }

  private PcapCaptureHandlerImpl handlerOver(
      java.util.function.Supplier<FanOutPcapCaptureService> engineFactory) {
    return new PcapCaptureHandlerImpl(tigerProxy) {
      @Override
      protected FanOutPcapCaptureService newCaptureService() {
        engine = engineFactory.get();
        return engine;
      }
    };
  }

  private CaptureStatusResponse start(String filename) {
    CaptureStatusResponse started = handler.startCapture(filename, false);
    assertThat(started.isCapturing()).as("start failed: %s", started.getError()).isTrue();
    return started;
  }

  private void deliver(int marker, Instant at) {
    network.deliver(LOOPBACK, frame(marker), at);
    network.awaitProcessed(LOOPBACK);
  }

  // ===== one proxy, no downstream =====

  @Test
  @DisplayName("a capture records what it sees and hands the file out when stopped")
  void captureRecordsAndHandsOutItsFile() throws Exception {
    CaptureStatusResponse started = start("scenario.pcapng");
    String id = started.getCaptureId();
    assertThat(started.getDownloadUrl()).as("only a stopped capture has one").isNull();

    deliver(1, T0);
    deliver(2, T0.plusMillis(10));
    CaptureStatusResponse status = handler.getStatus(id);
    assertThat(status.isCapturing()).isTrue();
    assertThat(status.getPacketCount()).isEqualTo(2);
    assertThat(status.getFilename()).isEqualTo("scenario.pcapng");

    CaptureStatusResponse stopped = handler.stopCapture(id);

    assertThat(stopped.getError()).isNull();
    assertThat(stopped.isCapturing()).isFalse();
    assertThat(stopped.getPacketCount()).isEqualTo(2);
    assertThat(stopped.getFileSizeBytes()).isPositive();
    assertThat(stopped.getDownloadUrl()).isEqualTo("/pcap/admin/download?captureId=" + id);
    assertThat(stopped.getStartTime()).isBeforeOrEqualTo(stopped.getStopTime());
    File file = handler.getPcapFile(id);
    assertThat(file.getName()).endsWith("_scenario.pcapng");
    assertThat(markersIn(file.toPath())).containsExactly(1, 2);
    assertThat(handler.getStatus(id).isCapturing()).as("closed").isFalse();
    assertThat(handler.stopCapture(id).getError()).as("stopped twice").isNotNull();
  }

  @Test
  @DisplayName("what arrives while a capture is suspended is not recorded")
  void suspendedCaptureRecordsNothing() throws Exception {
    String id = start("suspend.pcapng").getCaptureId();

    CaptureStatusResponse suspended = handler.suspendCapture(id);
    deliver(1, T0);
    CaptureStatusResponse resumed = handler.resumeCapture(id);
    deliver(2, T0.plusMillis(10));
    handler.stopCapture(id);

    assertThat(suspended.getError()).isNull();
    assertThat(suspended.getFilename()).isEqualTo("suspend.pcapng");
    assertThat(resumed.getError()).isNull();
    assertThat(markersIn(handler.getPcapFile(id).toPath())).containsExactly(2);
  }

  @Test
  @DisplayName("a gap capture only records while no scenario of its own client is running")
  void gapCaptureYieldsToItsClientsScenarios() throws Exception {
    CaptureStatusResponse gap = handler.startCapture("gap.pcapng", true, null, "suite");
    deliver(1, T0);
    CaptureStatusResponse scenario = handler.startCapture("scenario.pcapng", false, null, "suite");
    deliver(2, T0.plusMillis(10));
    handler.stopCapture(scenario.getCaptureId());
    deliver(3, T0.plusMillis(20));
    handler.stopCapture(gap.getCaptureId());

    assertThat(markersIn(handler.getPcapFile(gap.getCaptureId()).toPath())).containsExactly(1, 3);
    assertThat(markersIn(handler.getPcapFile(scenario.getCaptureId()).toPath())).containsExactly(2);
  }

  @Test
  @DisplayName("the engine is shared by all captures and ends with the last one")
  void engineIsSharedAndEndsWithItsLastCapture() {
    String first = start("first.pcapng").getCaptureId();
    String second = start("second.pcapng").getCaptureId();
    assertThat(network.opened()).as("one handle for both captures").hasSize(1);

    handler.stopCapture(first);
    assertThat(network.isOpen(LOOPBACK)).as("the second one still needs it").isTrue();

    handler.stopCapture(second);
    assertThat(network.isOpen(LOOPBACK)).isFalse();
    assertThat(engine.isEnabled()).isFalse();
  }

  @Test
  @DisplayName("ports of a later capture's proxy state widen what earlier captures capture")
  void laterCapturesWidenThePortFilters() {
    start("first.pcapng");
    when(tigerProxy.getProxyPort()).thenReturn(8081);

    start("second.pcapng");

    assertThat(network.kernelFilters(LOOPBACK)).last().asString().contains("8080").contains("8081");
  }

  @Test
  @DisplayName("a capture that cannot be opened is reported, and the engine ends with it")
  void captureThatCannotBeOpenedIsReported() {
    network.failToOpen(LOOPBACK);

    CaptureStatusResponse response = handler.startCapture("fails.pcapng", false);

    assertThat(response.isCapturing()).isFalse();
    assertThat(response.getError()).contains("Failed to open pcap capture");
    assertThat(engine.isEnabled()).isFalse();
  }

  @Test
  @DisplayName("an unexpected failure while starting is reported as the error")
  void unexpectedFailureWhileStartingIsReported() {
    handler =
        handlerOver(
            () ->
                new FakeNetworkFanOutService(network) {
                  @Override
                  public synchronized void alsoCapturePorts(java.util.Set<Integer> newPorts) {
                    throw new IllegalStateException("boom");
                  }
                });
    start("first.pcapng");

    CaptureStatusResponse response = handler.startCapture("second.pcapng", false);

    assertThat(response.isCapturing()).isFalse();
    assertThat(response.getError())
        .startsWith("Failed to start capture:")
        .contains("boom")
        .contains("\tat ");
  }

  @Test
  @DisplayName("a capture whose engine fails while closing is reported as not properly closed")
  void failureWhileClosingIsReported() {
    handler =
        handlerOver(
            () ->
                new FakeNetworkFanOutService(network) {
                  @Override
                  public synchronized long closeCapture(String captureId) {
                    throw new IllegalStateException("disk gone");
                  }
                });
    String id = start("broken.pcapng").getCaptureId();

    CaptureStatusResponse stopped = handler.stopCapture(id);

    assertThat(stopped.isCapturing()).isFalse();
    assertThat(stopped.getError())
        .startsWith("Failed to properly close capture:")
        .contains("\tat ");
    assertThat(handler.getPcapFile(id)).as("nothing to hand out").isNull();
  }

  @Test
  @DisplayName("a capture whose file is gone when it is closed is reported as not properly closed")
  void missingFileWhileClosingIsReported() {
    handler =
        handlerOver(
            () ->
                new FakeNetworkFanOutService(network) {
                  @Override
                  public synchronized long closeCapture(String captureId) {
                    long packets = super.closeCapture(captureId);
                    try (var files =
                        Files.list(PcapCaptureHandlerImplOnFakeNetworkTest.this.captureDir())) {
                      files
                          .filter(file -> file.getFileName().toString().endsWith("_gone.pcapng"))
                          .forEach(file -> file.toFile().delete());
                    } catch (IOException e) {
                      throw new IllegalStateException(e);
                    }
                    return packets;
                  }
                });
    String id = start("gone.pcapng").getCaptureId();

    CaptureStatusResponse stopped = handler.stopCapture(id);

    assertThat(stopped.getError()).isEqualTo("Failed to properly close capture");
  }

  Path captureDir() {
    return Path.of(System.getProperty("java.io.tmpdir"), "tiger-pcap-captures");
  }

  // ===== relaying to a downstream proxy =====

  private final class DownstreamProxy {
    static final String CAPTURE_ID = "downstream-capture";

    final List<String> calls = new CopyOnWriteArrayList<>();
    final String url;
    private final HttpServer server;
    volatile int stopStatus = 200;
    volatile byte[] capture;
    volatile CountDownLatch stopRendezvous;

    DownstreamProxy(byte[] capture) throws IOException {
      this.capture = capture;
      server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      server.createContext(
          "/pcap/admin/start",
          exchange -> {
            calls.add(
                "start "
                    + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"capturing\":true,\"captureId\":\"" + CAPTURE_ID + "\"}");
          });
      for (String operation : List.of("suspend", "resume")) {
        server.createContext(
            "/pcap/admin/" + operation,
            exchange -> {
              if (captureIsNotTheCallers(exchange)) {
                return;
              }
              calls.add(operation);
              respond(exchange, 200, "{\"capturing\":true}");
            });
      }
      server.createContext(
          "/pcap/admin/stop",
          exchange -> {
            if (captureIsNotTheCallers(exchange)) {
              return;
            }
            calls.add("stop");
            CountDownLatch rendezvous = stopRendezvous;
            if (rendezvous != null) {
              rendezvous.countDown();
              try {
                if (rendezvous.await(5, TimeUnit.SECONDS)) {
                  calls.add("stop overlapped");
                }
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            }
            if (stopStatus != 200) {
              exchange.sendResponseHeaders(stopStatus, -1);
              exchange.close();
              return;
            }
            respond(
                exchange,
                200,
                "{\"capturing\":false,\"downloadUrl\":\"/pcap/admin/download?captureId="
                    + CAPTURE_ID
                    + "\"}");
          });
      server.createContext(
          "/pcap/admin/download",
          exchange -> {
            if (captureIsNotTheCallers(exchange)) {
              return;
            }
            calls.add("download");
            exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
            exchange.sendResponseHeaders(200, this.capture.length == 0 ? -1 : this.capture.length);
            if (this.capture.length > 0) {
              exchange.getResponseBody().write(this.capture);
            }
            exchange.close();
          });
      server.start();
      servers.add(server);
      url = "http://localhost:" + server.getAddress().getPort();
    }

    private boolean captureIsNotTheCallers(HttpExchange exchange) throws IOException {
      boolean theirs = exchange.getRequestURI().getQuery().contains("captureId=" + CAPTURE_ID);
      if (!theirs) {
        calls.add("refused " + exchange.getRequestURI().getPath());
        exchange.sendResponseHeaders(400, -1);
        exchange.close();
      }
      return !theirs;
    }

    private void respond(HttpExchange exchange, int status, String json) throws IOException {
      byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(status, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    }
  }

  private void relayTo(DownstreamProxy downstream, Duration clockOffset) {
    relayTo(clockOffset, downstream);
  }

  private void relayTo(Duration clockOffset, DownstreamProxy... downstreams) {
    List<TigerRemoteProxyClient> clients = new ArrayList<>();
    for (DownstreamProxy downstream : downstreams) {
      TigerRemoteProxyClient client = mock(TigerRemoteProxyClient.class);
      when(client.getRemoteProxyUrl()).thenReturn(downstream.url);
      when(client.getRemoteClockOffset()).thenReturn(clockOffset);
      clients.add(client);
    }
    when(tigerProxy.getRemoteProxyClients()).thenReturn(clients);
  }

  private byte[] downstreamCapture(int marker, Instant at) throws IOException {
    Path file = PcapFiles.writeCapture(tempDir.resolve("downstream.pcapng"), "eth0", marker, at);
    return Files.readAllBytes(file);
  }

  @Test
  @DisplayName("a capture is relayed to a downstream proxy and its capture merged into the file")
  void relayedCaptureIsMergedIntoTheFile() throws Exception {
    DownstreamProxy downstream = new DownstreamProxy(downstreamCapture(2, T0.plusSeconds(3)));
    // The downstream clock is one second ahead: what it stamped at +3 s happened at +2 s.
    relayTo(downstream, Duration.ofSeconds(1));
    CaptureStatusResponse started = handler.startCapture("relay.pcapng", false, null, "suite-7");
    String id = started.getCaptureId();
    assertThat(started.isCapturing()).isTrue();
    assertThat(downstream.calls)
        .singleElement()
        .asString()
        .startsWith("start ")
        .contains("\"suiteId\":\"suite-7\"")
        .contains("\"filename\":\"relay.pcapng\"");

    assertThat(handler.suspendCapture(id).getError()).isNull();
    assertThat(handler.resumeCapture(id).getError()).isNull();
    deliver(1, T0.plusMillis(2500));
    CaptureStatusResponse stopped = handler.stopCapture(id);

    assertThat(downstream.calls)
        .as("each call was about the downstream capture")
        .containsExactly(downstream.calls.get(0), "suspend", "resume", "stop", "download");
    assertThat(stopped.getError()).isNull();
    assertThat(stopped.getPacketCount()).isEqualTo(2);
    Path merged = handler.getPcapFile(id).toPath();
    assertThat(markersIn(merged))
        .as("the downstream packet, clock-corrected, is first")
        .containsExactly(2, 1);
    assertThat(interfaceNamesIn(merged)).hasSize(2);
  }

  @Test
  @DisplayName("downstream proxies are stopped at the same time")
  void downstreamProxiesAreStoppedConcurrently() throws Exception {
    DownstreamProxy first = new DownstreamProxy(downstreamCapture(2, T0.plusSeconds(1)));
    DownstreamProxy second = new DownstreamProxy(downstreamCapture(3, T0.plusSeconds(2)));
    CountDownLatch bothStopping = new CountDownLatch(2);
    first.stopRendezvous = bothStopping;
    second.stopRendezvous = bothStopping;
    relayTo(Duration.ZERO, first, second);
    String id = start("relay.pcapng").getCaptureId();
    deliver(1, T0);

    handler.stopCapture(id);

    assertThat(first.calls).contains("stop overlapped");
    assertThat(second.calls).contains("stop overlapped");
  }

  @Test
  @DisplayName("a downstream proxy that cannot stop its capture is left out of the file")
  void downstreamThatCannotBeStoppedIsLeftOut() throws Exception {
    DownstreamProxy downstream = new DownstreamProxy(downstreamCapture(2, T0.plusSeconds(3)));
    downstream.stopStatus = 500;
    relayTo(downstream, Duration.ZERO);
    String id = start("relay.pcapng").getCaptureId();
    deliver(1, T0);

    CaptureStatusResponse stopped = handler.stopCapture(id);

    assertThat(stopped.getError()).isNull();
    assertThat(downstream.calls).doesNotContain("download");
    assertThat(markersIn(handler.getPcapFile(id).toPath())).containsExactly(1);
  }

  @Test
  @DisplayName("an empty or broken capture from a downstream proxy is left out of the file")
  void emptyOrBrokenDownstreamCaptureIsLeftOut() throws Exception {
    DownstreamProxy downstream = new DownstreamProxy(new byte[0]);
    relayTo(downstream, Duration.ZERO);
    String empty = start("empty.pcapng").getCaptureId();
    deliver(1, T0);
    handler.stopCapture(empty);
    assertThat(markersIn(handler.getPcapFile(empty).toPath())).containsExactly(1);

    downstream.capture = "this is not a pcapng file".getBytes(StandardCharsets.UTF_8);
    String broken = start("broken.pcapng").getCaptureId();
    deliver(2, T0.plusMillis(10));
    CaptureStatusResponse stopped = handler.stopCapture(broken);

    assertThat(stopped.getError()).isNull();
    assertThat(markersIn(handler.getPcapFile(broken).toPath())).containsExactly(2);
  }
}
