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

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerConfigurationRoute;
import de.gematik.test.tiger.lib.TigerDirector;
import de.gematik.test.tiger.lib.TigerPcapCaptureConfig;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.data.TigerProxyRoute;
import de.gematik.test.tiger.testenvmgr.env.TigerStatusUpdate;
import de.gematik.test.tiger.testenvmgr.env.TigerUpdateListener;
import de.gematik.test.tiger.testenvmgr.servers.TigerProxyServer;
import io.cucumber.plugin.event.TestCase;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.serenitybdd.core.Serenity;

/**
 * Lifecycle manager that wires {@code TigerPcapCaptureService} into Cucumber/Serenity test
 * execution events.
 *
 * <p>Responsible for:
 *
 * <ul>
 *   <li>Reading {@code lib.pcapCapture.*} config from {@code TigerLibConfig}.
 *   <li>Discovering proxy ports from running {@code TigerProxyServer} instances.
 *   <li>Starting/stopping the capture service at suite boundaries.
 *   <li>Rotating per-testcase dumpers at scenario boundaries (if {@code splitByTestcase=true}).
 *   <li>Registering captured files as Serenity evidence artifacts.
 * </ul>
 *
 * <p>Designed to be a passive observer (no throwing exceptions out of callbacks) — capture failures
 * must never fail a testcase.
 *
 * <p>The BPF filter is compiled once when capture starts (first scenario). A {@code
 * TigerProxyServer} started later in the run — e.g. via {@code TGR start server} — would otherwise
 * stay outside the filter for the rest of the suite. This class widens the filter (never narrows)
 * in two places: on every {@link #receiveTestEnvUpdate} that mentions a server, and again at every
 * scenario boundary as a catch-all for routes added without a status update. Manual filters ({@code
 * lib.pcapCapture.bpfFilter}) are never touched by this widening.
 */
@Slf4j
public class TigerPcapCaptureLifecycle implements TigerUpdateListener {

  private TigerPcapCaptureService captureService;
  private Path evidenceDir;
  private boolean splitByTestcase;
  private boolean initialized = false;
  private Path currentSuiteFile;
  private boolean startSuspended = false;
  private String filenameTemplate;
  private boolean manualFilterConfigured = false;
  // Tiebreaker for suite-wide filenames: currentTimeMillis() alone can collide when two such
  // files are opened within the same millisecond (e.g. a zero-latency scenario boundary).
  private final AtomicLong suiteFileSequence = new AtomicLong();

  /**
   * Called at test suite start. Initializes the capture service and starts capturing immediately,
   * into a suite-wide file — the environment (including all auto-starting {@code TigerProxyServer}
   * instances) is already up by this point, so ports are discoverable now, and traffic between this
   * point and the first scenario's first step would otherwise never be captured in either mode.
   */
  public void onTestRunStarted() {
    try {
      TigerPcapCaptureConfig pcapConfig = loadPcapConfig();
      if (pcapConfig == null || !pcapConfig.isEnabled()) {
        log.debug("Pcap capture is disabled in config; skipping initialization");
        return;
      }

      log.info("Initializing pcap capture service...");
      evidenceDir = computeEvidenceDir();
      splitByTestcase = pcapConfig.isSplitByTestcase();
      startSuspended = pcapConfig.isStartSuspended();
      filenameTemplate = pcapConfig.getFilename();
      log.debug("splitByTestcase: {}, startSuspended: {}", splitByTestcase, startSuspended);

      captureService = createCaptureService(pcapConfig);
      captureService.setInterfaceNames(pcapConfig.getInterfaceNames());
      captureService.setManualBpfFilter(pcapConfig.getBpfFilter());
      manualFilterConfigured =
          pcapConfig.getBpfFilter() != null && !pcapConfig.getBpfFilter().isBlank();
      TigerPcapCaptureService.setInstance(captureService);

      currentSuiteFile = resolveFilePath(null, -1);

      discoverAndConfigurePorts();
      initialized = true;
      log.info("*** Pcap capture service initialized ***");
    } catch (Exception e) {
      log.warn("Failed to initialize pcap capture: {}", e.getMessage());
      log.debug("Initialization error:", e);
    }
  }

