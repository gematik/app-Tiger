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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import de.gematik.test.tiger.lib.TigerPcapCaptureConfig;
import de.gematik.test.tiger.testenvmgr.env.TigerServerStatusUpdate;
import de.gematik.test.tiger.testenvmgr.env.TigerStatusUpdate;
import io.cucumber.plugin.event.TestCase;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/**
 * Unit tests for {@code TigerPcapCaptureLifecycle}, driving it through a subclass that stubs out
 * the static {@code TigerDirector}/Serenity dependencies and injects a mocked {@code
 * TigerPcapCaptureService}.
 */
@DisplayName("TigerPcapCaptureLifecycle")
class TigerPcapCaptureLifecycleTest {

  private static TigerPcapCaptureConfig configWith(boolean splitByTestcase) {
    TigerPcapCaptureConfig cfg = new TigerPcapCaptureConfig();
    cfg.setEnabled(true);
    cfg.setSplitByTestcase(splitByTestcase);
    cfg.setStartSuspended(false);
    cfg.setFilename("${scenarioId}.pcapng");
    return cfg;
  }

  /** Test double giving full control over the seams the real lifecycle pulls from statics. */
  private static class TestableLifecycle extends TigerPcapCaptureLifecycle {
    final TigerPcapCaptureConfig config;
    final TigerPcapCaptureService serviceMock = mock(TigerPcapCaptureService.class);
    boolean discoverProxyPortsCalled = false;
    Set<Integer> discoveredPorts = Set.of();
    boolean envUpdateListenerRegistered = false;

    TestableLifecycle(TigerPcapCaptureConfig config) {
      this.config = config;
      when(serviceMock.isEnabled()).thenReturn(true);
    }

    @Override
    protected TigerPcapCaptureConfig loadPcapConfig() {
      return config;
    }

    @Override
    protected TigerPcapCaptureService createCaptureService(TigerPcapCaptureConfig pcapConfig) {
      return serviceMock;
    }

    @Override
    protected Set<Integer> discoverProxyPorts() {
      discoverProxyPortsCalled = true;
      return discoveredPorts;
    }

    @Override
    protected boolean isSerenityAvailable() {
      return false;
    }

    @Override
    protected void registerEnvUpdateListener() {
      envUpdateListenerRegistered = true;
    }
  }

  @Test
  @DisplayName("splitByTestcase=false rotates to a non-null suite file on first testcase start")
  void suiteWideCapture_rotatesToNonNullFile() {
    TestableLifecycle lifecycle = new TestableLifecycle(configWith(false));

    lifecycle.onTestRunStarted();
    lifecycle.onTestCaseStarted(mock(TestCase.class), "scenario-1", -1);

    ArgumentCaptor<Path> captor = ArgumentCaptor.forClass(Path.class);
    verify(lifecycle.serviceMock).rotate(captor.capture());
    assertThat(captor.getValue()).isNotNull();
  }

  @Test
  @DisplayName("splitByTestcase=true resolves the filename template with the real scenario id")
  void perTestcaseCapture_resolvesFilenameTemplateWithScenarioId() {
    TestableLifecycle lifecycle = new TestableLifecycle(configWith(true));

    lifecycle.onTestRunStarted();
    TestCase testCase = mock(TestCase.class);
    when(testCase.getName()).thenReturn("My Scenario");
    lifecycle.onTestCaseStarted(testCase, "scenario-1", -1);

    // First rotate() is the pre-first-scenario file; second is this scenario's own.
    ArgumentCaptor<Path> captor = ArgumentCaptor.forClass(Path.class);
    verify(lifecycle.serviceMock, times(2)).rotate(captor.capture());
    assertThat(captor.getValue().getFileName().toString()).isEqualTo("scenario-1.pcapng");
  }

  @Test
  @DisplayName("splitByTestcase=true gives different scenarios different files")
  void perTestcaseCapture_differentScenariosGetDifferentFiles() {
    TestableLifecycle lifecycle = new TestableLifecycle(configWith(true));
    lifecycle.onTestRunStarted();

    ArgumentCaptor<Path> captor = ArgumentCaptor.forClass(Path.class);

    lifecycle.onTestCaseStarted(mock(TestCase.class), "scenario-1", -1);
    lifecycle.onTestCaseStarted(mock(TestCase.class), "scenario-2", -1);

    // Pre-first-scenario suite file + scenario-1's file + scenario-2's file.
    verify(lifecycle.serviceMock, times(3)).rotate(captor.capture());
    assertThat(captor.getAllValues()).extracting(Object::toString).doesNotHaveDuplicates();
  }

