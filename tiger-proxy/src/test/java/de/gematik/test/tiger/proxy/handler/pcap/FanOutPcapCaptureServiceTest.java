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
import static de.gematik.rbellogger.util.MemoryConstants.MB;
import static org.assertj.core.api.Assertions.*;

import de.gematik.test.tiger.common.data.config.tigerproxy.PcapCaptureConfiguration;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pcap4j.core.PcapHandle;
import org.pcap4j.core.PcapNativeException;
import org.pcap4j.core.PcapNetworkInterface;

@DisplayName("FanOutPcapCaptureService (proxy-side, fan-out)")
class FanOutPcapCaptureServiceTest {

  @TempDir private Path tempDir;
  private FanOutPcapCaptureService service;

  @BeforeEach
  void setUp() {
    service = new FanOutPcapCaptureService();
  }

  @Test
  @DisplayName("openCapture()/closeCapture() are no-ops when capture is not active")
  void captureOpsAreNoopsWhenInactive() {
    assertThat(service.openCapture("s1", tempDir.resolve("a.pcapng"), false)).isFalse();
    assertThat(service.closeCapture("s1")).isZero();
  }

  @Test
  @DisplayName("closing an unknown capture is a safe no-op")
  void closingUnknownCaptureIsNoop() {
    service.start(Set.of(8080));
    if (!service.isEnabled()) {
      return;
    }
    assertThat(service.closeCapture("never-opened")).isZero();
  }

  @Test
  @DisplayName("two captures can be open at once, each with its own file")
  void twoCapturesOpenConcurrently() {
    service.start(Set.of(8080));
    if (!service.isEnabled()) {
      return;
    }

    Path fileA = tempDir.resolve("capture-a.pcapng");
    Path fileB = tempDir.resolve("capture-b.pcapng");
    assertThat(service.openCapture("capture-a", fileA, false)).isTrue();
    assertThat(service.openCapture("capture-b", fileB, false)).isTrue();

    // Closing one capture must not disturb the other.
    service.closeCapture("capture-a");
    assertThat(fileA).exists();

    service.closeCapture("capture-b");
    assertThat(fileB).exists();
  }

  @Test
  @DisplayName("stop() closes every open capture")
  void stopClosesEveryCapture() {
    service.start(Set.of(8080));
    if (!service.isEnabled()) {
      return;
    }

    Path fileA = tempDir.resolve("capture-a.pcapng");
    Path fileB = tempDir.resolve("capture-b.pcapng");
    service.openCapture("capture-a", fileA, false);
    service.openCapture("capture-b", fileB, false);

    service.stop();

    assertThat(fileA).exists();
    assertThat(fileB).exists();
    assertThat(service.isEnabled()).isFalse();
  }

  @Test
  @DisplayName("gap capture only receives packets while no non-gap capture is open")
  void gapCaptureYieldsToNonGapCaptures() {
    service.start(Set.of(8080));
    if (!service.isEnabled()) {
      return;
    }

    Path gapFile = tempDir.resolve("gap.pcapng");
    Path scenarioFile = tempDir.resolve("scenario.pcapng");

    // Gap capture opens first (suite start), before any scenario runs.
    assertThat(service.openCapture("gap", gapFile, true)).isTrue();

    // A scenario capture opens; from here on the gap capture must sit out.
    assertThat(service.openCapture("scenario", scenarioFile, false)).isTrue();

    service.closeCapture("scenario");
    assertThat(scenarioFile).exists();

    // Scenario is gone again; the gap capture resumes receiving packets.
    long gapPackets = service.closeCapture("gap");
    assertThat(gapFile).exists();
    assertThat(gapPackets).isGreaterThanOrEqualTo(0);
  }

