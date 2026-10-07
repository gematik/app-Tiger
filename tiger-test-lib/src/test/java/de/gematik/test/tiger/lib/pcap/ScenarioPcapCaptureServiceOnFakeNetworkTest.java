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

import static de.gematik.test.tiger.lib.pcap.PcapTestFiles.frame;
import static de.gematik.test.tiger.lib.pcap.PcapTestFiles.markersIn;
import static de.gematik.test.tiger.testutils.pcap.FakePcapNetwork.LOOPBACK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.gematik.test.tiger.proxy.handler.pcap.PcapCaptureEngine;
import de.gematik.test.tiger.testutils.pcap.FakePcapNetwork;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

@DisplayName("ScenarioPcapCaptureService on a fake network")
class ScenarioPcapCaptureServiceOnFakeNetworkTest {

  private static final String ETH0 = "fake-eth0";
  private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

  @TempDir private Path tempDir;
  private final FakePcapNetwork network = new FakePcapNetwork(ETH0);
  private final ScenarioPcapCaptureService service = new FakeNetworkScenarioService(network);

  @AfterEach
  void stopTheService() {
    service.stop();
  }

  private void deliver(int marker, long offsetMillis) {
    network.deliver(LOOPBACK, frame(marker), T0.plusMillis(offsetMillis));
    network.awaitProcessed(LOOPBACK);
  }

  // ===== the suite-wide file =====

  @Test
  @DisplayName("the suite file gets everything, and rotating hands over to the next file")
  void suiteFileGetsEverythingUntilItIsRotated() throws Exception {
    Path first = tempDir.resolve("first.pcapng");
    Path second = tempDir.resolve("second.pcapng");
    service.start(Set.of(8080));
    service.rotate(first);

    deliver(1, 0);
    deliver(2, 10);
    assertThat(service.getCurrentDumperPacketCount()).isEqualTo(2);
    service.rotate(second);
    deliver(3, 20);
    assertThat(service.getCurrentDumperPacketCount()).as("of the new file only").isEqualTo(1);
    service.stop();

    assertThat(markersIn(first)).containsExactly(1, 2);
    assertThat(markersIn(second)).containsExactly(3);
  }

  @Test
  @DisplayName("packets that arrive before there is any file are dropped, not an error")
  void packetsWithoutAnyFileAreDropped() {
    service.start(Set.of(8080));

    deliver(1, 0);

    assertThat(service.isEnabled()).isTrue();
    assertThat(service.getCurrentDumperPacketCount()).isZero();
  }

  @Test
  @DisplayName("a suspended capture reads packets and writes them nowhere")
  void suspendedCaptureWritesNothing() throws Exception {
    Path file = tempDir.resolve("suite.pcapng");
    service.start(Set.of(8080));
    service.rotate(file);

    service.suspend();
    deliver(1, 0);
    service.resume();
    deliver(2, 10);
    service.stop();

    assertThat(markersIn(file)).containsExactly(2);
  }

  @Test
  @DisplayName("a capture resumed before it ever had a file opens one on its first packet")
  void captureResumedWithoutFileOpensOneLazily() throws Exception {
    Set<Path> before = pcapFilesInEvidenceDirectory();
    service.start(Set.of(8080));
    service.suspend();

    service.resume();
    deliver(7, 0);
    service.stop();

    Set<Path> created = pcapFilesInEvidenceDirectory();
    created.removeAll(before);
    try {
      assertThat(created).hasSize(1);
      Path file = created.iterator().next();
      assertThat(file.getFileName().toString()).startsWith("pcap_resumed_");
      assertThat(markersIn(file)).containsExactly(7);
    } finally {
      for (Path file : created) {
        Files.deleteIfExists(file);
      }
    }
  }

  private static Set<Path> pcapFilesInEvidenceDirectory() throws IOException {
    Path evidences = Path.of("target", "evidences");
    if (!Files.isDirectory(evidences)) {
      return new HashSet<>();
    }
    try (Stream<Path> files = Files.list(evidences)) {
      return new HashSet<>(
          files.filter(file -> file.getFileName().toString().startsWith("pcap_resumed_")).toList());
    }
  }

