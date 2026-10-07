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
import static de.gematik.test.tiger.testutils.pcap.FakePcapNetwork.LOOPBACK;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.gematik.test.tiger.common.data.config.tigerproxy.PcapCaptureConfiguration;
import de.gematik.test.tiger.testutils.pcap.FakePcapNetwork;
import de.gematik.test.tiger.testutils.pcap.FakePcapNetwork.OpenedHandle;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pcap4j.core.NotOpenException;
import org.pcap4j.core.PcapNetworkInterface;
import org.slf4j.LoggerFactory;

@DisplayName("FanOutPcapCaptureService on a fake network")
class FanOutPcapCaptureServiceOnFakeNetworkTest {

  private static final String ETH0 = "fake-eth0";
  private static final int KERNEL_SNAPLEN_LIMIT = 65535;

  @TempDir private Path tempDir;
  private final FakePcapNetwork network = new FakePcapNetwork(ETH0);
  private final List<LogCapture> logCaptures = new ArrayList<>();
  private FanOutPcapCaptureService service = new FakeNetworkFanOutService(network);

  private static final class LogCapture {
    private final Logger logger;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    LogCapture(Class<?> source) {
      logger = (Logger) LoggerFactory.getLogger(source);
      appender.start();
      logger.addAppender(appender);
    }

    List<String> warnings() {
      return appender.list.stream()
          .filter(event -> event.getLevel() == Level.WARN)
          .map(ILoggingEvent::getFormattedMessage)
          .toList();
    }

    void forget() {
      appender.list.clear();
    }

    void stop() {
      logger.detachAppender(appender);
    }
  }

  private LogCapture captureLogOf(Class<?> source) {
    LogCapture capture = new LogCapture(source);
    logCaptures.add(capture);
    return capture;
  }

  @AfterEach
  void tearDown() {
    service.stop();
    logCaptures.forEach(LogCapture::stop);
  }

  private static byte[] frame(int marker) {
    return new byte[] {(byte) marker, 0, 0, 0};
  }

  private static List<Integer> markersIn(Path file) throws IOException {
    List<Integer> markers = new ArrayList<>();
    try (PcapNgFileReader reader = new PcapNgFileReader(file)) {
      for (PcapNgFileReader.Packet packet = reader.next(); packet != null; packet = reader.next()) {
        markers.add((int) packet.data()[0]);
      }
    }
    return markers;
  }

  private static List<String> interfaceNamesIn(Path file) throws IOException {
    List<String> names = new ArrayList<>();
    try (PcapNgFileReader reader = new PcapNgFileReader(file)) {
      for (PcapNgFileReader.Packet packet = reader.next(); packet != null; packet = reader.next()) {
        String name = reader.interfaceOf(packet.interfaceId()).name();
        if (!names.contains(name)) {
          names.add(name);
        }
      }
    }
    return names;
  }

  private static PcapCaptureConfiguration onInterfaces(String... names) {
    return PcapCaptureConfiguration.builder().interfaceNames(List.of(names)).build();
  }

  private static PcapCaptureConfiguration withFilter(String bpfFilter) {
    return PcapCaptureConfiguration.builder().bpfFilter(bpfFilter).build();
  }

  // ===== starting and stopping the engine =====

  @Test
  @DisplayName("start() captures on the loopback, with the port filter and the configured sizes")
  void startOpensTheLoopbackWithThePortFilter() {
    service.start(Set.of(8080));

    assertThat(service.isEnabled()).isTrue();
    assertThat(network.opened())
        .containsExactly(new OpenedHandle(LOOPBACK, KERNEL_SNAPLEN_LIMIT, 16 * MB));
    assertThat(network.kernelFilters(LOOPBACK))
        .containsExactly(BpfFilterBuilder.forTcpPorts(Set.of(8080)));
    assertThat(network.isOpen(LOOPBACK)).isTrue();
  }

  @Test
  @DisplayName("stop() closes the handles and the engine can be started again")
  void stopClosesHandlesAndTheEngineRestarts() {
    service.start(Set.of(8080));

    service.stop();

    assertThat(service.isEnabled()).isFalse();
    assertThat(network.isOpen(LOOPBACK)).isFalse();

    service.start(Set.of(8080));
    assertThat(service.isEnabled()).isTrue();
    assertThat(network.opened()).hasSize(2);
  }

