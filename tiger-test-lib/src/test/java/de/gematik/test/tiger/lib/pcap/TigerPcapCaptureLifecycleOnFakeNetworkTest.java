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
import static org.mockito.Mockito.mock;

import de.gematik.test.tiger.common.data.config.tigerproxy.PcapCaptureConfiguration;
import de.gematik.test.tiger.testenvmgr.env.TigerServerStatusUpdate;
import de.gematik.test.tiger.testenvmgr.env.TigerStatusUpdate;
import de.gematik.test.tiger.testutils.pcap.FakePcapNetwork;
import io.cucumber.plugin.event.TestCase;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("TigerPcapCaptureLifecycle on a fake network")
class TigerPcapCaptureLifecycleOnFakeNetworkTest {

  private static final Path EVIDENCES = Path.of("target", "evidences");
  private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

  @TempDir private Path tempDir;
  private final FakePcapNetwork network = new FakePcapNetwork();
  private final ExecutorService scenarioThreads = Executors.newCachedThreadPool();
  private final List<FakeRemoteProxy> remoteProxies = new java.util.ArrayList<>();
  private Set<Path> evidencesBefore;

  private class Lifecycle extends TigerPcapCaptureLifecycle {
    final TigerPcapCaptureConfig config;
    Set<Integer> ports = Set.of(8080);
    Map<String, Duration> remotes = Map.of();
    boolean serenityAvailable = false;
    RuntimeException failToCreateService;
    ScenarioPcapCaptureService service;
    boolean failToDiscoverRemotes = false;
    boolean failToRegisterForUpdates = false;
    int portLookupsBeforeFailing = Integer.MAX_VALUE;
    int configLoadsBeforeFailing = Integer.MAX_VALUE;
    private final AtomicInteger portLookups = new AtomicInteger();
    private final AtomicInteger configLoads = new AtomicInteger();

    Lifecycle(TigerPcapCaptureConfig config) {
      this.config = config;
    }

    @Override
    protected TigerPcapCaptureConfig loadPcapConfig() {
      if (configLoads.getAndIncrement() >= configLoadsBeforeFailing) {
        throw new IllegalStateException("configuration gone");
      }
      return config;
    }

    @Override
    protected ScenarioPcapCaptureService createCaptureService(TigerPcapCaptureConfig pcapConfig) {
      if (failToCreateService != null) {
        throw failToCreateService;
      }
      return service != null ? service : new FakeNetworkScenarioService(network);
    }

    @Override
    protected Set<Integer> discoverProxyPorts() {
      if (portLookups.getAndIncrement() >= portLookupsBeforeFailing) {
        throw new IllegalStateException("no environment");
      }
      return ports;
    }

    @Override
    protected Map<String, Duration> discoverRemoteProxiesWithClockOffsets() {
      if (failToDiscoverRemotes) {
        throw new IllegalStateException("no environment");
      }
      return remotes;
    }

    @Override
    protected PcapCaptureConfiguration resolveRemoteCaptureConfig(String proxyUrl) {
      return PcapCaptureConfiguration.builder().interfaceNames(List.of("eth0")).build();
    }

    @Override
    protected boolean isSerenityAvailable() {
      return serenityAvailable;
    }

    boolean failMergesWithRemotes = false;

    @Override
    protected long mergeAllSources(
        Map<String, RemotePcapMetadata> remotePcapMetadata,
        String scenarioId,
        int dataVariantIndex,
        Path localFile,
        List<File> localTempFiles) {
      if (failMergesWithRemotes && !remotePcapMetadata.isEmpty()) {
        return -1;
      }
      return super.mergeAllSources(
          remotePcapMetadata, scenarioId, dataVariantIndex, localFile, localTempFiles);
    }

    @Override
    protected Path serenityOutputDirectory() {
      return tempDir.resolve("serenity");
    }

    @Override
    protected void registerEvidence(Path pcapFile) {
      try {
        Path downloads = Files.createDirectories(serenityOutputDirectory().resolve("downloadable"));
        Files.copy(
            pcapFile,
            downloads.resolve("downloadable-" + UUID.randomUUID() + "-" + pcapFile.getFileName()));
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }

    @Override
    protected void registerEnvUpdateListener() {
      if (failToRegisterForUpdates) {
        throw new IllegalStateException("no environment");
      }
    }
  }

  private static TigerPcapCaptureConfig config(boolean splitByTestcase) {
    TigerPcapCaptureConfig config = new TigerPcapCaptureConfig();
    config.setEnabled(true);
    config.setSplitByTestcase(splitByTestcase);
    config.setFilename("${scenarioId}.pcapng");
    return config;
  }