  @Test
  @DisplayName("splitByTestcase=true reads the packet count before rotate() replaces the dumper")
  void perTestcaseCapture_readsPacketCountBeforeRotate() {
    TestableLifecycle lifecycle = new TestableLifecycle(configWith(true));
    lifecycle.onTestRunStarted();
    TestCase testCase = mock(TestCase.class);
    when(testCase.getName()).thenReturn("My Scenario");
    lifecycle.onTestCaseStarted(testCase, "scenario-1", -1);
    clearInvocations(lifecycle.serviceMock); // isolate onTestCaseFinished's own call order

    lifecycle.onTestCaseFinished(testCase, "scenario-1", -1);

    InOrder inOrder = inOrder(lifecycle.serviceMock);
    inOrder.verify(lifecycle.serviceMock).getCurrentDumperPacketCount();
    inOrder.verify(lifecycle.serviceMock).rotate(any());
  }

  @Test
  @DisplayName("splitByTestcase=true opens a fresh between-scenario suite file at testcase finish")
  void perTestcaseCapture_opensFreshSuiteFileBetweenScenarios() {
    TestableLifecycle lifecycle = new TestableLifecycle(configWith(true));
    lifecycle.onTestRunStarted();
    TestCase testCase = mock(TestCase.class);
    when(testCase.getName()).thenReturn("My Scenario");
    lifecycle.onTestCaseStarted(testCase, "scenario-1", -1);

    lifecycle.onTestCaseFinished(testCase, "scenario-1", -1);

    // Suite-wide pre-first-scenario file (onTestRunStarted) + scenario-1's file
    // (onTestCaseStarted) + this between-scenario file (onTestCaseFinished) = 3 so far.
    ArgumentCaptor<Path> betweenScenarioFile = ArgumentCaptor.forClass(Path.class);
    verify(lifecycle.serviceMock, times(3)).rotate(betweenScenarioFile.capture());
    assertThat(betweenScenarioFile.getValue().getFileName().toString())
        .as("the between-scenario file must not collide with scenario-1's own file")
        .isNotEqualTo("scenario-1.pcapng");

    // The next scenario's own rotate() closes and replaces that between-scenario file.
    lifecycle.onTestCaseStarted(mock(TestCase.class), "scenario-2", -1);
    ArgumentCaptor<Path> allRotations = ArgumentCaptor.forClass(Path.class);
    verify(lifecycle.serviceMock, times(4)).rotate(allRotations.capture());
    assertThat(allRotations.getAllValues()).extracting(Object::toString).doesNotHaveDuplicates();
  }

  @Test
  @DisplayName("splitByTestcase=true truncates long scenario ids with a UUID-hash suffix")
  void perTestcaseCapture_truncatesLongScenarioId() {
    TestableLifecycle lifecycle = new TestableLifecycle(configWith(true));
    lifecycle.onTestRunStarted();
    String longScenarioId = "a-very-long-scenario-id-that-exceeds-the-thirty-character-cap";

    lifecycle.onTestCaseStarted(mock(TestCase.class), longScenarioId, -1);

    ArgumentCaptor<Path> captor = ArgumentCaptor.forClass(Path.class);
    verify(lifecycle.serviceMock, times(2)).rotate(captor.capture());
    String fileName = captor.getValue().getFileName().toString();
    assertThat(fileName).startsWith(longScenarioId.substring(0, 30));
    // 30-char prefix + 8-char UUID-hash suffix + ".pcapng"
    assertThat(fileName).hasSize(30 + 8 + ".pcapng".length());
  }

  @Test
  @DisplayName("Manual BPF filter is passed to the capture service and skips port discovery")
  void manualBpfFilter_wiredToServiceAndSkipsDiscovery() {
    TigerPcapCaptureConfig cfg = configWith(true);
    cfg.setBpfFilter("tcp port 4444");
    TestableLifecycle lifecycle = new TestableLifecycle(cfg);

    lifecycle.onTestRunStarted();
    lifecycle.onTestCaseStarted(mock(TestCase.class), "scenario-1", -1);

    verify(lifecycle.serviceMock).setManualBpfFilter("tcp port 4444");
    assertThat(lifecycle.discoverProxyPortsCalled).isFalse();
  }