  @Test
  @DisplayName("stop() before start() is a no-op")
  void stopWithoutStartIsANoop() {
    service.stop();

    assertThat(service.isEnabled()).isFalse();
    assertThat(network.opened()).isEmpty();
  }

  @Test
  @DisplayName("starting an engine that is running changes nothing")
  void startingTwiceChangesNothing() {
    service.start(Set.of(8080));
    service.start(Set.of(9090));

    assertThat(network.opened()).hasSize(1);
    assertThat(network.kernelFilters(LOOPBACK))
        .containsExactly(BpfFilterBuilder.forTcpPorts(Set.of(8080)));
  }

  @Test
  @DisplayName("a manual filter replaces the port filter, and no ports means no filter at all")
  void manualFilterReplacesThePortFilter() {
    service.setManualBpfFilter("udp port 53");
    service.start(Set.of(8080));
    assertThat(network.kernelFilters(LOOPBACK)).containsExactly("udp port 53");
    service.stop();

    FakePcapNetwork other = new FakePcapNetwork();
    FanOutPcapCaptureService unfiltered = new FakeNetworkFanOutService(other);
    try {
      unfiltered.start(Set.of());
      assertThat(unfiltered.isEnabled()).isTrue();
      assertThat(other.kernelFilters(LOOPBACK)).isEmpty();
    } finally {
      unfiltered.stop();
    }
  }

  @Test
  @DisplayName("an engine that cannot open a single interface disables itself, and says so")
  void startWithoutAnyOpenableInterfaceDisablesCapture() {
    LogCapture log = captureLogOf(PcapCaptureEngine.class);
    network.failToOpen(LOOPBACK);

    service.start(Set.of(8080));

    assertThat(service.isEnabled()).isFalse();
    assertThat(log.warnings()).anyMatch(warning -> warning.contains("startup failed"));
  }

  @Test
  @DisplayName("an interface that cannot be opened is skipped, the others are captured")
  void startSkipsInterfacesThatCannotBeOpened() {
    network.failToOpen(ETH0);
    service.setInterfaceNames(List.of(LOOPBACK, ETH0));

    service.start(Set.of(8080));

    assertThat(service.isEnabled()).isTrue();
    assertThat(network.opened()).extracting(OpenedHandle::interfaceName).containsExactly(LOOPBACK);
  }

  @Test
  @DisplayName("interface names that match nothing disable capture")
  void unknownInterfaceNamesDisableCapture() {
    service.setInterfaceNames(List.of("no-such-interface"));

    service.start(Set.of(8080));

    assertThat(service.isEnabled()).isFalse();
    assertThat(network.opened()).isEmpty();
  }

  @Test
  @DisplayName("a missing native library disables capture instead of throwing, in every entry")
  void missingNativeLibraryDisablesCapture() {
    var withoutLibrary =
        new FakeNetworkFanOutService(network) {
          @Override
          protected List<PcapNetworkInterface> resolveInterfaces(List<String> names) {
            throw new NoClassDefFoundError("Could not initialize class org.pcap4j.core.Pcaps");
          }
        };
    service = withoutLibrary;

    service.start(Set.of(8080));
    assertThat(service.isEnabled()).isFalse();

    assertThat(
            service.openCapture(
                "s", tempDir.resolve("s.pcapng"), false, onInterfaces(LOOPBACK), Set.of(8080)))
        .as("opening a capture reports the failure like start() does, rather than throwing")
        .isFalse();
  }

  @Test
  @DisplayName("a snaplen above libpcap's maximum is clamped, with a warning")
  void oversizedSnaplenIsClamped() {
    LogCapture log = captureLogOf(PcapCaptureEngine.class);
    service = new FakeNetworkFanOutService(network, 100, 16 * KB);

    service.start(Set.of(8080));

    assertThat(network.opened().get(0).snaplenBytes()).isEqualTo(KERNEL_SNAPLEN_LIMIT);
    assertThat(log.warnings()).anyMatch(warning -> warning.contains("exceeds native max"));
  }