  private static TigerPcapCaptureConfig remoteConfig(boolean splitByTestcase) {
    TigerPcapCaptureConfig config = config(splitByTestcase);
    config.setRemoteProxies(true);
    return config;
  }

  @BeforeEach
  void rememberEvidences() throws IOException {
    evidencesBefore = evidenceFiles();
  }

  @AfterEach
  void cleanUp() throws IOException {
    scenarioThreads.shutdownNow();
    remoteProxies.forEach(FakeRemoteProxy::close);
    Set<Path> left = evidenceFiles();
    left.removeAll(evidencesBefore);
    for (Path file : left) {
      Files.deleteIfExists(file);
    }
    ScenarioPcapCaptureService.setInstance(null);
    RemotePcapCoordinator.setInstance(null);
  }

  private static Set<Path> evidenceFiles() throws IOException {
    if (!Files.isDirectory(EVIDENCES)) {
      return new HashSet<>();
    }
    try (Stream<Path> files = Files.list(EVIDENCES)) {
      return new HashSet<>(files.toList());
    }
  }

  private List<Path> newEvidence(String prefix) throws IOException {
    Set<Path> now = evidenceFiles();
    now.removeAll(evidencesBefore);
    return now.stream().filter(file -> file.getFileName().toString().startsWith(prefix)).toList();
  }

  private Path onlyNewEvidence(String prefix) throws IOException {
    List<Path> files = newEvidence(prefix);
    assertThat(files).as("new files starting with %s", prefix).hasSize(1);
    return files.get(0);
  }

  private void deliver(int marker, long offsetMillis) {
    network.deliver(LOOPBACK, frame(marker), T0.plusMillis(offsetMillis));
    network.awaitProcessed(LOOPBACK);
  }

  private void onScenarioThread(Runnable scenario) throws Exception {
    scenarioThreads.submit(scenario).get(30, TimeUnit.SECONDS);
  }

  private FakeRemoteProxy remoteProxyCapturing(int marker, Instant at) throws IOException {
    Path file =
        PcapTestFiles.writeCapture(
            tempDir.resolve("remote-" + marker + ".pcapng"), "eth0", marker, at);
    FakeRemoteProxy proxy = new FakeRemoteProxy(Files.readAllBytes(file));
    remoteProxies.add(proxy);
    return proxy;
  }

  private static final TestCase TEST_CASE = mock(TestCase.class);

  // ===== local capture =====

  @Test
  @DisplayName("a scenario's file holds its traffic, the suite file what happened outside")
  void scenarioFileHoldsItsTrafficAndTheSuiteFileTheRest() throws Exception {
    Lifecycle lifecycle = new Lifecycle(config(true));
    lifecycle.serenityAvailable = true; // registering the files as evidence must not get in the way

    lifecycle.onTestRunStarted();
    deliver(1, 0);
    lifecycle.onTestCaseStarted(TEST_CASE, "lifecycle-local", -1);
    deliver(2, 10);
    lifecycle.onTestCaseFinished(TEST_CASE, "lifecycle-local", -1);
    deliver(3, 20);
    lifecycle.onTestRunFinished();

    assertThat(markersIn(onlyNewEvidence("lifecycle-local"))).containsExactly(2);
    assertThat(markersIn(onlyNewEvidence("suite_"))).containsExactly(1, 3);
    assertThat(ScenarioPcapCaptureService.getInstance()).as("forgotten at the end").isNull();
  }

  @Test
  @DisplayName("without splitting by test case, the suite file holds everything")
  void suiteFileHoldsEverythingWithoutSplitting() throws Exception {
    Lifecycle lifecycle = new Lifecycle(config(false));

    lifecycle.onTestRunStarted();
    lifecycle.onTestCaseStarted(TEST_CASE, "lifecycle-unsplit", -1);
    deliver(1, 0);
    lifecycle.onTestCaseFinished(TEST_CASE, "lifecycle-unsplit", -1);
    deliver(2, 10);
    lifecycle.onTestRunFinished();

    assertThat(newEvidence("lifecycle-unsplit")).isEmpty();
    assertThat(markersIn(onlyNewEvidence("suite_"))).containsExactly(1, 2);
  }

