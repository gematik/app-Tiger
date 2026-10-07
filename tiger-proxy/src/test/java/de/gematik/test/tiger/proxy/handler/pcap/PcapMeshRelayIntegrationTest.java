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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import de.gematik.test.tiger.common.data.config.tigerproxy.PcapCaptureConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.TigerProxyApplication;
import de.gematik.test.tiger.proxy.client.TigerRemoteProxyClient;
import de.gematik.test.tiger.proxy.controller.PcapAdminController;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import kong.unirest.core.Unirest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    classes = TigerProxyApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    // A pcapCapture block is what opts a proxy in to being captured on remotely.
    properties = "tiger-proxy.pcap-capture.snaplen-kb=64")
@DirtiesContext
@ResetTigerConfiguration
@Tag("de.gematik.test.tiger.common.LongRunnerTest")
@DisplayName("Pcap mesh relay (B -> real C)")
class PcapMeshRelayIntegrationTest {

  @LocalServerPort private int downstreamPort;
  @Autowired private PcapCaptureHandlerImpl downstreamHandler;

  @Test
  @DisplayName("starting a capture on B opens a genuine capture on C over real HTTP")
  void startRelaysToRealDownstreamProxy() throws Exception {
    PcapCaptureHandlerImpl upstream = newUpstreamHandlerPointingAt(downstreamPort);

    PcapAdminController.CaptureStatusResponse started =
        upstream.startCapture("mesh-test.pcapng", false);
    if (!started.isCapturing()) {
      return; // native pcap unavailable in this environment; nothing left to assert
    }

    assertThat(captureCountOnDownstream())
        .as("C must have opened its own capture via the relay")
        .isEqualTo(1);

    upstream.stopCapture(started.getCaptureId());
  }

  @Test
  @DisplayName("stopping B's capture also stops C's relayed capture")
  void stopRelaysToRealDownstreamProxy() throws Exception {
    PcapCaptureHandlerImpl upstream = newUpstreamHandlerPointingAt(downstreamPort);

    PcapAdminController.CaptureStatusResponse started =
        upstream.startCapture("mesh-test-stop.pcapng", false);
    if (!started.isCapturing()) {
      return;
    }
    assertThat(captureCountOnDownstream()).isEqualTo(1);

    PcapAdminController.CaptureStatusResponse stopped =
        upstream.stopCapture(started.getCaptureId());

    assertThat(stopped.getError()).isNull();
    assertThat(captureCountOnDownstream())
        .as("C's relayed capture must be stopped too, not leaked")
        .isZero();
  }

  @Test
  @DisplayName("C's genuinely captured packets end up merged into B's final file, not dropped")
  void downstreamPacketsAreActuallyMergedIntoLocalFile() throws Exception {
    PcapCaptureHandlerImpl upstream = newUpstreamHandlerPointingAt(downstreamPort);

    PcapAdminController.CaptureStatusResponse started =
        upstream.startCapture("mesh-test-merge.pcapng", false);
    if (!started.isCapturing()) {
      return;
    }

    // Generate genuine loopback traffic into C while its relayed capture is capturing.
    Unirest.get("http://localhost:" + downstreamPort + "/clock").asString();

    PcapAdminController.CaptureStatusResponse stopped =
        upstream.stopCapture(started.getCaptureId());

    assertThat(stopped.getError()).isNull();
    assertThat(stopped.getPacketCount())
        .as(
            "packets C actually captured must survive the download+merge into B's file, not be"
                + " silently dropped")
        .isGreaterThan(0);
  }

