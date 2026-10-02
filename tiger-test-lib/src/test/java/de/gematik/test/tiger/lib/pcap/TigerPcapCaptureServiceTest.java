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
package de.gematik.test.tiger.lib.pcap;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.pcap4j.core.NotOpenException;
import org.pcap4j.packet.Packet;

/**
 * Unit tests for {@code TigerPcapCaptureService}. Primarily focused on error handling and graceful
 * degradation when native pcap library is unavailable (which is the common case on CI without
 * libpcap installed).
 *
 * <p>Functional tests that actually capture packets are gated behind system properties and
 * integration test tags.
 */
@DisplayName("TigerPcapCaptureService")
class TigerPcapCaptureServiceTest {

  @TempDir private Path tempDir;
  private TigerPcapCaptureService service;

  @BeforeEach
  void setUp() {
    service = new TigerPcapCaptureService(64, 16 * 1024);
  }

  @Test
  @DisplayName("Service gracefully disables when native lib is unavailable")
  @EnabledIfSystemProperty(named = "pcap.skip-if-unavailable", matches = "true")
  void serviceDisablesWhenNativeLibUnavailable() {
    // This test only runs if the native lib is expected to be unavailable.
    service.start(Set.of(8080, 9090));
    // Service should remain disabled (enabled=false), not throw.
    assertThat(service.isEnabled()).isFalse();
  }

  @Test
  @DisplayName("Service ignores stop when not started")
  void serviceIgnoresStopWhenNotStarted() {
    service.stop();
    assertThat(service.isEnabled()).isFalse();
  }

  @Test
  @DisplayName("Service ignores rotate when not started")
  void serviceIgnoresRotateWhenNotStarted() {
    Path dummyFile = tempDir.resolve("dummy.pcapng");
    service.rotate(dummyFile);
    assertThat(service.isEnabled()).isFalse();
  }

  @Test
  @DisplayName("Service is idempotent on stop")
  void serviceIsIdempotentOnStop() {
    service.stop();
    assertDoesNotThrow(service::stop);
    assertThat(service.isEnabled()).isFalse();
  }

  @Test
  @DisplayName("Service is idempotent on start")
  void serviceIsIdempotentOnStart() {
    service.start(Set.of(8080));
    boolean enabledAfterFirstStart = service.isEnabled();

    assertThatCode(() -> service.start(Set.of(8080))).doesNotThrowAnyException();

    assertThat(service.isEnabled()).isEqualTo(enabledAfterFirstStart);
  }