  @Test
  @DisplayName("a run that starts suspended captures nothing until it is resumed")
  void runStartingSuspendedCapturesNothingUntilResumed() throws Exception {
    TigerPcapCaptureConfig config = config(true);
    config.setStartSuspended(true);
    Lifecycle lifecycle = new Lifecycle(config);

    lifecycle.onTestRunStarted();
    lifecycle.onTestCaseStarted(TEST_CASE, "lifecycle-suspended", -1);
    deliver(1, 0);
    ScenarioPcapCaptureService.getInstance().resume();
    deliver(2, 10);
    lifecycle.onTestCaseFinished(TEST_CASE, "lifecycle-suspended", -1);
    lifecycle.onTestRunFinished();

    assertThat(newEvidence("suite_")).as("no suite file was ever opened").isEmpty();
    assertThat(newEvidence("pcap_resumed")).as("not in a global resume file").isEmpty();
    assertThat(markersIn(onlyNewEvidence("lifecycle-suspended")))
        .as("only what came after the resume, in the scenario's own file")
        .containsExactly(2);
  }

  @Test
  @DisplayName("an engine that cannot be set up leaves the run without capture, not failing")
  void failingSetUpLeavesTheRunWithoutCapture() throws Exception {
    Lifecycle lifecycle = new Lifecycle(config(true));
    lifecycle.failToCreateService = new IllegalStateException("no engine");

    assertThatCode(
            () -> {
              lifecycle.onTestRunStarted();
              lifecycle.onTestCaseStarted(TEST_CASE, "lifecycle-none", -1);
              lifecycle.onTestCaseFinished(TEST_CASE, "lifecycle-none", -1);
              lifecycle.onTestRunFinished();
            })
        .doesNotThrowAnyException();

    assertThat(newEvidence("")).isEmpty();
    assertThat(network.opened()).isEmpty();
  }

  @Test
  @DisplayName(
      "servers that appear later widen the capture filter, at once and at the next scenario")
  void laterServersWidenTheFilter() {
    Lifecycle lifecycle = new Lifecycle(config(true));
    lifecycle.onTestRunStarted();
    lifecycle.ports = Set.of(8080, 9090);

    lifecycle.receiveTestEnvUpdate(
        TigerStatusUpdate.builder()
            .serverUpdate(
                new LinkedHashMap<>(Map.of("late", TigerServerStatusUpdate.builder().build())))
            .build());

    assertThat(network.kernelFilters(LOOPBACK)).last().asString().contains("8080").contains("9090");

    lifecycle.ports = Set.of(8080, 9090, 7070);
    lifecycle.onTestCaseStarted(TEST_CASE, "lifecycle-widen", -1);
    assertThat(network.kernelFilters(LOOPBACK)).last().asString().contains("7070");

    lifecycle.onTestCaseFinished(TEST_CASE, "lifecycle-widen", -1);
    lifecycle.onTestRunFinished();
  }

  @Test
  @DisplayName("a failure while looking for servers does not get into the way of the update")
  void failureWhileLookingForServersIsContained() {
    Lifecycle lifecycle =
        new Lifecycle(config(true)) {
          @Override
          protected Set<Integer> discoverProxyPorts() {
            if (ports.isEmpty()) {
              throw new IllegalStateException("no environment");
            }
            return ports;
          }
        };
    lifecycle.onTestRunStarted();
    lifecycle.ports = Set.of();

    assertThatCode(
            () ->
                lifecycle.receiveTestEnvUpdate(
                    TigerStatusUpdate.builder()
                        .serverUpdate(
                            new LinkedHashMap<>(
                                Map.of("late", TigerServerStatusUpdate.builder().build())))
                        .build()))
        .doesNotThrowAnyException();

    lifecycle.onTestRunFinished();
  }

  // ===== capture on remote proxies =====

  @Test
  @DisplayName("a remote proxy's capture is merged into the scenario file and into the suite file")
  void remoteCaptureIsMergedIntoScenarioAndSuiteFiles() throws Exception {
    // The remote clock is a second ahead: what it stamped at +3 s really happened at +2 s.
    FakeRemoteProxy remote = remoteProxyCapturing(9, T0.plusSeconds(3));
    Lifecycle lifecycle = new Lifecycle(remoteConfig(true));
    lifecycle.remotes = Map.of(remote.url(), Duration.ofSeconds(1));

    lifecycle.onTestRunStarted();
    deliver(5, 0);
    assertThat(remote.startBodies()).singleElement().asString().contains("\"gap\":true");
    onScenarioThread(
        () -> {
          lifecycle.onTestCaseStarted(TEST_CASE, "lifecycle-remote", -1);
          deliver(1, 2500);
          RemotePcapCoordinator.getInstance().suspend();
          RemotePcapCoordinator.getInstance().resume();
          lifecycle.onTestCaseFinished(TEST_CASE, "lifecycle-remote", -1);
        });
    lifecycle.onTestRunFinished();

    assertThat(remote.startBodies()).hasSize(2);
    assertThat(remote.startBodies().get(1))
        .contains("\"gap\":false")
        .contains("\"interfaceNames\":[\"eth0\"]");
    assertThat(markersIn(onlyNewEvidence("lifecycle-remote.pcapng")))
        .as("the remote packet, clock-corrected, comes first")
        .containsExactly(9, 1);
    assertThat(markersIn(onlyNewEvidence("suite_")))
        .as("what was captured outside the scenario, here and there")
        .containsExactly(5, 9);
    assertThat(remote.calls())
        .as("every call carried the capture's secret")
        .containsExactly("suspend", "resume", "stop", "download", "stop", "download");
  }