  @Test
  @DisplayName("packets the kernel dropped are reported when capture stops")
  void droppedPacketsAreReportedAtStop() {
    LogCapture log = captureLogOf(PcapCaptureEngine.class);
    service.start(Set.of(8080));
    network.dropPackets(LOOPBACK, 7, 3);

    service.stop();

    assertThat(log.warnings())
        .anyMatch(warning -> warning.contains("dropping packets") && warning.contains("7"));
  }

  // ===== reading packets =====

  @Test
  @DisplayName("a frame that fails to read does not take the engine down; the reader just ends")
  void readerFailuresAreContained() {
    LogCapture log = captureLogOf(PcapCaptureEngine.class);
    service.start(Set.of(8080));
    network.failNextRead(LOOPBACK, new IllegalStateException("kaputt"));

    awaitUntil(() -> !network.hasPendingReadFailure(LOOPBACK));
    awaitUntil(() -> !log.warnings().isEmpty());

    assertThat(log.warnings()).anyMatch(warning -> warning.contains("Fatal error"));
    service.stop();
    assertThat(service.isEnabled()).isFalse();
  }

  @Test
  @DisplayName("a handle closed underneath the reader ends it quietly")
  void handleClosedUnderTheReaderEndsItQuietly() {
    LogCapture log = captureLogOf(PcapCaptureEngine.class);
    service.start(Set.of(8080));
    network.failNextRead(LOOPBACK, new NotOpenException());

    awaitUntil(() -> !network.hasPendingReadFailure(LOOPBACK));
    service.stop();

    assertThat(log.warnings()).noneMatch(warning -> warning.contains("Fatal error"));
  }