  /**
   * Called at testcase start. Widens the filter for any server that appeared since the last check.
   * If {@code splitByTestcase=true}, registers whichever between-scenario file was open (the
   * pre-first-scenario file, on the first call; the previous scenario's between-scenario file on
   * every later call — see {@link #onTestCaseFinished}) and opens a new dumper for this scenario.
   *
   * @param testCase the cucumber test case.
   * @param scenarioId the Tiger scenario ID.
   * @param dataVariantIndex the data variant index (-1 if not a data-driven scenario).
   */
  public void onTestCaseStarted(TestCase testCase, String scenarioId, int dataVariantIndex) {
    try {
      if (!initialized || captureService == null) {
        log.debug("Pcap capture not initialized; skipping testcase start");
        return;
      }

      widenFilterForLateServers();

      if (!captureService.isEnabled()) {
        log.debug("Pcap capture not active; skipping testcase rotation");
        return;
      }

      if (splitByTestcase) {
        long capturedInSuiteFile = captureService.getCurrentDumperPacketCount();
        Path testcaseFile = resolveFilePath(scenarioId, dataVariantIndex);
        captureService.rotate(testcaseFile);
        registerEvidenceIfCaptured(currentSuiteFile, capturedInSuiteFile);
        log.debug("Rotated pcap capture to testcase file: {}", testcaseFile);
      }
    } catch (Exception e) {
      log.warn("Failed to handle testcase start for pcap capture: {}", e.getMessage());
      log.debug("Testcase start error:", e);
    }
  }

  /** Loads the pcap capture config. Extracted for testability. */
  protected TigerPcapCaptureConfig loadPcapConfig() {
    return TigerDirector.getLibConfig().getPcapCapture();
  }

  /** Creates the capture service. Extracted for testability. */
  protected TigerPcapCaptureService createCaptureService(TigerPcapCaptureConfig pcapConfig) {
    return new TigerPcapCaptureService(pcapConfig.getSnaplenKb(), pcapConfig.getBufferSizeKb());
  }

  private void discoverAndConfigurePorts() {
    if (manualFilterConfigured) {
      log.debug("Manual BPF filter configured; skipping automatic proxy port discovery");
      startCaptureWithPorts(Set.of());
      return;
    }

    log.debug("Discovering proxy ports...");
    Set<Integer> ports = discoverProxyPorts();

    if (ports.isEmpty()) {
      log.warn("No Tiger Proxy servers found; pcap capture will be unrestricted");
    } else {
      log.debug("Discovered {} proxy ports for pcap capture: {}", ports.size(), ports);
    }
    startCaptureWithPorts(ports);
    registerForLateServerUpdates();
  }

  /**
   * Subscribes to the test environment so a {@code TigerProxyServer} started after capture began
   * still widens the BPF filter. A no-op if capture never actually started (native lib missing,
   * suspended, or manual filter).
   */
  private void registerForLateServerUpdates() {
    if (manualFilterConfigured || !captureService.isEnabled()) {
      return;
    }
    try {
      registerEnvUpdateListener();
    } catch (Exception e) {
      log.warn("Could not subscribe to test environment updates: {}", e.getMessage());
      log.debug("Listener registration error:", e);
    }
  }

  /** Subscribes this instance to test environment updates. Extracted for testability. */
  protected void registerEnvUpdateListener() {
    TigerDirector.getTigerTestEnvMgr().registerNewListener(this);
  }

  /**
   * Widens the capture filter on any news about a server, re-derived from scratch rather than taken
   * from the update, because a proxy's interesting ports (admin port, route targets) are not all in
   * its status message.
   */
  @Override
  public void receiveTestEnvUpdate(TigerStatusUpdate update) {
    try {
      if (manualFilterConfigured
          || !initialized
          || captureService == null
          || !captureService.isEnabled()
          || update == null
          || update.getServerUpdate() == null
          || update.getServerUpdate().isEmpty()) {
        return;
      }
      captureService.alsoCapturePorts(discoverProxyPorts());
    } catch (Exception e) {
      log.warn("Failed to widen the pcap filter after a server update: {}", e.getMessage());
      log.debug("Server update error:", e);
    }
  }