  @Test
  @DisplayName("stop() closes the suite file and the files of scenarios that never closed theirs")
  void stopClosesWhatIsStillOpen() throws Exception {
    Path suite = tempDir.resolve("suite.pcapng");
    Path scenario = tempDir.resolve("scenario.pcapng");
    service.start(Set.of(8080));
    service.rotate(suite);
    service.openDumper(scenario, false);
    deliver(1, 0);

    service.stop();

    assertThat(markersIn(scenario)).containsExactly(1);
    assertThat(markersIn(suite)).as("the scenario had it").isEmpty();
    assertThat(service.isEnabled()).isFalse();
  }

  @Test
  @DisplayName("the kernel dropping packets is reported at each rotation")
  void droppedPacketsAreReportedAtRotation() {
    ListAppender<ILoggingEvent> log = new ListAppender<>();
    Logger logger = (Logger) LoggerFactory.getLogger(PcapCaptureEngine.class);
    log.start();
    logger.addAppender(log);
    try {
      service.start(Set.of(8080));
      network.dropPackets(LOOPBACK, 5, 2);

      service.rotate(tempDir.resolve("rotated.pcapng"));

      assertThat(log.list)
          .filteredOn(event -> event.getLevel() == Level.WARN)
          .anyMatch(event -> event.getFormattedMessage().contains("dropping packets"));
    } finally {
      logger.detachAppender(log);
    }
  }

  // ===== one file per scenario =====

  @Test
  @DisplayName("every scenario's file gets every packet, and the suite file only the rest")
  void scenarioFilesFanOutAndTheSuiteFileGetsTheGaps() throws Exception {
    Path suite = tempDir.resolve("suite.pcapng");
    Path a = tempDir.resolve("a.pcapng");
    Path b = tempDir.resolve("b.pcapng");
    service.start(Set.of(8080));
    service.rotate(suite);

    deliver(1, 0);
    service.openDumper(a, false);
    deliver(2, 10);
    service.openDumper(b, false);
    deliver(3, 20);
    assertThat(service.closeDumper(a)).isEqualTo(2);
    deliver(4, 30);
    assertThat(service.closeDumper(b)).isEqualTo(2);
    deliver(5, 40);
    service.stop();

    assertThat(markersIn(a)).containsExactly(2, 3);
    assertThat(markersIn(b)).containsExactly(3, 4);
    assertThat(markersIn(suite)).as("outside every scenario").containsExactly(1, 5);
  }

  @Test
  @DisplayName("closing a scenario that was never opened is a no-op")
  void closingAnUnknownScenarioIsANoop() {
    service.start(Set.of(8080));

    assertThat(service.closeDumper(tempDir.resolve("never.pcapng"))).isZero();
    assertThat(service.closeDumperRawFiles(tempDir.resolve("never.pcapng"))).isEmpty();
  }

  @Test
  @DisplayName("a scenario file can be taken back unmerged, one file per interface")
  void scenarioFileCanBeTakenBackUnmerged() throws Exception {
    service.setInterfaceNames(List.of(LOOPBACK, ETH0));
    service.start(Set.of(8080));
    Path scenario = tempDir.resolve("scenario.pcapng");
    service.openDumper(scenario, false);
    network.deliver(LOOPBACK, frame(1), T0);
    network.deliver(ETH0, frame(2), T0.plusMillis(10));
    network.awaitProcessed(LOOPBACK);
    network.awaitProcessed(ETH0);

    List<File> raw = service.closeDumperRawFiles(scenario);

    assertThat(raw).hasSize(2);
    assertThat(scenario).as("nothing merged into the target").doesNotExist();
    List<Integer> markers = new java.util.ArrayList<>();
    for (File file : raw) {
      markers.addAll(markersIn(file.toPath()));
    }
    assertThat(markers).containsExactlyInAnyOrder(1, 2);
    assertThat(service.currentScenarioFile()).as("forgotten").isEmpty();
    raw.forEach(File::delete);
  }

  @Test
  @DisplayName("a scenario on several interfaces gets one file with all of them, in time order")
  void scenarioOnSeveralInterfacesGetsOneMergedFile() throws Exception {
    service.setInterfaceNames(List.of(LOOPBACK, ETH0));
    service.start(Set.of(8080));
    Path scenario = tempDir.resolve("scenario.pcapng");
    service.openDumper(scenario, false);
    network.deliver(LOOPBACK, frame(1), T0.plusMillis(100));
    network.deliver(ETH0, frame(2), T0.plusMillis(50));
    network.awaitProcessed(LOOPBACK);
    network.awaitProcessed(ETH0);

    assertThat(service.closeDumper(scenario)).isEqualTo(2);

    assertThat(markersIn(scenario)).containsExactly(2, 1);
  }