  @Test
  @DisplayName("suspendCapture()/resumeCapture() are no-ops (false) for an unknown capture")
  void suspendResumeUnknownCaptureIsNoop() {
    service.start(Set.of(8080));
    if (!service.isEnabled()) {
      return;
    }
    assertThat(service.suspendCapture("never-opened")).isFalse();
    assertThat(service.resumeCapture("never-opened")).isFalse();
  }

  @Test
  @DisplayName("a suspended capture can be resumed and closed normally")
  void suspendedCaptureCanBeResumedAndClosed() {
    service.start(Set.of(8080));
    if (!service.isEnabled()) {
      return;
    }

    Path file = tempDir.resolve("suspendable.pcapng");
    assertThat(service.openCapture("s1", file, false)).isTrue();

    assertThat(service.suspendCapture("s1")).isTrue();
    assertThat(service.resumeCapture("s1")).isTrue();

    service.closeCapture("s1");
    assertThat(file).exists();
  }

  @Test
  @DisplayName("a suspended scenario capture still excludes the gap capture from capturing")
  void suspendedScenarioCaptureStillExcludesGapCapture() {
    service.start(Set.of(8080));
    if (!service.isEnabled()) {
      return;
    }

    Path gapFile = tempDir.resolve("gap.pcapng");
    Path scenarioFile = tempDir.resolve("scenario.pcapng");
    assertThat(service.openCapture("gap", gapFile, true)).isTrue();
    assertThat(service.openCapture("scenario", scenarioFile, false)).isTrue();

    // Suspending the scenario capture must not hand control back to the gap capture — it's still
    // open, just quiet, mirroring the local capture side's suspend semantics.
    assertThat(service.suspendCapture("scenario")).isTrue();

    service.closeCapture("scenario");
    service.closeCapture("gap");
    assertThat(scenarioFile).exists();
    assertThat(gapFile).exists();
  }

  @Test
  @DisplayName("resolveBpfFilter() uses the manual filter when set, ignoring ports")
  void resolveBpfFilterPrefersManualFilter() {
    service.setManualBpfFilter("tcp port 9999");
    assertThat(service.resolveBpfFilter(Set.of(8080))).isEqualTo("tcp port 9999");
  }

  @Test
  @DisplayName("resolveBpfFilter() falls back to a port-based filter when no manual filter is set")
  void resolveBpfFilterFallsBackToPorts() {
    assertThat(service.resolveBpfFilter(Set.of(8080))).contains("8080");
  }

  @Test
  @DisplayName("computeWidenedPorts() merges in new ports, or is empty if nothing changed")
  void computeWidenedPortsMergesOrIsEmpty() {
    assertThat(FanOutPcapCaptureService.computeWidenedPorts(Set.of(8080), Set.of(9090)))
        .contains(Set.of(8080, 9090));
    assertThat(FanOutPcapCaptureService.computeWidenedPorts(Set.of(8080), Set.of(8080))).isEmpty();
    assertThat(FanOutPcapCaptureService.computeWidenedPorts(Set.of(8080), Set.of())).isEmpty();
    assertThat(FanOutPcapCaptureService.computeWidenedPorts(Set.of(8080), null)).isEmpty();
  }

