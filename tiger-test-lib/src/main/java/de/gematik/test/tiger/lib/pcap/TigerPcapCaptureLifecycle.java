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
import de.gematik.test.tiger.common.data.config.tigerproxy.PcapCaptureConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerConfigurationRoute;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.lib.TigerDirector;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.client.TigerRemoteProxyClient;
import de.gematik.test.tiger.proxy.data.TigerProxyRoute;
import de.gematik.test.tiger.proxy.handler.pcap.PcapMerger;
import de.gematik.test.tiger.proxy.handler.pcap.ProxyPortDiscovery;
import de.gematik.test.tiger.testenvmgr.env.TigerStatusUpdate;
import de.gematik.test.tiger.testenvmgr.env.TigerUpdateListener;
import de.gematik.test.tiger.testenvmgr.servers.TigerProxyServer;
import io.cucumber.plugin.event.TestCase;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import kong.unirest.core.Unirest;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.serenitybdd.core.Serenity;
import net.serenitybdd.model.environment.ConfiguredEnvironment;
import org.jspecify.annotations.NonNull;

@Slf4j
public class TigerPcapCaptureLifecycle implements TigerUpdateListener {

  private static final String PCAPNG_SUFFIX = ".pcapng";
  private static final int MAX_BASE_FILENAME_LENGTH = 30;

  private ScenarioPcapCaptureService captureService;
  private Path evidenceDir;
  private boolean splitByTestcase;
  private boolean initialized = false;
  private Path suitePcapFile;
  private boolean startSuspended = false;
  private String filenameTemplate;
  private boolean manualFilterConfigured = false;

  private RemotePcapCoordinator remotePcapCoordinator;
  private boolean remoteProxiesEnabled = false;
  private int remoteRequestTimeoutMs = (int) Duration.ofSeconds(30).toMillis();

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
      captureService.setDropDuplicatePackets(pcapConfig.isDropDuplicatePackets());
      captureService.setInterfaceNames(pcapConfig.getInterfaceNames());
      captureService.setManualBpfFilter(pcapConfig.getBpfFilter());
      manualFilterConfigured =
          pcapConfig.getBpfFilter() != null && !pcapConfig.getBpfFilter().isBlank();
      ScenarioPcapCaptureService.setInstance(captureService);

      suitePcapFile = newSuitePcapFilePath();

      if (pcapConfig.isRemoteProxies()) {
        remoteProxiesEnabled = true;
        remotePcapCoordinator = new RemotePcapCoordinator();
        remotePcapCoordinator.setCaptureConfigResolver(this::resolveRemoteCaptureConfig);
        Duration remoteTimeout = Duration.ofSeconds(pcapConfig.getRemoteDownloadTimeoutSeconds());
        remotePcapCoordinator.setRequestTimeout(remoteTimeout);
        remoteRequestTimeoutMs = (int) Math.min(remoteTimeout.toMillis(), Integer.MAX_VALUE);
        RemotePcapCoordinator.setInstance(remotePcapCoordinator);
        log.info("Remote pcap capture enabled; coordinator initialized");
        startRemoteSuiteCapture();
      }