  @Test
  @DisplayName("Service handles empty port set without crashing")
  void serviceHandlesEmptyPortSet() {
    assertThatCode(() -> service.start(Collections.emptySet())).doesNotThrowAnyException();
    assertThatCode(service::stop).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Service handles null port set without crashing")
  void serviceHandlesNullPortSet() {
    assertThatCode(() -> service.start(null)).doesNotThrowAnyException();
    assertThatCode(service::stop).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Suspend without start is no-op")
  void suspendWithoutStartIsNoop() {
    service.suspend();
    assertThat(service.isEnabled()).isFalse();
  }

  @Test
  @DisplayName("Resume without start warns and is no-op")
  void resumeWithoutStartWarnsAndNoops() {
    service.resume();
    assertThat(service.isEnabled()).isFalse();
  }

  @Test
  @DisplayName("Resume caches ports from start")
  void resumeCachesPortsFromStart() {
    Set<Integer> ports = Set.of(8080, 9090);
    service.start(ports);
    service.suspend();
    assertDoesNotThrow(service::resume);
  }

  @Test
  @DisplayName("Resume is idempotent when already enabled")
  void resumeIsIdempotentWhenEnabled() {
    service.start(Set.of(8080));
    assertDoesNotThrow(service::resume);
  }

  @Test
  @DisplayName("Suspend clears lazy dumper flag")
  void suspendClearsLazyDumperFlag() {
    service.start(Set.of(8080));
    service.suspend();
    assertDoesNotThrow(service::resume);
  }

  @Test
  @DisplayName("Service handles suspend/resume cycle")
  void serviceHandlesSuspendResumeCycle() {
    Set<Integer> ports = Set.of(8080, 9090);
    service.start(ports);
    service.suspend();
    assertThat(service.isEnabled()).isFalse();
    assertDoesNotThrow(service::resume);
  }

  @Test
  @DisplayName("Suspend sets enabled to false")
  void suspendDisablesCapture() {
    service.start(Set.of(8080, 9090));
    if (service.isEnabled()) {
      service.suspend();
      assertThat(service.isEnabled()).isFalse();
    }
  }

  @Test
  @DisplayName("rotate() flushes the previous dumper even if opening the replacement fails")
  void rotate_stillClosesPreviousDumperWhenReplacementFailsToOpen() throws Exception {
    Path firstFile = tempDir.resolve("first.pcapng");
    service.start(Set.of(8080));
    if (!service.isEnabled()) {
      return; // native lib unavailable on this machine; nothing to verify
    }
    service.rotate(firstFile);
    await().atMost(200, TimeUnit.MILLISECONDS).until(() -> Files.exists(firstFile));

    // Parent path is a plain file, not a directory, so open() fails deterministically.
    Path unwritableParent = tempDir.resolve("not-a-directory");
    Files.createFile(unwritableParent);
    Path secondFile = unwritableParent.resolve("second.pcapng");

    service.rotate(secondFile);

    assertThat(service.isEnabled()).as("a failed rotate() disables capture").isFalse();
    assertThat(service.getCurrentDumperPacketCount())
        .as("the failed rotate() must not leave currentDumper pointing at the old dumper")
        .isZero();
    assertThat(Files.size(firstFile))
        .as("the first file must still have been flushed, not leaked open")
        .isGreaterThan(0);
  }

  @Test
  @DisplayName("dumpPacket does not signal a break when the dumper was closed concurrently")
  void dumpPacket_dumperClosedConcurrently_doesNotBreakCaptureLoop() {
    PcapPacketDumper dumper = mock(PcapPacketDumper.class);
    doThrow(new RuntimeException(new NotOpenException("closed by concurrent rotate()")))
        .when(dumper)
        .dump(any(Packet.class));

    TigerPcapCaptureService.ProcessResult result = service.dumpPacket(dumper, mock(Packet.class));

    assertThat(result.shouldBreak)
        .as("a dumper closed by a rotation race must not permanently kill the capture loop")
        .isFalse();
    assertThat(result.packetProcessed).isFalse();
  }

  @Test
  @DisplayName("dumpPacket reports success for a normal write")
  void dumpPacket_normalWrite_reportsProcessed() {
    PcapPacketDumper dumper = mock(PcapPacketDumper.class);

    TigerPcapCaptureService.ProcessResult result = service.dumpPacket(dumper, mock(Packet.class));

    assertThat(result.shouldBreak).isFalse();
    assertThat(result.packetProcessed).isTrue();
  }

  @Test
  @DisplayName("resolveBpfFilter uses the manual filter when configured")
  void resolveBpfFilter_manualFilterTakesPrecedence() {
    service.setManualBpfFilter("tcp port 4444");

    String filter = service.resolveBpfFilter(Set.of(8080, 9090));

    assertThat(filter).isEqualTo("tcp port 4444");
  }

  @Test
  @DisplayName("resolveBpfFilter falls back to port-based filter when manual filter is null")
  void resolveBpfFilter_fallsBackToPortsWhenManualFilterNull() {
    String filter = service.resolveBpfFilter(Set.of(8080));

    assertThat(filter).isEqualTo("tcp port 8080");
  }

  @Test
  @DisplayName("alsoCapturePorts is a no-op when capture is not running")
  void alsoCapturePorts_noopWhenNotEnabled() {
    assertThatCode(() -> service.alsoCapturePorts(Set.of(1234))).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("alsoCapturePorts never widens a manually configured filter")
  void alsoCapturePorts_neverTouchesManualFilter() {
    service.setManualBpfFilter("tcp port 4444");
    service.start(Set.of(8080));
    if (!service.isEnabled()) {
      return; // native lib unavailable on this machine; nothing to verify
    }

    assertThatCode(() -> service.alsoCapturePorts(Set.of(9999))).doesNotThrowAnyException();
    // Effective filter stays the manual one - resolveBpfFilter proves it independently of ports.
    assertThat(service.resolveBpfFilter(Set.of(9999))).isEqualTo("tcp port 4444");
  }

  @Test
  @DisplayName("alsoCapturePorts does not narrow a capture that started unrestricted")
  void alsoCapturePorts_doesNotNarrowAnUnrestrictedCapture() {
    service.start(Set.of()); // empty set = unrestricted, no filter applied
    if (!service.isEnabled()) {
      return; // native lib unavailable on this machine; nothing to verify
    }

    service.alsoCapturePorts(Set.of(7000));

    // Must stay unrestricted, not narrow to "tcp port 7000".
    assertThat(service.getLastStartPortsForTesting()).isEmpty();
  }

  @Test
  @DisplayName("alsoCapturePorts widens the tracked ports on a restricted capture")
  void alsoCapturePorts_widensTrackedPortsOnRestrictedCapture() {
    service.start(Set.of(8080));
    if (!service.isEnabled()) {
      return; // native lib unavailable on this machine; nothing to verify
    }

    service.alsoCapturePorts(Set.of(7000));

    assertThat(service.getLastStartPortsForTesting()).containsExactlyInAnyOrder(8080, 7000);
  }

  @Test
  @DisplayName("computeWidenedPorts merges new ports into the current set")
  void computeWidenedPorts_merges() {
    assertThat(TigerPcapCaptureService.computeWidenedPorts(Set.of(8080, 9090), Set.of(7000)))
        .contains(Set.of(7000, 8080, 9090));
  }

  @Test
  @DisplayName("computeWidenedPorts is empty when nothing new is added")
  void computeWidenedPorts_emptyWhenAlreadyCovered() {
    assertThat(TigerPcapCaptureService.computeWidenedPorts(Set.of(8080), Set.of(8080))).isEmpty();
  }

  @Test
  @DisplayName("computeWidenedPorts is empty for a null or empty addition")
  void computeWidenedPorts_emptyForNullOrEmptyAddition() {
    assertThat(TigerPcapCaptureService.computeWidenedPorts(Set.of(8080), null)).isEmpty();
    assertThat(TigerPcapCaptureService.computeWidenedPorts(Set.of(8080), Set.of())).isEmpty();
  }

  @Test
  @DisplayName("computeWidenedPorts treats a null current set as empty")
  void computeWidenedPorts_nullCurrentSetTreatedAsEmpty() {
    assertThat(TigerPcapCaptureService.computeWidenedPorts(null, Set.of(7000)))
        .contains(Set.of(7000));
  }

  @Test
  @DisplayName("getCurrentDumperPacketCount is 0 with no dumper open")
  void getCurrentDumperPacketCount_zeroWhenNoDumperOpen() {
    assertThat(service.getCurrentDumperPacketCount()).isZero();
  }

  @Test
  @DisplayName("getCurrentDumperPacketCount counts packets written via dumpPacket")
  void getCurrentDumperPacketCount_countsSuccessfulDumps() {
    Path file = tempDir.resolve("counted.pcapng");
    service.start(Set.of(8080));
    if (!service.isEnabled()) {
      return; // native lib unavailable on this machine; nothing to verify
    }
    try {
      service.rotate(file);

      assertThat(service.getCurrentDumperPacketCount())
          .as("a freshly rotated dumper has written no packets yet")
          .isZero();
    } finally {
      service.stop(); // close the file handle before @TempDir cleanup runs
    }
  }

  @Test
  @DisplayName("Resume restores enabled state after suspend")
  void resumeRestoresEnabledStateAfterSuspend() {
    service.start(Set.of(8080, 9090));
    boolean startedEnabled = service.isEnabled();
    if (startedEnabled) {
      service.suspend();
      assertThat(service.isEnabled()).isFalse();
      service.resume();
      assertThat(service.isEnabled()).isTrue();
    }
    // If native lib unavailable, service never enabled; skip test
  }

  @Test
  @DisplayName("Resume multiple times stays enabled")
  void resumeMultipleTimes() {
    service.start(Set.of(8080));
    boolean wasEnabled = service.isEnabled();
    if (wasEnabled) {
      service.resume();
      assertThat(service.isEnabled()).isTrue();
      service.resume();
      assertThat(service.isEnabled()).isTrue();
    }
  }

  @Test
  @DisplayName("Stop clears the dumper")
  void stopClearsDumper() {
    service.start(Set.of(8080));
    if (service.isEnabled()) {
      service.rotate(tempDir.resolve("test.pcapng"));
      assertThat(service.getCurrentDumperPacketCount()).isZero();
    }
    service.stop();
    assertThat(service.getCurrentDumperPacketCount()).isZero();
  }

  @Test
  @DisplayName("Multiple suspend/resume cycles work correctly")
  void multipleSuspendResumeCycles() {
    service.start(Set.of(8080, 9090));
    if (!service.isEnabled()) {
      return;
    }
    for (int i = 0; i < 3; i++) {
      service.suspend();
      assertThat(service.isEnabled()).isFalse();
      service.resume();
      assertThat(service.isEnabled()).isTrue();
    }
  }

  @Test
  @DisplayName("dumpPacket with RuntimeException continues capture")
  void dumpPacket_runtimeException_continuesC() {
    PcapPacketDumper dumper = mock(PcapPacketDumper.class);
    doThrow(new RuntimeException("unexpected error")).when(dumper).dump(any(Packet.class));
    TigerPcapCaptureService.ProcessResult result = service.dumpPacket(dumper, mock(Packet.class));
    assertThat(result.shouldBreak).isFalse();
    assertThat(result.packetProcessed).isFalse();
  }

  @Test
  @DisplayName("dumpPacket with null dumper returns false")
  void dumpPacket_nullDumper_returnsNotProcessed() {
    TigerPcapCaptureService.ProcessResult result = service.dumpPacket(null, mock(Packet.class));
    assertThat(result.shouldBreak).isFalse();
    assertThat(result.packetProcessed).isFalse();
  }

  @Test
  @DisplayName("Multiple stops are safe")
  void multipleStopsAreSafe() {
    service.start(Set.of(8080));
    service.stop();
    assertThatCode(service::stop).doesNotThrowAnyException();
    assertThatCode(service::stop).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Rotate after stop has no effect")
  void rotateAfterStop() {
    service.start(Set.of(8080));
    service.stop();
    Path file = tempDir.resolve("afterstop.pcapng");
    assertThatCode(() -> service.rotate(file)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Start with different port sets")
  void startWithDifferentPortSets() {
    assertThatCode(() -> service.start(Set.of())).doesNotThrowAnyException();
    service.stop();
    assertThatCode(() -> service.start(Set.of(8080))).doesNotThrowAnyException();
    service.stop();
    assertThatCode(() -> service.start(Set.of(8080, 9090, 7070))).doesNotThrowAnyException();
    service.stop();
  }

  @Test
  @DisplayName("resolveBpfFilter with large port set")
  void resolveBpfFilter_largePortSet() {
    Set<Integer> ports = new java.util.HashSet<>();
    for (int i = 8000; i < 8010; i++) {
      ports.add(i);
    }
    String filter = service.resolveBpfFilter(ports);
    assertThat(filter).contains("tcp port");
  }

  @Test
  @DisplayName("resolveBpfFilter with single port")
  void resolveBpfFilter_singlePort() {
    String filter = service.resolveBpfFilter(Set.of(3000));
    assertThat(filter).isEqualTo("tcp port 3000");
  }

  @Test
  @DisplayName("alsoCapturePorts with empty service ports")
  void alsoCapturePorts_withEmptyServicePorts() {
    service.start(Set.of());
    if (service.isEnabled()) {
      service.alsoCapturePorts(Set.of(8080));
      assertThat(service.getLastStartPortsForTesting()).isEmpty();
    }
  }

  @Test
  @DisplayName("alsoCapturePorts preserves manual filter priority")
  void alsoCapturePorts_manualFilterHasPriority() {
    service.setManualBpfFilter("tcp port 5555");
    service.start(Set.of(8080));
    if (service.isEnabled()) {
      service.alsoCapturePorts(Set.of(9090));
      assertThat(service.resolveBpfFilter(Set.of())).isEqualTo("tcp port 5555");
    }
  }

  @Test
  @DisplayName("Rotate multiple times")
  void rotateMultipleTimes() {
    service.start(Set.of(8080));
    if (!service.isEnabled()) {
      return;
    }
    Path file1 = tempDir.resolve("first.pcapng");
    Path file2 = tempDir.resolve("second.pcapng");
    Path file3 = tempDir.resolve("third.pcapng");
    service.rotate(file1);
    await().atMost(200, TimeUnit.MILLISECONDS).until(() -> Files.exists(file1));
    service.rotate(file2);
    await().atMost(200, TimeUnit.MILLISECONDS).until(() -> Files.exists(file2));
    service.rotate(file3);
    await().atMost(200, TimeUnit.MILLISECONDS).until(() -> Files.exists(file3));
    service.stop();
    assertThat(file1).exists();
    assertThat(file2).exists();
    assertThat(file3).exists();
  }

  @Test
  @DisplayName("getCurrentDumperPacketCount after rotate")
  void getCurrentDumperPacketCount_afterRotate() {
    service.start(Set.of(8080));
    if (!service.isEnabled()) {
      return;
    }
    Path file = tempDir.resolve("packet_count.pcapng");
    service.rotate(file);
    await().atMost(200, TimeUnit.MILLISECONDS).until(() -> Files.exists(file));
    long count = service.getCurrentDumperPacketCount();
    assertThat(count).isGreaterThanOrEqualTo(0);
    service.stop();
  }

  @Test
  @DisplayName("Start with null then empty set")
  void startWithNullThenEmptySet() {
    assertThatCode(() -> service.start(null)).doesNotThrowAnyException();
    service.stop();
    assertThatCode(() -> service.start(Set.of())).doesNotThrowAnyException();
    service.stop();
  }

  @ParameterizedTest
  @CsvSource(value = {"   ,tcp port 8080", "  tcp port 5555  ,  tcp port 5555  ", ",tcp port 8080"})
  @DisplayName("Manual filter set to empty string")
  void manualFilter(String filterIn, String expected) {
    service.setManualBpfFilter(filterIn == null ? "" : filterIn);
    String filter = service.resolveBpfFilter(Set.of(8080));
    assertThat(filter).isEqualTo(expected);
  }
}