  @Test
  @DisplayName("a scenario does not wait for its remote captures to be downloaded and merged")
  void scenarioDoesNotWaitForTheRemoteMerge() throws Exception {
    FakeRemoteProxy remote = remoteProxyCapturing(9, T0.plusSeconds(1));
    CountDownLatch release = new CountDownLatch(1);
    remote.holdDownloadsUntil(release);
    Lifecycle lifecycle = new Lifecycle(remoteConfig(true));
    lifecycle.remotes = Map.of(remote.url(), Duration.ZERO);
    lifecycle.onTestRunStarted();

    onScenarioThread(
        () -> {
          lifecycle.onTestCaseStarted(TEST_CASE, "lifecycle-background", -1);
          deliver(1, 2500);
          lifecycle.onTestCaseFinished(TEST_CASE, "lifecycle-background", -1);
        });

    assertThat(remote.calls()).as("stopped at the end of the scenario").contains("stop");
    assertThat(newEvidence("lifecycle-background")).as("not merged yet").isEmpty();
    release.countDown();
    PcapMergeQueue.getInstance().awaitAll();
    assertThat(markersIn(onlyNewEvidence("lifecycle-background")))
        .as("merged once the report asks for it")
        .containsExactly(9, 1);
    lifecycle.onTestRunFinished();
  }

  @Test
  @DisplayName("the evidence registered when a scenario ends holds the merged file after the merge")
  void evidenceHoldsTheMergedFileAfterTheMerge() throws Exception {
    FakeRemoteProxy remote = remoteProxyCapturing(9, T0.plusSeconds(1));
    CountDownLatch release = new CountDownLatch(1);
    remote.holdDownloadsUntil(release);
    Lifecycle lifecycle = new Lifecycle(remoteConfig(true));
    lifecycle.serenityAvailable = true;
    lifecycle.remotes = Map.of(remote.url(), Duration.ZERO);
    lifecycle.onTestRunStarted();

    onScenarioThread(
        () -> {
          lifecycle.onTestCaseStarted(TEST_CASE, "lifecycle-evidence", -1);
          deliver(1, 2500);
          lifecycle.onTestCaseFinished(TEST_CASE, "lifecycle-evidence", -1);
        });

    Path copy = onlyEvidenceCopy(lifecycle, "lifecycle-evidence.pcapng");
    assertThat(copy).as("registered while the scenario is current").isEmptyFile();
    release.countDown();
    PcapMergeQueue.getInstance().awaitAll();
    assertThat(markersIn(copy)).containsExactly(9, 1);
    lifecycle.onTestRunFinished();
  }

  @Test
  @DisplayName("if merging with the remote captures fails, the evidence holds the local capture")
  void evidenceHoldsTheLocalCaptureIfTheRemoteMergeFails() throws Exception {
    FakeRemoteProxy remote = remoteProxyCapturing(9, T0.plusSeconds(1));
    Lifecycle lifecycle = new Lifecycle(remoteConfig(true));
    lifecycle.serenityAvailable = true;
    lifecycle.failMergesWithRemotes = true;
    lifecycle.remotes = Map.of(remote.url(), Duration.ZERO);
    lifecycle.onTestRunStarted();

    onScenarioThread(
        () -> {
          lifecycle.onTestCaseStarted(TEST_CASE, "lifecycle-fallback", -1);
          deliver(1, 2500);
          lifecycle.onTestCaseFinished(TEST_CASE, "lifecycle-fallback", -1);
        });
    PcapMergeQueue.getInstance().awaitAll();

    assertThat(markersIn(onlyEvidenceCopy(lifecycle, "lifecycle-fallback.pcapng")))
        .containsExactly(1);
    lifecycle.onTestRunFinished();
  }

  private static Path onlyEvidenceCopy(Lifecycle lifecycle, String filename) throws IOException {
    try (Stream<Path> copies =
        Files.list(lifecycle.serenityOutputDirectory().resolve("downloadable"))) {
      return copies
          .filter(copy -> copy.getFileName().toString().endsWith("-" + filename))
          .collect(java.util.stream.Collectors.toList())
          .get(0);
    }
  }