      discoverAndConfigurePorts();
      initialized = true;
      log.info("*** Pcap capture service initialized ***");
    } catch (IOException | RuntimeException e) {
      log.warn("Failed to initialize pcap capture: {}", e.getMessage());
      log.debug("Initialization error:", e);
    }
  }

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
        Path testcaseFile = testcaseFilePath(scenarioId, dataVariantIndex);
        captureService.openDumper(testcaseFile, startSuspended);
        log.debug("Opened pcap capture file for testcase: {}", testcaseFile);
      }

      startRemoteProxiesCaptures(scenarioId);
    } catch (RuntimeException e) {
      log.warn("Failed to handle testcase start for pcap capture: {}", e.getMessage());
      log.debug("Testcase start error:", e);
    }
  }

  protected TigerPcapCaptureConfig loadPcapConfig() {
    return TigerDirector.getLibConfig().getPcapCapture();
  }

  protected ScenarioPcapCaptureService createCaptureService(TigerPcapCaptureConfig pcapConfig) {
    return new ScenarioPcapCaptureService(pcapConfig.getSnaplenKb(), pcapConfig.getBufferSizeKb());
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

  private void registerForLateServerUpdates() {
    if (manualFilterConfigured || !captureService.isEnabled()) {
      return;
    }
    try {
      registerEnvUpdateListener();
    } catch (RuntimeException e) {
      log.warn("Could not subscribe to test environment updates: {}", e.getMessage());
      log.debug("Listener registration error:", e);
    }
  }

  protected void registerEnvUpdateListener() {
    TigerDirector.getTigerTestEnvMgr().registerNewListener(this);
  }

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
    } catch (RuntimeException e) {
      log.warn("Failed to widen the pcap filter after a server update: {}", e.getMessage());
      log.debug("Server update error:", e);
    }
  }

  private void widenFilterForLateServers() {
    if (manualFilterConfigured || captureService == null || !captureService.isEnabled()) {
      return;
    }
    captureService.alsoCapturePorts(discoverProxyPorts());
  }

  private void startCaptureWithPorts(Set<Integer> ports) {
    captureService.start(ports);
    if (!captureService.isEnabled()) {
      return;
    }
    if (startSuspended && splitByTestcase) {
      log.info("Pcap capture started; each scenario starts suspended until resumed");
    } else if (startSuspended) {
      captureService.suspend();
      log.info("Pcap capture started but suspended; call resume() to start capturing");
    } else {
      captureService.rotate(suitePcapFile);
      log.debug("Opened pcap file: {}", suitePcapFile);
    }
  }

  public void onTestCaseFinished(TestCase testCase, String scenarioId, int dataVariantIndex) {
    try {
      if (!initialized || captureService == null) {
        return;
      }

      boolean mergeRemote = remoteProxiesEnabled && remotePcapCoordinator != null;

      Path testcaseFile = null;
      long capturedInTestcaseFile = 0;
      List<File> localTempFiles = List.of();
      if (splitByTestcase) {
        testcaseFile = testcaseFilePath(scenarioId, dataVariantIndex);
        if (mergeRemote) {
          localTempFiles = captureService.closeDumperRawFiles(testcaseFile);
        } else {
          capturedInTestcaseFile = captureService.closeDumper(testcaseFile);
        }
        log.debug("Closed pcap capture file for testcase: {}", testcaseFile);
      }

      if (mergeRemote) {
        mergeInBackground(scenarioId, dataVariantIndex, testcaseFile, localTempFiles);
        return;
      }

      if (testcaseFile != null) {
        registerEvidenceIfCaptured(testcaseFile, capturedInTestcaseFile);
      }
    } catch (RuntimeException e) {
      log.warn("Failed to register pcap evidence at testcase finish: {}", e.getMessage());
      log.debug("Evidence registration error:", e);
    }
  }

  private void startRemoteSuiteCapture() {
    try {
      Map<String, Duration> remoteProxiesWithOffsets = discoverRemoteProxiesWithClockOffsets();
      if (!remoteProxiesWithOffsets.isEmpty()) {
        remotePcapCoordinator.startRemoteSuiteCapture(
            remoteProxiesWithOffsets, suitePcapFile.getFileName().toString());
        log.info("Remote pcap gap capture started on {} proxies", remoteProxiesWithOffsets.size());
      }
    } catch (RuntimeException e) {
      log.warn("Failed to start remote pcap gap capture: {}", e.getMessage());
      log.debug("Remote gap capture start error:", e);
    }
  }

  private void startRemoteProxiesCaptures(String scenarioId) {
    if (remoteProxiesEnabled && remotePcapCoordinator != null) {
      try {
        Map<String, Duration> remoteProxiesWithOffsets = discoverRemoteProxiesWithClockOffsets();
        if (!remoteProxiesWithOffsets.isEmpty()) {
          remotePcapCoordinator.startRemoteCapture(remoteProxiesWithOffsets, scenarioId);
          log.info(
              "Remote pcap capture started for scenario {} on {} proxies",
              scenarioId,
              remoteProxiesWithOffsets.size());
        }
      } catch (RuntimeException e) {
        log.warn("Failed to start remote pcap capture: {}", e.getMessage());
        log.debug("Remote capture start error:", e);
      }
    }
  }

  private void mergeInBackground(
      String scenarioId, int dataVariantIndex, Path testcaseFile, List<File> localTempFiles) {
    boolean anythingCaptured =
        remotePcapCoordinator.hasActiveCaptures()
            || localTempFiles.stream().anyMatch(file -> file.length() > 0);
    Map<String, RemotePcapMetadata> remotePcapMetadata = stopRemoteCapture();
    Path evidenceCopy =
        testcaseFile != null && anythingCaptured ? registerPlaceholderEvidence(testcaseFile) : null;

    PcapMergeQueue.getInstance()
        .submit(
            () -> {
              long merged =
                  mergeAllSources(
                      remotePcapMetadata,
                      scenarioId,
                      dataVariantIndex,
                      testcaseFile,
                      localTempFiles);
              if (merged < 0 && !remotePcapMetadata.isEmpty()) {
                log.warn("Merging with the remote captures failed; merging the local ones only");
                merged =
                    mergeAllSources(
                        Map.of(), scenarioId, dataVariantIndex, testcaseFile, localTempFiles);
              }
              if (evidenceCopy != null && merged > 0) {
                replaceEvidenceCopy(evidenceCopy, testcaseFile);
              }
            });
  }

  private Path registerPlaceholderEvidence(Path pcapFile) {
    if (!isSerenityAvailable()) {
      return null;
    }
    try {
      Files.write(pcapFile, new byte[0]);
      Path downloads = serenityOutputDirectory().resolve("downloadable");
      Set<Path> before = filesIn(downloads);
      registerEvidence(pcapFile);
      Set<Path> added = filesIn(downloads);
      added.removeAll(before);
      return added.stream()
          .filter(copy -> copy.getFileName().toString().endsWith("-" + pcapFile.getFileName()))
          .findFirst()
          .orElse(null);
    } catch (IOException | RuntimeException e) {
      log.warn("Could not register pcap placeholder as evidence: {}", e.getMessage());
      return null;
    }
  }

  private static Set<Path> filesIn(Path directory) throws IOException {
    if (!Files.isDirectory(directory)) {
      return new HashSet<>();
    }
    try (var files = Files.list(directory)) {
      return files.collect(Collectors.toSet());
    }
  }

  private void replaceEvidenceCopy(Path evidenceCopy, Path mergedFile) {
    try {
      Files.copy(mergedFile, evidenceCopy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      log.warn("Could not update the pcap evidence {}: {}", evidenceCopy, e.getMessage());
    }
  }

  protected Path serenityOutputDirectory() {
    return ConfiguredEnvironment.getConfiguration().getOutputDirectory().toPath();
  }

  private Map<String, RemotePcapMetadata> stopRemoteCapture() {
    try {
      return remotePcapCoordinator.stopRemoteCapture();
    } catch (RuntimeException e) {
      log.warn("Failed to stop remote pcap capture: {}", e.getMessage());
      log.debug("Remote capture stop error:", e);
      return Map.of();
    }
  }

  public void onTestRunFinished() {
    try {
      if (!initialized || captureService == null) {
        return;
      }

      PcapMergeQueue.getInstance().awaitAll();
      long capturedInSuiteFile = captureService.getCurrentDumperPacketCount();
      captureService.stop();

      if (remotePcapCoordinator != null) {
        capturedInSuiteFile = stopAndMergeRemoteSuiteCapture(capturedInSuiteFile);
        remotePcapCoordinator.clear();
      }

      ScenarioPcapCaptureService.setInstance(null);
      RemotePcapCoordinator.setInstance(null);
      registerEvidenceIfCaptured(suitePcapFile, capturedInSuiteFile);

      log.debug("Pcap capture service stopped");
    } catch (RuntimeException e) {
      log.warn("Failed to stop pcap capture: {}", e.getMessage());
      log.debug("Stop error:", e);
    }
  }

  private long stopAndMergeRemoteSuiteCapture(long capturedLocally) {
    Map<String, RemotePcapMetadata> remoteGapMetadata;
    try {
      remoteGapMetadata = remotePcapCoordinator.stopRemoteSuiteCapture();
    } catch (RuntimeException e) {
      log.warn("Failed to stop remote pcap gap capture: {}", e.getMessage());
      log.debug("Remote gap capture stop error:", e);
      return capturedLocally;
    }
    if (remoteGapMetadata.isEmpty()) {
      return capturedLocally;
    }

    try {
      List<File> localTempFiles = List.of();
      if (Files.exists(suitePcapFile) && Files.size(suitePcapFile) > 0) {
        Path localTemp =
            suitePcapFile.resolveSibling("." + suitePcapFile.getFileName() + ".gap.tmp");
        Files.move(suitePcapFile, localTemp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        localTempFiles = List.of(localTemp.toFile());
      }
      long merged =
          mergeAllSources(remoteGapMetadata, "suite_gap", -1, suitePcapFile, localTempFiles);
      log.info("Remote pcap gap capture stopped and merged into suite file");
      return merged >= 0 ? merged : capturedLocally;
    } catch (IOException | RuntimeException e) {
      log.warn("Failed to merge remote pcap gap capture into suite file: {}", e.getMessage());
      log.debug("Remote gap capture merge error:", e);
      return capturedLocally;
    }
  }

  private void registerEvidenceIfCaptured(Path pcapFile, long packetCount) {
    if (pcapFile != null && packetCount > 0 && Files.exists(pcapFile)) {
      registerEvidence(pcapFile);
    }
  }

  protected Set<Integer> discoverProxyPorts() {
    Set<Integer> ports = new HashSet<>();
    try {
      var servers = TigerDirector.getTigerTestEnvMgr().getServersOfType(TigerProxyServer.class);
      for (TigerProxyServer server : servers) {
        discoverProxyPorts(server, ports);
      }

      discoverLocalProxyPorts(ports);
    } catch (RuntimeException e) {
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
    } catch (RuntimeException e) {
      log.debug("Could not get proxy from TigerDirector: {}", e.getMessage());
    }
  }

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
    } catch (RuntimeException e) {
      log.debug("Could not read the ports of a running Tiger proxy: {}", e.getMessage());
    }
  }

  private static void addPortOf(String targetUri, Set<Integer> ports) {
    try {
      int targetPort =
          ProxyPortDiscovery.portOf(TigerGlobalConfiguration.resolvePlaceholders(targetUri));
      if (targetPort > 0) {
        ports.add(targetPort);
      }
    } catch (RuntimeException e) {
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

      Optional.ofNullable(server.getTigerProxyBean())
          .ifPresent(proxy -> addPortsOfRunningProxy(proxy, ports));
    } catch (RuntimeException e) {
      log.warn("Failed to discover ports for server {}", server.getServerId(), e);
    }
  }

  private Path newSuitePcapFilePath() {
    return evidenceDir.resolve("suite_" + System.currentTimeMillis() + PCAPNG_SUFFIX);
  }

  private Path testcaseFilePath(String scenarioId, int dataVariantIndex) {
    return evidenceDir.resolve(resolveTestcaseFileName(scenarioId, dataVariantIndex));
  }

  private String resolveTestcaseFileName(String scenarioId, int dataVariantIndex) {
    String base = filenameTemplate.replace("${scenarioId}", scenarioId);
    if (base.endsWith(PCAPNG_SUFFIX)) {
      base = base.substring(0, base.length() - PCAPNG_SUFFIX.length());
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
    return base + PCAPNG_SUFFIX;
  }

  private String replaceSpecialCharacters(String name) {
    return name.replaceAll("[äÄöÖüÜß]+", "_")
        .replaceAll("[^\\w.-]+", "_")
        .replaceAll("_+", "_")
        .replaceAll("(^_)|(_$)", "");
  }

  protected void registerEvidence(Path pcapFile) {
    try {
      if (isSerenityAvailable()) {
        (Serenity.recordReportData().asEvidence().withTitle("Network Traffic (PCAP)"))
            .downloadable()
            .fromFile(pcapFile);
        log.debug("Registered pcap file as Serenity evidence: {}", pcapFile);
      }
    } catch (IOException | RuntimeException e) {
      log.debug("Could not register pcap file as Serenity evidence: {}", e.getMessage());
    }
  }

  protected boolean isSerenityAvailable() {
    return TigerDirector.isSerenityAvailable();
  }

  private static Path computeEvidenceDir() throws IOException {
    Path parentDir = Paths.get("target", "evidences");
    if (Files.notExists(parentDir)) {
      Files.createDirectories(parentDir);
    }
    return parentDir;
  }

  protected Map<String, Duration> discoverRemoteProxiesWithClockOffsets() {
    try {
      return TigerDirector.getTigerTestEnvMgr().getLocalTigerProxyOptional().stream()
          .map(TigerProxy::getRemoteProxyClients)
          .flatMap(List::stream)
          .collect(
              Collectors.toMap(
                  TigerRemoteProxyClient::getRemoteProxyUrl,
                  TigerRemoteProxyClient::getRemoteClockOffset));
    } catch (RuntimeException e) {
      log.debug("Could not discover remote proxies: {}", e.getMessage());
      return Collections.emptyMap();
    }
  }

  protected PcapCaptureConfiguration resolveRemoteCaptureConfig(String proxyUrl) {
    int port = ProxyPortDiscovery.portOf(proxyUrl);
    PcapCaptureConfiguration config =
        TigerDirector.getTigerTestEnvMgr().getServersOfType(TigerProxyServer.class).stream()
            .map(server -> server.getConfiguration().getTigerProxyConfiguration())
            .filter(cfg -> cfg != null && cfg.getAdminPort() == port)
            .map(TigerProxyConfiguration::getPcapCapture)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    if (config == null) {
      log.info(
          "No 'pcapCapture' block found for the server with admin port {} behind {}: its own"
              + " settings apply there",
          port,
          proxyUrl);
    } else {
      log.debug("Sending the 'pcapCapture' settings of admin port {} to {}", port, proxyUrl);
    }
    return config;
  }

  protected long mergeAllSources(
      Map<String, RemotePcapMetadata> remotePcapMetadata,
      String scenarioId,
      int dataVariantIndex,
      Path localFile,
      List<File> localTempFiles) {
    Path finalTarget =
        localFile != null
            ? localFile
            : evidenceDir.resolve("merged_" + replaceSpecialCharacters(scenarioId) + PCAPNG_SUFFIX);
    try {
      val merger = new PcapMerger();
      merger.setDropDuplicates(loadPcapConfig().isDropDuplicatePackets());

      for (File localTemp : localTempFiles) {
        if (localTemp.exists() && localTemp.length() > 0) {
          merger.addRemotePcap(localTemp.getName(), localTemp, 0L);
        }
      }

      TigerPcapCaptureConfig pcapConfig = loadPcapConfig();
      if (pcapConfig.isMergeRemotePcaps()) {
        for (val metaDataPerProxyUrl : remotePcapMetadata.entrySet()) {
          String proxyUrl = metaDataPerProxyUrl.getKey();
          RemotePcapMetadata metadata = metaDataPerProxyUrl.getValue();
          downloadAndAddToMerger(metadata, merger, proxyUrl, scenarioId, dataVariantIndex);
        }
      } else if (!remotePcapMetadata.isEmpty()) {
        log.debug(
            "Remote pcap merge disabled; {} remote capture(s) will not be included",
            remotePcapMetadata.size());
      }

      Path mergedTemp = finalTarget.resolveSibling("." + finalTarget.getFileName() + ".merge.tmp");
      long packetsWritten = merger.mergeToFile(mergedTemp.toFile());
      Files.move(mergedTemp, finalTarget, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

      for (File localTemp : localTempFiles) {
        Files.deleteIfExists(localTemp.toPath());
      }

      log.debug(
          "Merged {} packets from {} local interface(s) + {} remote source(s) into {}",
          packetsWritten,
          localTempFiles.size(),
          remotePcapMetadata.size(),
          finalTarget);
      return packetsWritten;
    } catch (IOException | RuntimeException e) {
      log.warn(
          "Failed to merge pcap sources for scenario {} into {}: {} ({} local temp file(s) left in"
              + " place for recovery)",
          scenarioId,
          finalTarget,
          e.getMessage(),
          localTempFiles.size());
      log.debug("Merge error:", e);
      return -1;
    }
  }

  private void downloadAndAddToMerger(
      RemotePcapMetadata metadata,
      PcapMerger merger,
      String proxyUrl,
      String scenarioId,
      int dataVariantIndex) {
    try {
      File downloadedPcap = computeTargetFilename(proxyUrl, scenarioId, dataVariantIndex);
      downloadRemotePcap(proxyUrl + metadata.getDownloadUrl(), downloadedPcap);
      if (downloadedPcap.exists() && downloadedPcap.length() > 0) {
        merger.addRemotePcap(proxyUrl, downloadedPcap, metadata.getClockOffset());
      }
    } catch (RuntimeException e) {
      log.warn("Failed to download pcap from {}: {}", proxyUrl, e.getMessage());
      metadata.setError(e.getMessage());
    }
  }

  private @NonNull File computeTargetFilename(
      String proxyUrl, String scenarioId, int dataVariantIndex) {
    String proxyId = extractProxyId(proxyUrl);
    String sanitizedScenario = replaceSpecialCharacters(scenarioId);
    if (dataVariantIndex >= 0) {
      sanitizedScenario += "_" + (dataVariantIndex + 1);
    }

    String filename = "pcap_" + sanitizedScenario + "_" + proxyId + PCAPNG_SUFFIX;
    return evidenceDir.resolve(filename).toFile();
  }

  private void downloadRemotePcap(String fullUrl, File targetFile) {
    try {
      log.debug("Downloading remote pcap from: {}", fullUrl);

      downloadToFile(fullUrl, targetFile);

      if (targetFile.exists() && targetFile.length() > 0) {
        log.debug(
            "Successfully downloaded remote pcap from {}: {} bytes to {}",
            fullUrl,
            targetFile.length(),
            targetFile.getAbsolutePath());
      } else {
        log.warn("Downloaded pcap file is empty or missing: {}", targetFile.getAbsolutePath());
        Files.deleteIfExists(targetFile.toPath());
      }
    } catch (IOException | RuntimeException e) {
      log.warn("Failed to download remote pcap from {}: {}", fullUrl, e.getMessage());
      log.debug("Download error details:", e);
    }
  }

  private static String extractProxyId(String proxyUrl) {
    String proxyId;
    try {
      proxyId = new URI(proxyUrl).getHost();
      if (proxyId == null || proxyId.isEmpty()) {
        proxyId = "remote-proxy";
      }
    } catch (URISyntaxException e) {
      proxyId = "remote-proxy";
    }
    return proxyId;
  }

  private void downloadToFile(String fullUrl, File targetFile) throws IOException {
    // Unirest only writes to a file that does not exist yet.
    Files.deleteIfExists(targetFile.toPath());
    var response =
        Unirest.get(fullUrl)
            .requestTimeout(remoteRequestTimeoutMs)
            .asFile(targetFile.getAbsolutePath());
    if (!response.isSuccess()) {
      String error = Files.exists(targetFile.toPath()) ? Files.readString(targetFile.toPath()) : "";
      Files.deleteIfExists(targetFile.toPath());
      log.warn(
          "Download of {} failed with HTTP status {}: {}", fullUrl, response.getStatus(), error);
    }
  }
}