  /** Catch-all widen at every scenario boundary, for routes added without a status update. */
  private void widenFilterForLateServers() {
    if (manualFilterConfigured || captureService == null || !captureService.isEnabled()) {
      return;
    }
    captureService.alsoCapturePorts(discoverProxyPorts());
  }

  private void startCaptureWithPorts(Set<Integer> ports) {
    if (startSuspended) {
      log.info("Pcap capture initialized but suspended; call resume() to start capturing");
      captureService.setLastStartPorts(ports);
    } else {
      captureService.start(ports);
      if (captureService.isEnabled()) {
        // In splitByTestcase=true mode, the first scenario's rotate() closes this and takes over.
        captureService.rotate(currentSuiteFile);
        log.debug("Opened pcap file: {}", currentSuiteFile);
      }
    }
  }

  /**
   * Called at testcase finish. If {@code splitByTestcase=true}, registers the pcap file as a
   * Serenity evidence artifact, then opens a fresh suite file to catch any traffic between this
   * scenario and the next (or the suite's end, if this was the last one) — see {@link
   * #onTestCaseStarted} and {@link #onTestRunFinished} for where that file gets registered.
   *
   * @param testCase the cucumber test case.
   * @param scenarioId the Tiger scenario ID.
   * @param dataVariantIndex the data variant index (-1 if not a data-driven scenario).
   */
  public void onTestCaseFinished(TestCase testCase, String scenarioId, int dataVariantIndex) {
    try {
      if (!initialized || captureService == null) {
        return;
      }

      if (splitByTestcase && captureService.isEnabled()) {
        Path testcaseFile = resolveFilePath(scenarioId, dataVariantIndex);
        long capturedInTestcaseFile = captureService.getCurrentDumperPacketCount();
        currentSuiteFile = resolveFilePath(null, -1);
        captureService.rotate(currentSuiteFile);
        registerEvidenceIfCaptured(testcaseFile, capturedInTestcaseFile);
        log.debug("Opened between-scenario pcap file: {}", currentSuiteFile);
      }

      restoreBaselineState();
    } catch (Exception e) {
      log.warn("Failed to register pcap evidence at testcase finish: {}", e.getMessage());
      log.debug("Evidence registration error:", e);
    }
  }

  private void restoreBaselineState() {
    if (!initialized || captureService == null) {
      return;
    }

    try {
      if (startSuspended) {
        if (captureService.isEnabled()) {
          captureService.suspend();
          log.debug("Restored baseline: pcap capture suspended at scenario boundary");
        }
      } else {
        if (!captureService.isEnabled()) {
          captureService.resume();
          log.debug("Restored baseline: pcap capture resumed at scenario boundary");
        }
      }
    } catch (Exception e) {
      log.warn("Failed to restore baseline pcap state: {}", e.getMessage());
      log.debug("Baseline restore error:", e);
    }
  }

  /**
   * Called at test suite finish. Stops the capture service and registers whatever suite file was
   * still open.
   *
   * <p>In {@code splitByTestcase=false} mode this is the whole run's traffic. In {@code
   * splitByTestcase=true} mode it is the trailing between-scenario file opened by the last {@link
   * #onTestCaseFinished} (or the pre-first-scenario file, if no scenario ever ran).
   */
  public void onTestRunFinished() {
    try {
      if (!initialized || captureService == null) {
        return;
      }

      long capturedInSuiteFile = captureService.getCurrentDumperPacketCount();
      captureService.stop();
      TigerPcapCaptureService.setInstance(null);
      registerEvidenceIfCaptured(currentSuiteFile, capturedInSuiteFile);

      log.debug("Pcap capture service stopped");
    } catch (Exception e) {
      log.warn("Failed to stop pcap capture: {}", e.getMessage());
      log.debug("Stop error:", e);
    }
  }

