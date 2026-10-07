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

import static de.gematik.rbellogger.util.MemoryConstants.KB;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpServer;
import de.gematik.test.tiger.common.data.config.tigerproxy.PcapCaptureConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.client.TigerRemoteProxyClient;
import de.gematik.test.tiger.proxy.controller.PcapAdminController;
import de.gematik.test.tiger.testutils.pcap.FakePcapNetwork;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PcapCaptureHandlerImpl")
class PcapCaptureHandlerImplTest {

  private static final String UNRESOLVABLE_INTERFACE = "definitely-not-a-real-interface";

  private final FakePcapNetwork network = new FakePcapNetwork();
  private FanOutPcapCaptureService engine;
  private TigerProxy tigerProxy;
  private TigerProxyConfiguration proxyConfig;
  private PcapCaptureHandlerImpl handler;

  @BeforeEach
  void setUp() {
    tigerProxy = mock(TigerProxy.class);
    proxyConfig = new TigerProxyConfiguration();
    when(tigerProxy.getTigerProxyConfiguration()).thenReturn(proxyConfig);
    when(tigerProxy.getProxyPort()).thenReturn(8080);
    when(tigerProxy.getAdminPort()).thenReturn(9000);
    when(tigerProxy.getRoutes()).thenReturn(List.of());
    // A proxy only accepts remote capture requests once it has opted in with a pcapCapture block.
    proxyConfig.setPcapCapture(PcapCaptureConfiguration.builder().build());
    // Capturing happens on a fake network: these tests must not depend on libpcap being installed
    // or on the permission to capture.
    handler =
        new PcapCaptureHandlerImpl(tigerProxy) {
          @Override
          protected FanOutPcapCaptureService newCaptureService() {
            engine = new FakeNetworkFanOutService(network);
            return engine;
          }
        };
  }

  @Test
  @DisplayName("an unresolvable configured interface name disables capture (proves config is read)")
  void unresolvableConfiguredInterfaceDisablesCapture() {
    proxyConfig.setPcapCapture(
        PcapCaptureConfiguration.builder()
            .interfaceNames(List.of("definitely-not-a-real-interface"))
            .build());

    PcapAdminController.CaptureStatusResponse result = handler.startCapture("test.pcapng", false);

    assertThat(result.isCapturing()).isFalse();
    assertThat(result.getError()).isNotNull();
  }

  @Test
  @DisplayName("a caller's filename cannot name a file outside the capture directory")
  void filenamesFromCallersAreMadeSafe() {
    assertThat(PcapCaptureHandlerImpl.plainFileName("scenario-1_ok.pcapng"))
        .isEqualTo("scenario-1_ok.pcapng");
    assertThat(PcapCaptureHandlerImpl.plainFileName("../../etc/passwd"))
        .isEqualTo(".._.._etc_passwd");
    assertThat(PcapCaptureHandlerImpl.plainFileName("..\\..\\evil.pcapng"))
        .isEqualTo(".._.._evil.pcapng");
    assertThat(PcapCaptureHandlerImpl.plainFileName("..")).isEqualTo("capture.pcapng");
    assertThat(PcapCaptureHandlerImpl.plainFileName(null)).isEqualTo("capture.pcapng");
  }

  @Test
  @DisplayName("settings requested by the caller win over this proxy's own configuration")
  void requestedConfigWinsOverProxyOwnConfig() {
    // The proxy's own config is valid (loopback default); only the requested one is unresolvable,
    // so capture can only fail if the requested settings were the ones actually used.
    proxyConfig.setPcapCapture(
        PcapCaptureConfiguration.builder()
            .allowedInterfaces(List.of("definitely-not-a-real-interface"))
            .build());
    PcapCaptureConfiguration requested =
        PcapCaptureConfiguration.builder()
            .interfaceNames(List.of("definitely-not-a-real-interface"))
            .build();

    PcapAdminController.CaptureStatusResponse result =
        handler.startCapture("test.pcapng", false, requested);

    assertThat(result.isCapturing()).isFalse();
    assertThat(result.getError()).isNotNull();
  }