  private static void awaitUntil(java.util.function.BooleanSupplier condition) {
    long deadline = System.nanoTime() + java.time.Duration.ofSeconds(5).toNanos();
    while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
      try {
        Thread.sleep(5);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  // ===== captures, fan-out, gap, suspend =====

  @Test
  @DisplayName("openCapture() needs capture to be running")
  void openCaptureNeedsCaptureToBeRunning() {
    assertThat(service.openCapture("s", tempDir.resolve("s.pcapng"), false)).isFalse();
    assertThat(service.closeCapture("s")).isZero();
  }

  @Test
  @DisplayName("every open capture gets every packet, in its own file")
  void everyOpenCaptureGetsEveryPacket() throws Exception {
    service.start(Set.of(8080));
    Path a = tempDir.resolve("a.pcapng");
    Path b = tempDir.resolve("b.pcapng");
    assertThat(service.openCapture("a", a, false)).isTrue();
    network.deliver(LOOPBACK, frame(1));
    assertThat(service.openCapture("b", b, false)).isTrue();
    network.deliver(LOOPBACK, frame(2));
    network.awaitProcessed(LOOPBACK);

    assertThat(service.getCapturePacketCount("a")).isEqualTo(2);
    assertThat(service.closeCapture("a")).isEqualTo(2);
    network.deliver(LOOPBACK, frame(3));
    network.awaitProcessed(LOOPBACK);
    assertThat(service.closeCapture("b")).isEqualTo(2);

    assertThat(markersIn(a)).containsExactly(1, 2);
    assertThat(markersIn(b)).containsExactly(2, 3);
    assertThat(service.getCapturePacketCount("a")).as("closed").isZero();
  }

  @Test
  @DisplayName("packets that arrive while no capture is open are dropped")
  void packetsWithoutACaptureAreDropped() throws Exception {
    service.start(Set.of(8080));
    network.deliver(LOOPBACK, frame(1));
    network.awaitProcessed(LOOPBACK);

    Path file = tempDir.resolve("late.pcapng");
    service.openCapture("late", file, false);
    service.closeCapture("late");

    assertThat(markersIn(file)).isEmpty();
  }

  @Test
  @DisplayName("a gap capture only gets packets while no scenario capture is open")
  void gapCaptureYieldsToScenarioCaptures() throws Exception {
    service.start(Set.of(8080));
    Path gap = tempDir.resolve("gap.pcapng");
    Path scenario = tempDir.resolve("scenario.pcapng");
    service.openCapture("gap", gap, true);

    network.deliver(LOOPBACK, frame(1));
    network.awaitProcessed(LOOPBACK);
    service.openCapture("scenario", scenario, false);
    network.deliver(LOOPBACK, frame(2));
    network.awaitProcessed(LOOPBACK);
    service.closeCapture("scenario");
    network.deliver(LOOPBACK, frame(3));
    network.awaitProcessed(LOOPBACK);
    service.closeCapture("gap");

    assertThat(markersIn(gap)).as("before and after the scenario").containsExactly(1, 3);
    assertThat(markersIn(scenario)).containsExactly(2);
  }

  @Test
  @DisplayName("a suspended capture writes nothing, but still keeps the gap capture out")
  void suspendedCaptureWritesNothingButStillExcludesTheGapCapture() throws Exception {
    service.start(Set.of(8080));
    Path gap = tempDir.resolve("gap.pcapng");
    Path scenario = tempDir.resolve("scenario.pcapng");
    service.openCapture("gap", gap, true);
    service.openCapture("scenario", scenario, false);

    assertThat(service.suspendCapture("scenario")).isTrue();
    network.deliver(LOOPBACK, frame(1));
    network.awaitProcessed(LOOPBACK);
    assertThat(service.resumeCapture("scenario")).isTrue();
    network.deliver(LOOPBACK, frame(2));
    network.awaitProcessed(LOOPBACK);
    service.closeCapture("scenario");
    service.closeCapture("gap");

    assertThat(markersIn(scenario)).as("not what arrived while suspended").containsExactly(2);
    assertThat(markersIn(gap)).as("the scenario was open all along").isEmpty();
  }

  @Test
  @DisplayName("suspending or resuming an unknown capture reports false")
  void suspendingAnUnknownCaptureReportsFalse() {
    service.start(Set.of(8080));

    assertThat(service.suspendCapture("nobody")).isFalse();
    assertThat(service.resumeCapture("nobody")).isFalse();
    assertThat(service.closeCapture("nobody")).isZero();
  }

  @Test
  @DisplayName("stop() closes every open capture into its file")
  void stopClosesEveryCapture() throws Exception {
    service.start(Set.of(8080));
    Path a = tempDir.resolve("a.pcapng");
    Path b = tempDir.resolve("b.pcapng");
    service.openCapture("a", a, false);
    service.openCapture("b", b, false);
    network.deliver(LOOPBACK, frame(1));
    network.awaitProcessed(LOOPBACK);

    service.stop();

    assertThat(markersIn(a)).containsExactly(1);
    assertThat(markersIn(b)).containsExactly(1);
  }

  @Test
  @DisplayName("a gap capture only yields to scenarios of its own client")
  void gapCaptureYieldsOnlyToItsOwnClient() throws Exception {
    PcapCaptureConfiguration settings = onInterfaces(LOOPBACK);
    Path gapA = tempDir.resolve("gap-a.pcapng");
    Path scenarioB = tempDir.resolve("scenario-b.pcapng");
    Path scenarioA = tempDir.resolve("scenario-a.pcapng");
    service.openCapture("gap-a", gapA, true, settings, Set.of(8080), "suite-a");
    service.openCapture("scenario-b", scenarioB, false, settings, Set.of(8080), "suite-b");

    network.deliver(LOOPBACK, frame(1));
    network.awaitProcessed(LOOPBACK);
    service.openCapture("scenario-a", scenarioA, false, settings, Set.of(8080), "suite-a");
    network.deliver(LOOPBACK, frame(2));
    network.awaitProcessed(LOOPBACK);
    service.closeCapture("gap-a");
    service.closeCapture("scenario-a");
    service.closeCapture("scenario-b");

    assertThat(markersIn(gapA)).as("suite B's scenario does not blind it").containsExactly(1);
    assertThat(markersIn(scenarioA)).containsExactly(2);
    assertThat(markersIn(scenarioB)).containsExactly(1, 2);
  }

  // ===== per-capture settings =====

  @Test
  @DisplayName("a capture only gets packets from the interfaces its own settings name")
  void capturesOnlyGetTheirOwnInterfaces() throws Exception {
    Path onLoopback = tempDir.resolve("loopback.pcapng");
    Path onEth0 = tempDir.resolve("eth0.pcapng");
    service.openCapture("lo", onLoopback, false, onInterfaces(LOOPBACK), Set.of(8080));
    service.openCapture("eth", onEth0, false, onInterfaces(ETH0), Set.of(8080));

    network.deliver(LOOPBACK, frame(1));
    network.deliver(ETH0, frame(2));
    network.awaitProcessed(LOOPBACK);
    network.awaitProcessed(ETH0);
    service.closeCapture("lo");
    service.closeCapture("eth");

    assertThat(markersIn(onLoopback)).containsExactly(1);
    assertThat(markersIn(onEth0)).containsExactly(2);
  }

  @Test
  @DisplayName("a capture on several interfaces gets one file with all of them, time-ordered")
  void captureOnSeveralInterfacesGetsOneMergedFile() throws Exception {
    Path file = tempDir.resolve("both.pcapng");
    service.openCapture("both", file, false, onInterfaces(LOOPBACK, ETH0), Set.of(8080));
    Instant t0 = Instant.parse("2026-01-01T00:00:00Z");

    network.deliver(LOOPBACK, frame(1), t0.plusMillis(100));
    network.deliver(ETH0, frame(2), t0.plusMillis(50));
    network.deliver(LOOPBACK, frame(3), t0.plusMillis(200));
    network.awaitProcessed(LOOPBACK);
    network.awaitProcessed(ETH0);
    assertThat(service.closeCapture("both")).isEqualTo(3);

    assertThat(markersIn(file)).as("by capture time, not by interface").containsExactly(2, 1, 3);
    assertThat(interfaceNamesIn(file))
        .as("each packet still says which interface it came from")
        .hasSize(2)
        .anyMatch(name -> name.endsWith(LOOPBACK))
        .anyMatch(name -> name.endsWith(ETH0));
  }

  @Test
  @DisplayName("a frame seen on two interfaces is written once if duplicates are to be dropped")
  void duplicatesAcrossInterfacesAreKeptOrDropped() throws Exception {
    Instant when = Instant.parse("2026-01-01T00:00:00Z");
    Path kept = tempDir.resolve("kept.pcapng");
    service.openCapture("kept", kept, false, onInterfaces(LOOPBACK, ETH0), Set.of(8080));
    network.deliver(LOOPBACK, frame(1), when);
    network.deliver(ETH0, frame(1), when);
    network.awaitProcessed(LOOPBACK);
    network.awaitProcessed(ETH0);
    service.closeCapture("kept");
    assertThat(markersIn(kept)).as("reported, not dropped, by default").containsExactly(1, 1);

    service.setDropDuplicatePackets(true);
    Path dropped = tempDir.resolve("dropped.pcapng");
    service.openCapture("dropped", dropped, false, onInterfaces(LOOPBACK, ETH0), Set.of(8080));
    network.deliver(LOOPBACK, frame(1), when);
    network.deliver(ETH0, frame(1), when);
    network.awaitProcessed(LOOPBACK);
    network.awaitProcessed(ETH0);
    service.closeCapture("dropped");
    assertThat(markersIn(dropped)).containsExactly(1);
  }

  @Test
  @DisplayName("a capture only gets what its own filter accepts, whatever the kernel let through")
  void capturesOnlyGetWhatTheirOwnFilterAccepts() throws Exception {
    network.filterAccepts("tcp port 1111", bytes -> bytes[0] == 1);
    network.filterAccepts("tcp port 2222", bytes -> bytes[0] == 2);
    Path a = tempDir.resolve("a.pcapng");
    Path b = tempDir.resolve("b.pcapng");
    service.openCapture("a", a, false, withFilter("tcp port 1111"), Set.of());
    service.openCapture("b", b, false, withFilter("tcp port 2222"), Set.of());

    network.deliver(LOOPBACK, frame(1));
    network.deliver(LOOPBACK, frame(2));
    network.deliver(LOOPBACK, frame(1));
    network.awaitProcessed(LOOPBACK);
    service.closeCapture("a");
    service.closeCapture("b");

    assertThat(markersIn(a)).containsExactly(1, 1);
    assertThat(markersIn(b)).containsExactly(2);
    assertThat(network.kernelFilters(LOOPBACK))
        .as("the kernel lets through the union of what the captures want")
        .last()
        .asString()
        .contains("tcp port 1111")
        .contains("tcp port 2222");
  }

  @Test
  @DisplayName("a capture without a filter of its own and without ports gets everything")
  void captureWithoutAnyFilterGetsEverything() throws Exception {
    Path file = tempDir.resolve("all.pcapng");
    service.openCapture("all", file, false, PcapCaptureConfiguration.builder().build(), Set.of());

    network.deliver(LOOPBACK, frame(1));
    network.deliver(LOOPBACK, frame(2));
    network.awaitProcessed(LOOPBACK);
    service.closeCapture("all");

    assertThat(markersIn(file)).containsExactly(1, 2);
    assertThat(network.compiledFilters(LOOPBACK)).isEmpty();
    assertThat(network.kernelFilters(LOOPBACK)).as("no filter at all").allMatch(String::isEmpty);
  }

  @Test
  @DisplayName("ports that appear later widen a port-filtered capture and the kernel filter")
  void laterPortsWidenPortFilteredCaptures() {
    service.openCapture(
        "s", tempDir.resolve("s.pcapng"), false, onInterfaces(LOOPBACK), Set.of(8080));
    assertThat(network.compiledFilters(LOOPBACK))
        .containsExactly(BpfFilterBuilder.forTcpPorts(Set.of(8080)));

    service.alsoCapturePorts(Set.of(9090));

    assertThat(network.compiledFilters(LOOPBACK))
        .last()
        .isEqualTo(BpfFilterBuilder.forTcpPorts(Set.of(8080, 9090)));
    assertThat(network.kernelFilters(LOOPBACK).get(network.kernelFilters(LOOPBACK).size() - 1))
        .contains("8080")
        .contains("9090");

    int compiled = network.compiledFilters(LOOPBACK).size();
    service.alsoCapturePorts(Set.of(9090));
    assertThat(network.compiledFilters(LOOPBACK)).as("nothing new").hasSize(compiled);
  }

  @Test
  @DisplayName("a capture with a filter of its own is never widened by later ports")
  void manuallyFilteredCapturesAreNeverWidened() {
    service.openCapture(
        "s", tempDir.resolve("s.pcapng"), false, withFilter("tcp port 1111"), Set.of(8080));

    service.alsoCapturePorts(Set.of(9090));

    assertThat(network.compiledFilters(LOOPBACK)).containsExactly("tcp port 1111");
  }

  @Test
  @DisplayName("alsoCapturePorts() widens the filter of start()-ed capture, and only if running")
  void alsoCapturePortsWidensStartedCapture() {
    service.alsoCapturePorts(Set.of(9090)); // not running: nothing happens
    assertThat(network.opened()).isEmpty();

    service.start(Set.of(8080));
    service.alsoCapturePorts(Set.of(9090));

    assertThat(network.kernelFilters(LOOPBACK))
        .last()
        .isEqualTo(BpfFilterBuilder.forTcpPorts(Set.of(8080, 9090)));

    service.alsoCapturePorts(Set.of());
    service.alsoCapturePorts(null);
    assertThat(network.kernelFilters(LOOPBACK)).hasSize(2);
  }

  @Test
  @DisplayName("capture started without ports is already unrestricted and is never narrowed")
  void unrestrictedCaptureIsNeverNarrowed() {
    service.start(Set.of());

    service.alsoCapturePorts(Set.of(9090));

    assertThat(network.kernelFilters(LOOPBACK)).isEmpty();
  }

  @Test
  @DisplayName("a manual filter on the engine is never widened")
  void manualEngineFilterIsNeverWidened() {
    service.setManualBpfFilter("udp port 53");
    service.start(Set.of(8080));

    service.alsoCapturePorts(Set.of(9090));

    assertThat(network.kernelFilters(LOOPBACK)).containsExactly("udp port 53");
  }

  @Test
  @DisplayName("snaplen and buffer are fixed per interface; a later capture is told it got these")
  void snaplenAndBufferAreFixedPerInterface() {
    LogCapture log = captureLogOf(FanOutPcapCaptureService.class);
    var first = PcapCaptureConfiguration.builder().snaplenKb(32).bufferSizeKb(2048).build();
    var second = PcapCaptureConfiguration.builder().snaplenKb(8).bufferSizeKb(4096).build();

    service.openCapture("first", tempDir.resolve("first.pcapng"), false, first, Set.of(8080));
    service.openCapture("second", tempDir.resolve("second.pcapng"), false, second, Set.of(8080));

    assertThat(network.opened()).containsExactly(new OpenedHandle(LOOPBACK, 32 * KB, 2 * MB));
    assertThat(log.warnings())
        .anyMatch(warning -> warning.contains("snaplen of " + 32 * KB))
        .anyMatch(warning -> warning.contains("buffer of " + 2 * MB));

    log.forget();
    service.openCapture("third", tempDir.resolve("third.pcapng"), false, first, Set.of(8080));
    assertThat(log.warnings()).as("same sizes: nothing to say").isEmpty();
  }

  @Test
  @DisplayName("a capture opens with the interfaces that can be opened, and fails with none")
  void captureOpensWithWhatCanBeOpened() {
    network.failToOpen(ETH0);

    assertThat(
            service.openCapture(
                "partly",
                tempDir.resolve("partly.pcapng"),
                false,
                onInterfaces(LOOPBACK, ETH0),
                Set.of(8080)))
        .isTrue();
    assertThat(
            service.openCapture(
                "none", tempDir.resolve("none.pcapng"), false, onInterfaces(ETH0), Set.of(8080)))
        .isFalse();
    assertThat(
            service.openCapture(
                "unknown",
                tempDir.resolve("unknown.pcapng"),
                false,
                onInterfaces("no-such-interface"),
                Set.of(8080)))
        .isFalse();
    assertThat(service.getCapturePacketCount("none")).isZero();
    assertThat(service.isEnabled()).as("the failed ones did not stop the capture").isTrue();
  }

  // ===== things that go wrong =====

  @Test
  @DisplayName("a handle whose filter cannot be applied is closed again, not left open unused")
  void handleWithoutItsFilterIsClosed() {
    network.failFilters(LOOPBACK);

    service.start(Set.of(8080));

    assertThat(service.isEnabled()).isFalse();
    assertThat(network.isOpen(LOOPBACK)).isFalse();
  }

  @Test
  @DisplayName("a filter that cannot be widened is reported, and capture carries on")
  void filterThatCannotBeWidenedIsReported() {
    LogCapture log = captureLogOf(PcapCaptureEngine.class);
    service.start(Set.of(8080));
    network.failFilters(LOOPBACK);

    service.alsoCapturePorts(Set.of(9090));

    assertThat(log.warnings()).anyMatch(warning -> warning.contains("Failed to widen"));
    assertThat(service.isEnabled()).isTrue();
  }

  @Test
  @DisplayName("a capture filter that cannot be widened is reported, and the capture carries on")
  void captureFilterThatCannotBeWidenedIsReported() throws Exception {
    LogCapture log = captureLogOf(FanOutPcapCaptureService.class);
    Path file = tempDir.resolve("s.pcapng");
    service.openCapture("s", file, false, onInterfaces(LOOPBACK), Set.of(8080));
    network.failFilters(LOOPBACK);

    service.alsoCapturePorts(Set.of(9090));

    assertThat(log.warnings()).anyMatch(warning -> warning.contains("capture's filter"));
    network.deliver(LOOPBACK, frame(1));
    network.awaitProcessed(LOOPBACK);
    service.closeCapture("s");
    assertThat(markersIn(file)).containsExactly(1);
  }

  @Test
  @DisplayName("a capture whose filter the kernel refuses is not opened, the others are unharmed")
  void captureWhoseFilterIsRefusedIsNotOpened() throws Exception {
    LogCapture log = captureLogOf(FanOutPcapCaptureService.class);
    Path good = tempDir.resolve("good.pcapng");
    service.openCapture("good", good, false, withFilter("tcp port 1111"), Set.of());
    network.failFilters(LOOPBACK);

    boolean opened =
        service.openCapture(
            "refused",
            tempDir.resolve("refused.pcapng"),
            false,
            withFilter("tcp port 2222"),
            Set.of());

    assertThat(opened).isFalse();
    assertThat(log.warnings())
        .anyMatch(warning -> warning.contains("Failed to open pcap capture refused"));
    network.deliver(LOOPBACK, frame(1));
    network.awaitProcessed(LOOPBACK);
    service.closeCapture("good");
    assertThat(markersIn(good)).containsExactly(1);
  }

  @Test
  @DisplayName("a filter that cannot be compiled does not stay in the kernel filter")
  void invalidFilterDoesNotStayInTheKernelFilter() throws Exception {
    Path good = tempDir.resolve("good.pcapng");
    service.openCapture("good", good, false, withFilter("tcp port 1111"), Set.of());
    network.refuseFiltersContaining(LOOPBACK, "invalid");

    boolean opened =
        service.openCapture(
            "invalid", tempDir.resolve("invalid.pcapng"), false, withFilter("invalid"), Set.of());
    boolean laterOpened =
        service.openCapture(
            "later", tempDir.resolve("later.pcapng"), false, withFilter("tcp port 3333"), Set.of());

    assertThat(opened).isFalse();
    assertThat(laterOpened).isTrue();
    assertThat(network.kernelFilters(LOOPBACK))
        .last()
        .asString()
        .contains("3333")
        .doesNotContain("invalid");
  }

  @Test
  @DisplayName("statistics that cannot be read do not get in the way of stopping")
  void unreadableStatisticsDoNotPreventStopping() {
    LogCapture log = captureLogOf(PcapCaptureEngine.class);
    service.start(Set.of(8080));
    network.failStats(LOOPBACK);

    service.stop();

    assertThat(service.isEnabled()).isFalse();
    assertThat(network.isOpen(LOOPBACK)).isFalse();
    assertThat(log.warnings()).noneMatch(warning -> warning.contains("dropping packets"));
  }

  @Test
  @DisplayName("a capture whose file cannot be created is not opened, in either way of opening")
  void captureWhoseFileCannotBeCreatedIsNotOpened() throws Exception {
    LogCapture log = captureLogOf(PcapCaptureEngine.class);
    Path notADirectory = Files.writeString(tempDir.resolve("blocker"), "x");
    Path impossible = notADirectory.resolve("s.pcapng");
    service.start(Set.of(8080));

    assertThat(service.openCapture("legacy", impossible, false)).isFalse();
    assertThat(
            service.openCapture(
                "with-settings", impossible, false, onInterfaces(LOOPBACK), Set.of(8080)))
        .isFalse();

    assertThat(log.warnings())
        .anyMatch(warning -> warning.contains("Failed to open per-interface"));
    assertThat(service.getCapturePacketCount("legacy")).isZero();
  }

  @Test
  @DisplayName("a capture file that cannot be written is reported when it is closed")
  void captureFileThatCannotBeWrittenIsReportedAtClose() throws Exception {
    LogCapture log = captureLogOf(PcapCaptureEngine.class);
    Path busy = Files.createDirectories(tempDir.resolve("busy.pcapng"));
    Files.writeString(busy.resolve("occupant"), "x");
    service.start(Set.of(8080));
    service.openCapture("s", busy, false);
    network.deliver(LOOPBACK, frame(1));
    network.awaitProcessed(LOOPBACK);

    assertThat(service.closeCapture("s")).as("the packet was written, only not moved").isEqualTo(1);

    assertThat(log.warnings()).anyMatch(warning -> warning.contains("Failed to merge"));
    assertThat(busy.resolve("occupant")).exists();
  }

  @Test
  @DisplayName("the first capture with settings starts the capture, no start() needed")
  void capturesStartCaptureWithoutStart() {
    assertThat(service.isEnabled()).isFalse();

    service.openCapture("s", tempDir.resolve("s.pcapng"), false, onInterfaces(LOOPBACK), Set.of());

    assertThat(service.isEnabled()).isTrue();
    assertThat(network.isOpen(LOOPBACK)).isTrue();
  }
}