  /**
   * Registers {@code pcapFile} as Serenity evidence if it exists and actually captured at least one
   * packet — {@code packetCount} must be read from {@link
   * TigerPcapCaptureService#getCurrentDumperPacketCount()} before the dumper for this file was
   * closed/rotated/stopped, since a pcapng file's non-zero section-header block means {@code
   * Files.size() > 0} is true even with zero packets.
   */
  private void registerEvidenceIfCaptured(Path pcapFile, long packetCount) throws IOException {
    if (pcapFile != null && packetCount > 0 && Files.exists(pcapFile)) {
      registerEvidence(pcapFile);
    }
  }

  /** Discovers proxy ports to filter on. Extracted for testability. */
  protected Set<Integer> discoverProxyPorts() {
    Set<Integer> ports = new HashSet<>();
    try {
      var servers = TigerDirector.getTigerTestEnvMgr().getServersOfType(TigerProxyServer.class);
      for (TigerProxyServer server : servers) {
        discoverProxyPorts(server, ports);
      }

      discoverLocalProxyPorts(ports);
    } catch (Exception e) {
      log.warn("Failed to discover proxy ports: {}", e.getMessage());
      log.debug("Discovery error:", e);
    }
    return ports.stream().filter(p -> p > 0).collect(Collectors.toSet());
  }

  private static void discoverLocalProxyPorts(Set<Integer> ports) {
    try {
      TigerDirector.getTigerTestEnvMgr()
          .getLocalTigerProxyOptional()
          .ifPresent(proxy -> addPortsOfRunningProxy(proxy, ports));
    } catch (Exception e) {
      log.debug("Could not get proxy from TigerDirector: {}", e.getMessage());
    }
  }

  /**
   * Ports of a proxy that is actually running, including any route added since it started (e.g. via
   * {@code TigerProxy.addRoute} or a PUT on the admin API) — those targets exist nowhere in the
   * server's startup configuration, so the running proxy has to be asked directly.
   */
  private static void addPortsOfRunningProxy(TigerProxy proxy, Set<Integer> ports) {
    try {
      int proxyPort = proxy.getProxyPort();
      int adminPort = proxy.getAdminPort();
      log.debug("Running proxy port: {}, admin port: {}", proxyPort, adminPort);
      if (proxyPort > 0) {
        ports.add(proxyPort);
      }
      if (adminPort > 0) {
        ports.add(adminPort);
      }
      Optional.ofNullable(proxy.getRoutes()).stream()
          .flatMap(List::stream)
          .map(TigerProxyRoute::getTo)
          .filter(Objects::nonNull)
          .forEach(targetUri -> addPortOf(targetUri, ports));
    } catch (Exception e) {
      log.debug("Could not read the ports of a running Tiger proxy: {}", e.getMessage());
    }
  }

  private static void addPortOf(String targetUri, Set<Integer> ports) {
    try {
      String resolvedUri = TigerGlobalConfiguration.resolvePlaceholders(targetUri);
      int targetPort = new URI(resolvedUri).getPort();
      if (targetPort > 0) {
        ports.add(targetPort);
      }
    } catch (Exception e) {
      log.debug("Could not parse route target URI: {}", targetUri, e);
    }
  }

  private static void discoverProxyPorts(TigerProxyServer server, Set<Integer> ports) {
    try {
      var cfg = server.getConfiguration().getTigerProxyConfiguration();
      if (cfg != null) {
        if (cfg.getDirectReverseProxy() != null) {
          int targetPort = cfg.getDirectReverseProxy().getPort();
          ports.add(targetPort);
        }

        Optional.ofNullable(cfg.getProxyRoutes()).stream()
            .flatMap(List::stream)
            .map(TigerConfigurationRoute::getTo)
            .flatMap(List::stream)
            .forEach(targetUri -> addPortOf(targetUri, ports));
      }

      // Static config misses routes added at runtime; ask the running proxy too.
      Optional.ofNullable(server.getTigerProxyBean())
          .ifPresent(proxy -> addPortsOfRunningProxy(proxy, ports));
    } catch (Exception e) {
      log.warn(
          "Failed to discover ports for server {}: {}", server.getServerId(), e.getMessage(), e);
    }
  }