  @Test
  @DisplayName("a capture with unusable settings fails without breaking the next one's own")
  void capturesAreConfiguredIndependently() {
    proxyConfig.setPcapCapture(
        PcapCaptureConfiguration.builder()
            .allowedInterfaces(List.of("definitely-not-a-real-interface"))
            .build());
    var unresolvable =
        PcapCaptureConfiguration.builder()
            .interfaceNames(List.of("definitely-not-a-real-interface"))
            .build();
    assertThat(handler.startCapture("bad.pcapng", false, unresolvable).isCapturing()).isFalse();

    PcapAdminController.CaptureStatusResponse next =
        handler.startCapture("next.pcapng", false, PcapCaptureConfiguration.builder().build());
    assertThat(next.isCapturing()).isTrue();
    assertThat(handler.stopCapture(next.getCaptureId()).getError()).isNull();
  }

  @Test
  @DisplayName("a caller may not capture on an interface its proxy's operator did not allow")
  void interfaceNotAllowedIsRefused() {
    // The proxy's own block says nothing about interfaces: loopback only.
    PcapCaptureConfiguration requested =
        PcapCaptureConfiguration.builder().interfaceNames(List.of("eth0")).build();

    assertThatThrownBy(() -> handler.startCapture("test.pcapng", false, requested))
        .isInstanceOf(PcapCaptureNotAllowedException.class)
        .hasMessageContaining("eth0")
        .hasMessageContaining("loopback only");
  }

  @Test
  @DisplayName("asking for no interface, i.e. loopback, is always allowed")
  void loopbackIsAlwaysAllowed() {
    var started = new AtomicReference<PcapAdminController.CaptureStatusResponse>();

    // A refusal by the policy is an exception, so not throwing is what "allowed" means here.
    assertThatCode(
            () ->
                started.set(
                    handler.startCapture(
                        "test.pcapng", false, PcapCaptureConfiguration.builder().build())))
        .doesNotThrowAnyException();

    PcapAdminController.CaptureStatusResponse response = started.get();
    assertThat(response.isCapturing()).isTrue();
    assertThat(response.getError()).isNull();
    assertThat(handler.stopCapture(response.getCaptureId()).getError()).isNull();
  }