  @Test
  @DisplayName("Automatic discovery registers an env-update listener; manual filter does not")
  void autoDiscovery_registersEnvUpdateListener_manualFilterDoesNot() {
    TestableLifecycle autoLifecycle = new TestableLifecycle(configWith(true));
    autoLifecycle.onTestRunStarted();
    autoLifecycle.onTestCaseStarted(mock(TestCase.class), "scenario-1", -1);
    assertThat(autoLifecycle.envUpdateListenerRegistered).isTrue();

    TigerPcapCaptureConfig manualCfg = configWith(true);
    manualCfg.setBpfFilter("tcp port 4444");
    TestableLifecycle manualLifecycle = new TestableLifecycle(manualCfg);
    manualLifecycle.onTestRunStarted();
    manualLifecycle.onTestCaseStarted(mock(TestCase.class), "scenario-1", -1);
    assertThat(manualLifecycle.envUpdateListenerRegistered).isFalse();
  }

  @Test
  @DisplayName("A server-related env update widens the capture filter")
  void receiveTestEnvUpdate_widensFilterOnServerUpdate() {
    TestableLifecycle lifecycle = new TestableLifecycle(configWith(true));
    lifecycle.discoveredPorts = Set.of(9999);
    lifecycle.onTestRunStarted();
    lifecycle.onTestCaseStarted(mock(TestCase.class), "scenario-1", -1);
    clearInvocations(lifecycle.serviceMock); // isolate from the testcase-start widen call

    TigerStatusUpdate update =
        TigerStatusUpdate.builder()
            .serverUpdate(
                new java.util.LinkedHashMap<>(
                    Map.of("newProxy", TigerServerStatusUpdate.builder().build())))
            .build();
    lifecycle.receiveTestEnvUpdate(update);

    verify(lifecycle.serviceMock).alsoCapturePorts(Set.of(9999));
  }

  @Test
  @DisplayName("An env update without a server entry does not widen the filter")
  void receiveTestEnvUpdate_ignoresNonServerUpdate() {
    TestableLifecycle lifecycle = new TestableLifecycle(configWith(true));
    lifecycle.onTestRunStarted();
    lifecycle.onTestCaseStarted(mock(TestCase.class), "scenario-1", -1);
    clearInvocations(lifecycle.serviceMock); // isolate from the testcase-start widen call

    lifecycle.receiveTestEnvUpdate(TigerStatusUpdate.builder().build());

    verify(lifecycle.serviceMock, never()).alsoCapturePorts(any());
  }

  @Test
  @DisplayName("Every scenario boundary widens the filter, including the first")
  void onTestCaseStarted_widensFilterAtEachScenarioBoundary() {
    TestableLifecycle lifecycle = new TestableLifecycle(configWith(true));
    lifecycle.discoveredPorts = Set.of(7777);
    lifecycle.onTestRunStarted();

    lifecycle.onTestCaseStarted(mock(TestCase.class), "scenario-1", -1);
    lifecycle.onTestCaseStarted(mock(TestCase.class), "scenario-2", -1);

    verify(lifecycle.serviceMock, times(2)).alsoCapturePorts(Set.of(7777));
  }

  @Test
  @DisplayName("Manual filter is never widened by env updates or scenario boundaries")
  void manualFilter_neverWidened() {
    TigerPcapCaptureConfig cfg = configWith(true);
    cfg.setBpfFilter("tcp port 4444");
    TestableLifecycle lifecycle = new TestableLifecycle(cfg);
    lifecycle.onTestRunStarted();
    lifecycle.onTestCaseStarted(mock(TestCase.class), "scenario-1", -1);
    lifecycle.onTestCaseStarted(mock(TestCase.class), "scenario-2", -1);

    TigerStatusUpdate update =
        TigerStatusUpdate.builder()
            .serverUpdate(
                new java.util.LinkedHashMap<>(
                    Map.of("newProxy", TigerServerStatusUpdate.builder().build())))
            .build();
    lifecycle.receiveTestEnvUpdate(update);

    verify(lifecycle.serviceMock, never()).alsoCapturePorts(any());
  }

  @Test
  @DisplayName("Disabled config never creates a capture service")
  void disabledConfig_neverCreatesService() {
    TigerPcapCaptureConfig cfg = configWith(false);
    cfg.setEnabled(false);
    TestableLifecycle lifecycle = new TestableLifecycle(cfg);

    lifecycle.onTestRunStarted();
    lifecycle.onTestCaseStarted(mock(TestCase.class), "scenario-1", -1);

    verifyNoInteractions(lifecycle.serviceMock);
  }
}