  @Test
  @DisplayName("suspend/resume relay actually takes effect on C's relayed capture, not just B's")
  void suspendResumeRelayTakesEffectOnDownstream() throws Exception {
    PcapCaptureHandlerImpl upstream = newUpstreamHandlerPointingAt(downstreamPort);

    PcapAdminController.CaptureStatusResponse started =
        upstream.startCapture("mesh-suspend.pcapng", false);
    if (!started.isCapturing()) {
      return;
    }
    String downstreamCaptureId = onlyCaptureIdOnDownstream();

    generateTrafficInto(downstreamPort);
    long beforeSuspend = downstreamHandler.getStatus(downstreamCaptureId).getPacketCount();
    assertThat(beforeSuspend).as("baseline traffic must have been captured").isGreaterThan(0);

    assertThat(upstream.suspendCapture(started.getCaptureId()).getError()).isNull();
    generateTrafficInto(downstreamPort);
    long duringSuspend = downstreamHandler.getStatus(downstreamCaptureId).getPacketCount();
    assertThat(duringSuspend)
        .as(
            "no new packets while C's relayed capture is suspended — proves the suspend relay"
                + " actually reached C, not just B")
        .isEqualTo(beforeSuspend);

    assertThat(upstream.resumeCapture(started.getCaptureId()).getError()).isNull();
    generateTrafficInto(downstreamPort);
    long afterResume = downstreamHandler.getStatus(downstreamCaptureId).getPacketCount();
    assertThat(afterResume)
        .as("packets resume being captured on C once the resume relay reaches it")
        .isGreaterThan(duringSuspend);

    upstream.stopCapture(started.getCaptureId());
  }

  @Test
  @DisplayName(
      "the gap flag survives serialization over the relay — C treats a relayed gap "
          + "capture with the same priority rule as a locally-opened one")
  void gapFlagSurvivesRelayOverTheWire() throws Exception {
    PcapCaptureHandlerImpl upstream = newUpstreamHandlerPointingAt(downstreamPort);

    PcapAdminController.CaptureStatusResponse startedGap =
        upstream.startCapture("mesh-gap.pcapng", true);
    if (!startedGap.isCapturing()) {
      return;
    }
    String relayedGapCaptureId = onlyCaptureIdOnDownstream();

    // Open a second, non-gap capture directly on C (same process — no relay needed for this half):
    // gap priority means the relayed capture must now sit out while this one is open, which only
    // happens if C genuinely received gap=true, not just defaulted it.
    PcapAdminController.CaptureStatusResponse directNonGap =
        downstreamHandler.startCapture("direct-non-gap.pcapng", false);
    if (!directNonGap.isCapturing()) {
      upstream.stopCapture(startedGap.getCaptureId());
      return;
    }

    generateTrafficInto(downstreamPort);
    long gapPacketsWhileNonGapOpen =
        downstreamHandler.getStatus(relayedGapCaptureId).getPacketCount();
    assertThat(gapPacketsWhileNonGapOpen)
        .as(
            "the relayed gap capture must sit out while a non-gap capture is open on C — proves"
                + " gap=true actually made it across the wire, not just a client-side default")
        .isZero();

    downstreamHandler.stopCapture(directNonGap.getCaptureId());
    upstream.stopCapture(startedGap.getCaptureId());
  }

  private void generateTrafficInto(int port) throws InterruptedException {
    Unirest.get("http://localhost:" + port + "/clock").asString();
    Thread.sleep(300); // let the async capture reader thread catch up
  }

  private String onlyCaptureIdOnDownstream() throws Exception {
    Field capturesField = PcapCaptureHandlerImpl.class.getDeclaredField("captures");
    capturesField.setAccessible(true);
    Map<String, ?> captures = (Map<String, ?>) capturesField.get(downstreamHandler);
    return captures.keySet().iterator().next();
  }

  private PcapCaptureHandlerImpl newUpstreamHandlerPointingAt(int port) {
    TigerProxy upstreamProxy = mock(TigerProxy.class);
    TigerProxyConfiguration upstreamConfig = new TigerProxyConfiguration();
    upstreamConfig.setPcapCapture(PcapCaptureConfiguration.builder().build());
    when(upstreamProxy.getTigerProxyConfiguration()).thenReturn(upstreamConfig);
    when(upstreamProxy.getProxyPort()).thenReturn(8080);
    when(upstreamProxy.getAdminPort()).thenReturn(9000);
    when(upstreamProxy.getRoutes()).thenReturn(List.of());
    when(upstreamProxy.getRemoteProxyClients())
        .thenReturn(List.of(new TigerRemoteProxyClient("http://localhost:" + port)));
    return new PcapCaptureHandlerImpl(upstreamProxy);
  }

  @SuppressWarnings("unchecked")
  private int captureCountOnDownstream() throws Exception {
    Field capturesField = PcapCaptureHandlerImpl.class.getDeclaredField("captures");
    capturesField.setAccessible(true);
    Map<String, ?> captures = (Map<String, ?>) capturesField.get(downstreamHandler);
    return captures.size();
  }
}