  /**
   * Resolves the target file path for a pcap file.
   *
   * <p>For testcase files ({@code scenarioId != null}): resolves {@code lib.pcapCapture.filename}
   * with {@code ${scenarioId}} substituted by the real scenario id, via {@link
   * #resolveTestcaseFileName}.
   *
   * <p>For the suite-wide file ({@code scenarioId == null}): no per-scenario context exists yet at
   * suite start, so a generic, run-unique name is used instead of the template.
   *
   * @param scenarioId the Tiger scenario ID; {@code null} selects the suite-wide file.
   * @param dataVariantIndex the data variant index (-1 if none).
   * @return the resolved file path in {@code target/evidences/}.
   */
  private Path resolveFilePath(String scenarioId, int dataVariantIndex) {
    String fileName =
        scenarioId == null
            ? "suite_"
                + System.currentTimeMillis()
                + "_"
                + suiteFileSequence.getAndIncrement()
                + ".pcapng"
            : resolveTestcaseFileName(scenarioId, dataVariantIndex);
    return evidenceDir.resolve(fileName);
  }

  private static final int MAX_BASE_FILENAME_LENGTH = 30;

  /**
   * Resolves the {@code lib.pcapCapture.filename} template for one testcase's file: {@code
   * ${scenarioId}} is substituted with the real scenario id, long names are truncated with a
   * UUID-hash suffix to stay within {@link #MAX_BASE_FILENAME_LENGTH} characters, and a
   * data-variant suffix is appended for parametrized scenarios.
   */
  private String resolveTestcaseFileName(String scenarioId, int dataVariantIndex) {
    String base = filenameTemplate.replace("${scenarioId}", scenarioId);
    if (base.endsWith(".pcapng")) {
      base = base.substring(0, base.length() - ".pcapng".length());
    }
    base = replaceSpecialCharacters(base);
    if (base.length() > MAX_BASE_FILENAME_LENGTH) {
      base =
          base.substring(0, MAX_BASE_FILENAME_LENGTH)
              + UUID.nameUUIDFromBytes(base.getBytes()).toString().substring(0, 8);
    }
    if (dataVariantIndex >= 0) {
      base += "_" + (dataVariantIndex + 1);
    }
    return base + ".pcapng";
  }

  /** Replaces umlauts and special characters, matching Serenity's convention. */
  private String replaceSpecialCharacters(String name) {
    return name.replaceAll("[äÄöÖüÜß]+", "_")
        .replaceAll("[^\\w.-]+", "_")
        .replaceAll("_+", "_")
        .replaceAll("(^_)|(_$)", "");
  }

  /** Registers a pcap file as a Serenity evidence artifact. */
  private void registerEvidence(Path pcapFile) {
    try {
      if (isSerenityAvailable()) {
        (Serenity.recordReportData().asEvidence().withTitle("Network Traffic (PCAP)"))
            .downloadable()
            .fromFile(pcapFile);
        log.debug("Registered pcap file as Serenity evidence: {}", pcapFile);
      }
    } catch (Exception e) {
      log.debug("Could not register pcap file as Serenity evidence: {}", e.getMessage());
    }
  }

  /** Whether Serenity evidence registration is available. Extracted for testability. */
  protected boolean isSerenityAvailable() {
    return TigerDirector.isSerenityAvailable();
  }

  /** Returns or creates the evidence directory. */
  private static Path computeEvidenceDir() throws IOException {
    Path parentDir = Paths.get("target", "evidences");
    if (Files.notExists(parentDir)) {
      Files.createDirectories(parentDir);
    }
    return parentDir;
  }
}