  @Test
  @DisplayName("the calling thread's current scenario file is the one it opened last")
  void currentScenarioFileIsTheOneLastOpened() {
    service.start(Set.of(8080));
    assertThat(service.currentScenarioFile()).isEmpty();

    Path scenario = tempDir.resolve("scenario.pcapng");
    service.openDumper(scenario, false);
    assertThat(service.currentScenarioFile()).contains(scenario);

    service.closeDumper(scenario);
    assertThat(service.currentScenarioFile()).isEmpty();
  }

  @Test
  @DisplayName("opening a scenario file needs a running capture, and failing to is not fatal")
  void openingAScenarioFileIsSafe() throws Exception {
    service.openDumper(tempDir.resolve("early.pcapng"), false); // not started: nothing happens
    assertThat(service.currentScenarioFile()).isEmpty();

    service.start(Set.of(8080));
    Path notADirectory = Files.writeString(tempDir.resolve("blocker"), "x");
    Path impossible = notADirectory.resolve("scenario.pcapng");

    assertThatCode(() -> service.openDumper(impossible, false)).doesNotThrowAnyException();

    assertThat(service.isEnabled()).isTrue();
    assertThat(service.closeDumper(impossible)).as("nothing was ever opened").isZero();
  }

  // ===== suspending a scenario =====

  @Test
  @DisplayName("a suspended scenario writes nothing, but still keeps the suite file out")
  void suspendedScenarioWritesNothingAndStillExcludesTheSuiteFile() throws Exception {
    Path suite = tempDir.resolve("suite.pcapng");
    Path scenario = tempDir.resolve("scenario.pcapng");
    service.start(Set.of(8080));
    service.rotate(suite);
    service.openDumper(scenario, false);

    service.suspend();
    assertThat(service.isEnabled()).isFalse();
    deliver(1, 0);
    service.resume();
    assertThat(service.isEnabled()).isTrue();
    deliver(2, 10);
    service.closeDumper(scenario);
    service.stop();

    assertThat(markersIn(scenario)).containsExactly(2);
    assertThat(markersIn(suite)).isEmpty();
  }

  @Test
  @DisplayName("a scenario that starts suspended opens its file on the first resume only")
  void scenarioStartingSuspendedOpensItsFileOnResume() throws Exception {
    Path scenario = tempDir.resolve("scenario.pcapng");
    service.start(Set.of(8080));
    service.openDumper(scenario, true);

    deliver(1, 0);
    service.resume();
    deliver(2, 10);
    service.resume(); // already resumed: nothing to do
    assertThat(service.closeDumper(scenario)).isEqualTo(1);

    assertThat(markersIn(scenario)).containsExactly(2);
  }

  @Test
  @DisplayName("resuming a scenario does nothing while the whole capture is suspended")
  void resumingAScenarioWaitsForTheWholeCapture() throws Exception {
    Path scenario = tempDir.resolve("scenario.pcapng");
    service.start(Set.of(8080));
    service.openDumper(scenario, true);
    // Something outside any scenario suspends the whole capture.
    Thread outsideAnyScenario = new Thread(service::suspend);
    outsideAnyScenario.start();
    outsideAnyScenario.join();

    service.resume();

    assertThat(service.isEnabled()).as("the capture as a whole is still suspended").isFalse();
    assertThat(service.closeDumper(scenario)).isZero();
    assertThat(scenario).as("its file was never opened").doesNotExist();
  }

  @Test
  @DisplayName("a scenario that cannot open its file on resume stays quiet instead of failing")
  void scenarioThatCannotOpenItsFileOnResumeStaysQuiet() throws Exception {
    Path notADirectory = Files.writeString(tempDir.resolve("blocker"), "x");
    Path impossible = notADirectory.resolve("scenario.pcapng");
    service.start(Set.of(8080));
    service.openDumper(impossible, true);

    service.resume();
    deliver(1, 0);

    assertThat(service.closeDumper(impossible)).isZero();
    assertThat(service.isEnabled()).isTrue();
  }
}