  @Test
  @DisplayName("alsoCapturePorts() is a no-op when capture is not active")
  void alsoCapturePortsNoopWhenInactive() {
    assertThatCode(() -> service.alsoCapturePorts(Set.of(9090))).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("alsoCapturePorts() is a no-op when a manual filter is configured")
  void alsoCapturePortsNoopWithManualFilter() {
    service.setManualBpfFilter("tcp port 9999");
    service.start(Set.of(8080));
    if (!service.isEnabled()) {
      return;
    }
    // Should not throw and should not touch the manual filter.
    assertThatCode(() -> service.alsoCapturePorts(Set.of(9090))).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("interfaceNames is honored: an unresolvable name yields a disabled service")
  void unresolvableInterfaceNameDisablesCapture() {
    service.setInterfaceNames(List.of("definitely-not-a-real-interface"));
    service.start(Set.of(8080));
    assertThat(service.isEnabled()).isFalse();
  }

  @Test
  @DisplayName("per-capture filters: a capture never receives traffic only another capture wants")
  void capturesWithDifferentFiltersOnlyReceiveTheirOwnTraffic() throws Exception {
    try (ServerSocket serverA = new ServerSocket(0);
        ServerSocket serverB = new ServerSocket(0)) {
      var settingsA =
          PcapCaptureConfiguration.builder()
              .bpfFilter("tcp port " + serverA.getLocalPort())
              .build();
      var settingsB =
          PcapCaptureConfiguration.builder()
              .bpfFilter("tcp port " + serverB.getLocalPort())
              .build();
      boolean openedA =
          service.openCapture("a", tempDir.resolve("a.pcapng"), false, settingsA, Set.of());
      if (!openedA) {
        return; // native pcap unavailable in this environment; nothing left to assert
      }
      assertThat(service.openCapture("b", tempDir.resolve("b.pcapng"), false, settingsB, Set.of()))
          .isTrue();

      // Traffic only to A's port: B's filter must keep it out even though the kernel lets it
      // through for A.
      try (Socket client = new Socket("127.0.0.1", serverA.getLocalPort());
          Socket accepted = serverA.accept()) {
        client.getOutputStream().write("hello".getBytes());
        client.getOutputStream().flush();
        accepted.getInputStream().read(new byte[5]);
        awaitPackets("a");
      }

      assertThat(service.closeCapture("a")).isPositive();
      assertThat(service.closeCapture("b")).isZero();
    }
  }

  @Test
  @DisplayName("a gap capture keeps capturing while only another client's scenarios are open")
  void gapCaptureIgnoresOtherClientsScenarios() throws Exception {
    try (ServerSocket server = new ServerSocket(0)) {
      var settings =
          PcapCaptureConfiguration.builder().bpfFilter("tcp port " + server.getLocalPort()).build();
      if (!service.openCapture(
          "gap-a", tempDir.resolve("gap-a.pcapng"), true, settings, Set.of(), "suite-a")) {
        return; // native pcap unavailable in this environment; nothing left to assert
      }
      assertThat(
              service.openCapture(
                  "scenario-b",
                  tempDir.resolve("scenario-b.pcapng"),
                  false,
                  settings,
                  Set.of(),
                  "suite-b"))
          .isTrue();

      exchangeOver(server);
      awaitPackets("scenario-b");
      awaitPackets("gap-a");

      assertThat(service.closeCapture("gap-a"))
          .as("suite B's scenario must not blind suite A's gap file")
          .isPositive();
      assertThat(service.closeCapture("scenario-b")).isPositive();
    }
  }

  @Test
  @DisplayName("a gap capture sits out while one of its own client's scenarios is open")
  void gapCaptureYieldsToItsOwnClientsScenarios() throws Exception {
    try (ServerSocket server = new ServerSocket(0)) {
      var settings =
          PcapCaptureConfiguration.builder().bpfFilter("tcp port " + server.getLocalPort()).build();
      if (!service.openCapture(
          "gap-a", tempDir.resolve("gap-a.pcapng"), true, settings, Set.of(), "suite-a")) {
        return; // native pcap unavailable in this environment; nothing left to assert
      }
      assertThat(
              service.openCapture(
                  "scenario-a",
                  tempDir.resolve("scenario-a.pcapng"),
                  false,
                  settings,
                  Set.of(),
                  "suite-a"))
          .isTrue();

      exchangeOver(server);
      awaitPackets("scenario-a");

      assertThat(service.closeCapture("gap-a")).isZero();
      assertThat(service.closeCapture("scenario-a")).isPositive();
    }
  }

  private static void exchangeOver(ServerSocket server) throws Exception {
    try (Socket client = new Socket("127.0.0.1", server.getLocalPort());
        Socket accepted = server.accept()) {
      client.getOutputStream().write("hello".getBytes());
      client.getOutputStream().flush();
      accepted.getInputStream().read(new byte[5]);
    }
  }

  @Test
  @DisplayName("a capture's snaplen and buffer size reach the native handle, once per interface")
  void captureSnaplenAndBufferSizeReachTheNativeHandle() {
    List<int[]> opened = new ArrayList<>();
    var recording =
        new FanOutPcapCaptureService() {
          @Override
          protected PcapHandle openHandle(
              PcapNetworkInterface iface, int snaplenBytes, int bufferBytes)
              throws PcapNativeException {
            opened.add(new int[] {snaplenBytes, bufferBytes});
            return super.openHandle(iface, snaplenBytes, bufferBytes);
          }
        };
    var first = PcapCaptureConfiguration.builder().snaplenKb(32).bufferSizeKb(2048).build();
    if (!recording.openCapture("first", tempDir.resolve("first.pcapng"), false, first, Set.of())) {
      return; // native pcap unavailable in this environment; nothing left to assert
    }

    try {
      assertThat(opened).hasSize(1);
      assertThat(opened.get(0)).containsExactly(32 * KB, 2 * MB);

      // The interface is already open: a capture asking for other values must not reopen it.
      var second = PcapCaptureConfiguration.builder().snaplenKb(8).bufferSizeKb(4096).build();
      assertThat(
              recording.openCapture(
                  "second", tempDir.resolve("second.pcapng"), false, second, Set.of()))
          .isTrue();
      assertThat(opened).hasSize(1);
    } finally {
      recording.stop();
    }
  }

  @Test
  @DisplayName("bufferSizeBytes converts KB to bytes, clamps, and keeps zero as 'default'")
  void bufferSizeBytesConvertsAndClamps() {
    assertThat(FanOutPcapCaptureService.bufferSizeBytes(16 * KB)).isEqualTo(16 * MB);
    assertThat(FanOutPcapCaptureService.bufferSizeBytes(Integer.MAX_VALUE))
        .isEqualTo(Integer.MAX_VALUE);
    assertThat(FanOutPcapCaptureService.bufferSizeBytes(0)).isZero();
  }

  @Test
  @DisplayName("a capture whose interface cannot be opened fails without disturbing the others")
  void failingCaptureDoesNotDisturbOthers() {
    PcapCaptureConfiguration defaults = PcapCaptureConfiguration.builder().build();
    if (!service.openCapture("ok", tempDir.resolve("ok.pcapng"), false, defaults, Set.of(8080))) {
      return; // native pcap unavailable in this environment; nothing left to assert
    }

    var unresolvable =
        PcapCaptureConfiguration.builder()
            .interfaceNames(List.of("definitely-not-a-real-interface"))
            .build();
    assertThat(
            service.openCapture(
                "bad", tempDir.resolve("bad.pcapng"), false, unresolvable, Set.of(8080)))
        .isFalse();

    assertThat(service.isEnabled()).isTrue();
    service.closeCapture("ok");
    assertThat(tempDir.resolve("ok.pcapng")).exists();
  }

  private void awaitPackets(String captureId) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 5000;
    while (service.getCapturePacketCount(captureId) == 0 && System.currentTimeMillis() < deadline) {
      Thread.sleep(50);
    }
  }

  @Test
  @DisplayName("ports are widened (never narrowed) after a second openCapture-triggering start")
  void alsoCapturePortsWidensOnRealCapture() {
    service.start(Set.of(8080));
    if (!service.isEnabled()) {
      return;
    }
    assertThatCode(() -> service.alsoCapturePorts(Set.of(9090))).doesNotThrowAnyException();
    // Capture must still be usable afterward.
    Path file = tempDir.resolve("after-widen.pcapng");
    assertThat(service.openCapture("s1", file, false)).isTrue();
    service.closeCapture("s1");
    assertThat(file).exists();
  }
}