  @Test
  @DisplayName("remote captures are left out of the files if merging them is switched off")
  void remoteCaptureIsLeftOutIfMergingIsSwitchedOff() throws Exception {
    FakeRemoteProxy remote = remoteProxyCapturing(9, T0.plusSeconds(3));
    TigerPcapCaptureConfig config = remoteConfig(true);
    config.setMergeRemotePcaps(false);
    Lifecycle lifecycle = new Lifecycle(config);
    lifecycle.remotes = Map.of(remote.url(), Duration.ZERO);

    lifecycle.onTestRunStarted();
    onScenarioThread(
        () -> {
          lifecycle.onTestCaseStarted(TEST_CASE, "lifecycle-nomerge", -1);
          deliver(1, 0);
          lifecycle.onTestCaseFinished(TEST_CASE, "lifecycle-nomerge", -1);
        });
    lifecycle.onTestRunFinished();

    assertThat(markersIn(onlyNewEvidence("lifecycle-nomerge.pcapng"))).containsExactly(1);
    assertThat(remote.calls()).contains("stop").doesNotContain("download");
  }

  @Test
  @DisplayName("a remote capture that cannot be downloaded is left out, the local file stays")
  void remoteCaptureThatCannotBeDownloadedIsLeftOut() throws Exception {
    FakeRemoteProxy remote = remoteProxyCapturing(9, T0.plusSeconds(3));
    remote.refuseDownloadsWith(404);
    Lifecycle lifecycle = new Lifecycle(remoteConfig(true));
    lifecycle.remotes = Map.of(remote.url(), Duration.ZERO);

    lifecycle.onTestRunStarted();
    onScenarioThread(
        () -> {
          lifecycle.onTestCaseStarted(TEST_CASE, "lifecycle-nodownload", -1);
          deliver(1, 0);
          lifecycle.onTestCaseFinished(TEST_CASE, "lifecycle-nodownload", -1);
        });
    lifecycle.onTestRunFinished();

    assertThat(markersIn(onlyNewEvidence("lifecycle-nodownload.pcapng"))).containsExactly(1);
  }

  @Test
  @DisplayName("an empty or broken remote capture is left out, the local file stays")
  void emptyOrBrokenRemoteCaptureIsLeftOut() throws Exception {
    FakeRemoteProxy remote = remoteProxyCapturing(9, T0.plusSeconds(3));
    remote.serve(new byte[0]);
    Lifecycle lifecycle = new Lifecycle(remoteConfig(true));
    lifecycle.remotes = Map.of(remote.url(), Duration.ZERO);
    lifecycle.onTestRunStarted();
    onScenarioThread(
        () -> {
          lifecycle.onTestCaseStarted(TEST_CASE, "lifecycle-empty", -1);
          deliver(1, 0);
          lifecycle.onTestCaseFinished(TEST_CASE, "lifecycle-empty", -1);
        });
    PcapMergeQueue.getInstance().awaitAll();
    assertThat(markersIn(onlyNewEvidence("lifecycle-empty.pcapng"))).containsExactly(1);

    remote.serve("this is not a pcapng file".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    onScenarioThread(
        () -> {
          lifecycle.onTestCaseStarted(TEST_CASE, "lifecycle-broken", -1);
          deliver(2, 10);
          lifecycle.onTestCaseFinished(TEST_CASE, "lifecycle-broken", -1);
        });
    lifecycle.onTestRunFinished();

    assertThat(markersIn(onlyNewEvidence("lifecycle-broken.pcapng"))).containsExactly(2);
  }

  @Test
  @DisplayName("without splitting by test case, a scenario's remote capture gets a file of its own")
  void unsplitRemoteCaptureGetsItsOwnFile() throws Exception {
    FakeRemoteProxy remote = remoteProxyCapturing(9, T0.plusSeconds(3));
    Lifecycle lifecycle = new Lifecycle(remoteConfig(false));
    lifecycle.remotes = Map.of(remote.url(), Duration.ZERO);

    lifecycle.onTestRunStarted();
    onScenarioThread(
        () -> {
          lifecycle.onTestCaseStarted(TEST_CASE, "lifecycle-unsplit-remote", -1);
          deliver(1, 0);
          lifecycle.onTestCaseFinished(TEST_CASE, "lifecycle-unsplit-remote", -1);
        });
    lifecycle.onTestRunFinished();

    assertThat(markersIn(onlyNewEvidence("merged_lifecycle-unsplit-remote")))
        .as("the remote packet, and no local ones: the suite file has those")
        .containsExactly(9);
  }
}