  @Test
  @DisplayName("naming an interface in the proxy's own block is allowing it")
  void interfaceNamedInOwnBlockIsAllowed() {
    // (Names that can't be resolved: the request passes the policy but opens no real capture.)
    String second = UNRESOLVABLE_INTERFACE + "-2";
    proxyConfig.setPcapCapture(
        PcapCaptureConfiguration.builder()
            .interfaceNames(List.of(UNRESOLVABLE_INTERFACE, second))
            .build());
    PcapCaptureConfiguration requested =
        PcapCaptureConfiguration.builder().interfaceNames(List.of(second)).build();

    assertThatCode(() -> handler.startCapture("test.pcapng", false, requested))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("allowedInterfaces, when set, is what counts")
  void allowedInterfacesOverridesOwnInterfaceNames() {
    proxyConfig.setPcapCapture(
        PcapCaptureConfiguration.builder()
            .interfaceNames(List.of("eth0"))
            .allowedInterfaces(List.of("docker0"))
            .build());

    val config = PcapCaptureConfiguration.builder().interfaceNames(List.of("eth0")).build();
    assertThatThrownBy(() -> handler.startCapture("test.pcapng", false, config))
        .isInstanceOf(PcapCaptureNotAllowedException.class);
  }

  @Test
  @DisplayName("any interface, \"*\" included, needs an explicit \"*\" in allowedInterfaces")
  void anyInterfaceNeedsExplicitWildcard() {
    PcapCaptureConfiguration wildcard =
        PcapCaptureConfiguration.builder().interfaceNames(List.of("*")).build();
    assertThatThrownBy(() -> handler.startCapture("test.pcapng", false, wildcard))
        .isInstanceOf(PcapCaptureNotAllowedException.class);

    proxyConfig.setPcapCapture(
        PcapCaptureConfiguration.builder().allowedInterfaces(List.of("*")).build());
    PcapCaptureConfiguration named =
        PcapCaptureConfiguration.builder().interfaceNames(List.of("anything-at-all")).build();

    assertThatCode(() -> handler.startCapture("test.pcapng", false, named))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("a caller's own capture filter is refused unless the operator allows it")
  void customFilterIsRefusedByDefault() {
    PcapCaptureConfiguration requested =
        PcapCaptureConfiguration.builder().bpfFilter("tcp").build();

    assertThatThrownBy(() -> handler.startCapture("test.pcapng", false, requested))
        .isInstanceOf(PcapCaptureNotAllowedException.class)
        .hasMessageContaining("allowCustomFilter");
  }

  @Test
  @DisplayName("a filter equal to the proxy's own is accepted, any filter with allowCustomFilter")
  void customFilterAllowedWhenEqualOrPermitted() {
    // The interface can't be resolved, so a request that passes the policy fails to open (an error
    // response) instead of starting a real capture — what matters here is that it is not refused.
    proxyConfig.setPcapCapture(
        PcapCaptureConfiguration.builder()
            .bpfFilter("tcp port 8080")
            .allowedInterfaces(List.of(UNRESOLVABLE_INTERFACE))
            .build());
    assertThatCode(() -> handler.startCapture("test.pcapng", false, requesting(" tcp port 8080 ")))
        .as("the same filter the operator configured")
        .doesNotThrowAnyException();
    val tcpRequestConfig = requesting("tcp");
    assertThatThrownBy(() -> handler.startCapture("test.pcapng", false, tcpRequestConfig))
        .isInstanceOf(PcapCaptureNotAllowedException.class);

    proxyConfig.setPcapCapture(
        PcapCaptureConfiguration.builder()
            .allowCustomFilter(true)
            .allowedInterfaces(List.of(UNRESOLVABLE_INTERFACE))
            .build());
    assertThatCode(() -> handler.startCapture("test.pcapng", false, tcpRequestConfig))
        .as("any filter, once the operator allows custom ones")
        .doesNotThrowAnyException();
  }

  private static PcapCaptureConfiguration requesting(String filter) {
    return PcapCaptureConfiguration.builder()
        .interfaceNames(List.of(UNRESOLVABLE_INTERFACE))
        .bpfFilter(filter)
        .build();
  }

  @Test
  @DisplayName("a caller can not have this proxy take more than 256 MB of kernel buffer")
  void outsizedBufferIsRefused() {
    PcapCaptureConfiguration tooBig =
        PcapCaptureConfiguration.builder().bufferSizeKb(257 * KB).build();
    assertThatThrownBy(() -> handler.startCapture("test.pcapng", false, tooBig))
        .isInstanceOf(PcapCaptureNotAllowedException.class)
        .hasMessageContaining("256 MB");

    // An unresolvable interface keeps this from opening a real capture with a buffer that size.
    proxyConfig.setPcapCapture(
        PcapCaptureConfiguration.builder()
            .allowedInterfaces(List.of(UNRESOLVABLE_INTERFACE))
            .build());
    PcapCaptureConfiguration atTheLimit =
        PcapCaptureConfiguration.builder()
            .interfaceNames(List.of(UNRESOLVABLE_INTERFACE))
            .bufferSizeKb(256 * KB)
            .build();
    assertThatCode(() -> handler.startCapture("test.pcapng", false, atTheLimit))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("a buffer the operator configured for the proxy itself is never too much to ask")
  void operatorsOwnBufferSizeIsNotRefused() {
    proxyConfig.setPcapCapture(
        PcapCaptureConfiguration.builder()
            .bufferSizeKb(512 * KB)
            .allowedInterfaces(List.of(UNRESOLVABLE_INTERFACE))
            .build());
    PcapCaptureConfiguration sameAsOwn =
        PcapCaptureConfiguration.builder()
            .interfaceNames(List.of(UNRESOLVABLE_INTERFACE))
            .bufferSizeKb(512 * KB)
            .build();

    assertThatCode(() -> handler.startCapture("test.pcapng", false, sameAsOwn))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("policy fields sent by a caller change nothing: only the proxy's own block counts")
  void policyFieldsInARequestAreIgnored() {
    PcapCaptureConfiguration cheating =
        PcapCaptureConfiguration.builder()
            .interfaceNames(List.of("eth0"))
            .allowedInterfaces(List.of("eth0"))
            .build();

    assertThatThrownBy(() -> handler.startCapture("test.pcapng", false, cheating))
        .isInstanceOf(PcapCaptureNotAllowedException.class);
  }

  @Test
  @DisplayName("a proxy without a pcapCapture block refuses to start a capture")
  void proxyWithoutPcapCaptureBlockRefusesCapture() {
    proxyConfig.setPcapCapture(null);

    assertThatThrownBy(() -> handler.startCapture("test.pcapng", false))
        .isInstanceOf(PcapCaptureNotAllowedException.class)
        .hasMessageContaining("pcapCapture");
  }

  @Test
  @DisplayName("an empty pcapCapture block is enough to opt in, and falls back to the defaults")
  void emptyPcapCaptureBlockOptsIn() {
    proxyConfig.setPcapCapture(PcapCaptureConfiguration.builder().build());

    assertThatCode(() -> handler.startCapture("test.pcapng", false)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("a downloaded file is discarded: forgotten and deleted from disk")
  void discardedFileIsForgottenAndDeleted() {
    PcapAdminController.CaptureStatusResponse started =
        handler.startCapture("discard.pcapng", false);
    assertThat(started.isCapturing()).isTrue();
    handler.stopCapture(started.getCaptureId());
    java.io.File file = handler.getPcapFile(started.getCaptureId());
    assertThat(file).as("a stopped capture's file is kept for download").exists();

    handler.discardPcapFile(started.getCaptureId());

    assertThat(handler.getPcapFile(started.getCaptureId())).isNull();
    assertThat(file).doesNotExist();
  }

  @Test
  @DisplayName("a stopped capture's file that is never downloaded is deleted after the retention")
  void undownloadedFileIsPurgedAfterTheRetention() throws Exception {
    PcapAdminController.CaptureStatusResponse started = handler.startCapture("purge.pcapng", false);
    assertThat(started.isCapturing()).isTrue();
    handler.stopCapture(started.getCaptureId());
    java.io.File file = handler.getPcapFile(started.getCaptureId());
    assertThat(file).exists();

    // Fresh files stay: starting and stopping another capture purges nothing yet.
    stopCapturing(handler.startCapture("fresh.pcapng", false));
    assertThat(file).as("within the retention").exists();

    handler.setClosedFileRetention(Duration.ZERO);
    Thread.sleep(20);
    stopCapturing(handler.startCapture("later.pcapng", false)); // any start purges stale files

    assertThat(handler.getPcapFile(started.getCaptureId())).isNull();
    assertThat(file).as("past the retention").doesNotExist();
  }

  private void stopCapturing(PcapAdminController.CaptureStatusResponse started) {
    assertThat(started.isCapturing()).isTrue();
    handler.stopCapture(started.getCaptureId());
  }

  @Test
  @DisplayName("discarding an unknown capture's file is a no-op")
  void discardingUnknownFileIsNoop() {
    assertThatCode(() -> handler.discardPcapFile("never-started")).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("a capture is known while it is open and until its closed file is discarded")
  void capturesAreKnownUntilTheirFileIsDiscarded() {
    assertThat(handler.isKnownCapture("never-started")).isFalse();
    assertThat(handler.isKnownCapture(null)).isFalse();

    String id = handler.startCapture("mine.pcapng", false).getCaptureId();
    assertThat(handler.isKnownCapture(id)).isTrue();

    handler.stopCapture(id);
    assertThat(handler.isKnownCapture(id)).as("its file is still there").isTrue();

    handler.discardPcapFile(id);
    assertThat(handler.isKnownCapture(id)).as("nothing left").isFalse();
  }

  @Test
  @DisplayName("a slow downstream proxy does not stall other captures of this proxy")
  void slowDownstreamRelayDoesNotBlockOtherCaptures() throws Exception {
    when(tigerProxy.getRemoteProxyClients()).thenReturn(List.of());
    PcapAdminController.CaptureStatusResponse first = handler.startCapture("first.pcapng", false);
    assertThat(first.isCapturing()).isTrue();

    java.util.concurrent.CountDownLatch relayReached = new java.util.concurrent.CountDownLatch(1);
    oldProxyStub = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    oldProxyStub.createContext(
        "/",
        exchange -> {
          relayReached.countDown();
          try {
            Thread.sleep(2500); // a downstream proxy that takes its time to answer
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          exchange.sendResponseHeaders(404, -1);
          exchange.close();
        });
    oldProxyStub.start();
    TigerRemoteProxyClient slow = mock(TigerRemoteProxyClient.class);
    when(slow.getRemoteProxyUrl())
        .thenReturn("http://localhost:" + oldProxyStub.getAddress().getPort());
    when(slow.getRemoteClockOffset()).thenReturn(Duration.ZERO);
    when(tigerProxy.getRemoteProxyClients()).thenReturn(List.of(slow));

    Thread secondStart = new Thread(() -> handler.startCapture("second.pcapng", false));
    secondStart.start();
    assertThat(relayReached.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();

    long began = System.nanoTime();
    // Meanwhile the first, unrelated capture is stopped: this must not wait for the slow relay.
    // (Its own downstream list was empty when it started.)
    handler.stopCapture(first.getCaptureId());
    long tookMillis = Duration.ofNanos(System.nanoTime() - began).toMillis();

    assertThat(tookMillis)
        .as("stopping an unrelated capture while a relay is slow")
        .isLessThan(1500);
    secondStart.join();
  }

  @Test
  @DisplayName("stopping an unknown capture returns an error")
  void stoppingUnknownCaptureReturnsError() {
    PcapAdminController.CaptureStatusResponse result = handler.stopCapture("never-started");

    assertThat(result.isCapturing()).isFalse();
    assertThat(result.getError()).contains("Unknown or already-closed capture");
  }

  @Test
  @DisplayName("suspending/resuming an unknown capture returns an error")
  void suspendResumeUnknownCaptureReturnsError() {
    assertThat(handler.suspendCapture("never-started").getError()).isNotNull();
    assertThat(handler.resumeCapture("never-started").getError()).isNotNull();
  }

  @Test
  @DisplayName("status of an unknown capture reports not capturing")
  void statusOfUnknownCaptureReportsNotCapturing() {
    assertThat(handler.getStatus("never-started").isCapturing()).isFalse();
  }

  @Test
  @DisplayName("getPcapFile() for an unknown capture is null")
  void getPcapFileForUnknownCaptureIsNull() {
    assertThat(handler.getPcapFile("never-started")).isNull();
  }

  @Test
  @DisplayName("a leaf proxy (no downstream remote clients) starts/stops normally, no relay")
  void leafProxyStartsAndStopsNormally() {
    when(tigerProxy.getRemoteProxyClients()).thenReturn(List.of());

    PcapAdminController.CaptureStatusResponse started = handler.startCapture("leaf.pcapng", false);
    assertThat(started.isCapturing()).isTrue();

    PcapAdminController.CaptureStatusResponse stopped = handler.stopCapture(started.getCaptureId());
    assertThat(stopped.getError()).isNull();
  }

  @Test
  @DisplayName("a downstream relay that fails to start doesn't fail the local capture")
  void failingDownstreamRelayDoesNotFailLocalCapture() {
    TigerRemoteProxyClient unreachable = mock(TigerRemoteProxyClient.class);
    when(unreachable.getRemoteProxyUrl()).thenReturn("http://localhost:1");
    when(unreachable.getRemoteClockOffset()).thenReturn(Duration.ZERO);
    when(tigerProxy.getRemoteProxyClients()).thenReturn(List.of(unreachable));

    PcapAdminController.CaptureStatusResponse started =
        handler.startCapture("with-bad-downstream.pcapng", false);
    assertThat(started.isCapturing()).isTrue();

    PcapAdminController.CaptureStatusResponse stopped = handler.stopCapture(started.getCaptureId());
    assertThat(stopped.getError()).isNull();
    assertThat(stopped.isCapturing()).isFalse();
  }

  private HttpServer oldProxyStub;

  @AfterEach
  void stopOldProxyStub() {
    if (oldProxyStub != null) {
      oldProxyStub.stop(0);
      oldProxyStub = null;
    }
    // A test that leaves a capture open must not leave a capture thread behind.
    if (engine != null) {
      engine.stop();
    }
  }

  @Test
  @DisplayName(
      "a downstream proxy without the pcap admin API (404) is skipped, local capture still works")
  void oldDownstreamProxyWithoutPcapEndpointIsSkipped() throws Exception {
    // Simulates an old proxy that predates this feature: a real HTTP server that 404s on
    // /pcap/admin/start, as opposed to the unreachable-host case covered by
    // failingDownstreamRelayDoesNotFailLocalCapture above.
    oldProxyStub = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    oldProxyStub.createContext(
        "/",
        exchange -> {
          exchange.sendResponseHeaders(404, -1);
          exchange.close();
        });
    oldProxyStub.start();
    int port = oldProxyStub.getAddress().getPort();

    TigerRemoteProxyClient oldProxy = mock(TigerRemoteProxyClient.class);
    when(oldProxy.getRemoteProxyUrl()).thenReturn("http://localhost:" + port);
    when(oldProxy.getRemoteClockOffset()).thenReturn(Duration.ZERO);
    when(tigerProxy.getRemoteProxyClients()).thenReturn(List.of(oldProxy));

    PcapAdminController.CaptureStatusResponse started =
        handler.startCapture("with-old-downstream.pcapng", false);
    assertThat(started.isCapturing()).isTrue();

    PcapAdminController.CaptureStatusResponse stopped = handler.stopCapture(started.getCaptureId());
    assertThat(stopped.getError()).isNull();
    assertThat(stopped.isCapturing()).isFalse();
  }
}
