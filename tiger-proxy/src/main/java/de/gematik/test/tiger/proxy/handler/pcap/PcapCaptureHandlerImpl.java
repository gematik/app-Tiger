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

import de.gematik.test.tiger.common.data.config.tigerproxy.PcapCaptureConfiguration;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.client.TigerRemoteProxyClient;
import de.gematik.test.tiger.proxy.controller.PcapAdminController;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import kong.unirest.core.Unirest;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.util.VisibleForTesting;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PcapCaptureHandlerImpl implements PcapCaptureHandler {

  private static final int RELAY_REQUEST_TIMEOUT_MS = 30_000;
  private static final int RELAY_DOWNLOAD_TIMEOUT_MS = 300_000;
  private static final int RELAY_STOP_BUDGET_MS = 20_000;
  private static final Duration CLOSED_FILE_RETENTION = Duration.ofHours(1);
  private static final int MAX_REQUESTED_BUFFER_SIZE_KB = 256 * KB;
  private static final String ANY_INTERFACE = "*";

  private final ConcurrentHashMap<String, PcapCapture> captures = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, ClosedFile> closedFiles = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, List<DownstreamCapture>> downstreamCaptures =
      new ConcurrentHashMap<>();
  private final ExecutorService relayExecutor =
      Executors.newCachedThreadPool(
          runnable -> {
            Thread thread = new Thread(runnable, "pcap-relay");
            thread.setDaemon(true);
            return thread;
          });
  private final File captureDir;
  private final TigerProxy tigerProxy;
  private final Object engineLock = new Object();
  private volatile FanOutPcapCaptureService captureService;
  private volatile Duration closedFileRetention = CLOSED_FILE_RETENTION;

  private record DownstreamCapture(String proxyUrl, String captureId, Duration clockOffset) {
    @Override
    public String toString() {
      return "DownstreamCapture[proxyUrl=" + proxyUrl + ", captureId=" + captureId + "]";
    }
  }

  private record ClosedFile(File file, Instant closedAt) {
    @Override
    public String toString() {
      return "ClosedFile[file=" + file + ", closedAt=" + closedAt + "]";
    }
  }

  public PcapCaptureHandlerImpl(TigerProxy tigerProxy) {
    this.captureDir = new File(System.getProperty("java.io.tmpdir"), "tiger-pcap-captures");
    if (!captureDir.exists() && !captureDir.mkdirs()) {
      log.warn("Failed to create capture directory: {}", captureDir.getAbsolutePath());
    }
    this.tigerProxy = tigerProxy;
    log.info("PCAP capture directory: {}", captureDir.getAbsolutePath());
  }

  static String plainFileName(String label) {
    String name = label == null ? "" : label.replaceAll("[^A-Za-z0-9._-]", "_");
    return name.matches("\\.*") ? "capture.pcapng" : name;
  }

  @Override
  public PcapAdminController.CaptureStatusResponse startCapture(
      String filename, boolean gap, PcapCaptureConfiguration requestedConfig, String suiteId) {
    PcapCaptureConfiguration policy = tigerProxy.getTigerProxyConfiguration().getPcapCapture();
    if (policy == null) {
      throw new PcapCaptureNotAllowedException(
          "This proxy does not accept remote pcap capture requests: add a 'pcapCapture' block"
              + " (an empty one is enough) to its tigerProxy configuration to allow them");
    }
    enforceCapturePolicy(requestedConfig, policy);
    purgeExpiredClosedFiles();
    File pcapFile = new File(captureDir, UUID.randomUUID() + "_" + plainFileName(filename));
    String captureId = UUID.randomUUID().toString();

    try {
      Set<Integer> capturePorts = ProxyPortDiscovery.discoverPorts(tigerProxy, false);
      PcapCapture capture;
      synchronized (engineLock) {
        if (captureService == null) {
          log.debug("Discovered ports for pcap capture: {}", capturePorts);
          captureService = newCaptureService();
        } else {
          captureService.alsoCapturePorts(capturePorts);
        }

        if (!captureService.openCapture(
            captureId,
            pcapFile.toPath(),
            gap,
            requestedConfigOrOwn(requestedConfig),
            capturePorts,
            suiteId)) {
          stopSharedServiceIfIdle();
          return PcapAdminController.CaptureStatusResponse.builder()
              .capturing(false)
              .error("Failed to open pcap capture")
              .build();
        }

        capture =
            PcapCapture.builder()
                .captureId(captureId)
                .filename(filename)
                .pcapFile(pcapFile)
                .startTime(ZonedDateTime.now(ZoneId.systemDefault()))
                .build();
        captures.put(captureId, capture);
      }
      downstreamCaptures.put(captureId, relayStartToDownstreamProxies(filename, gap, suiteId));

      log.info(
          "Pcap capture started: {} ({}) at {}", captureId, filename, pcapFile.getAbsolutePath());

      return PcapAdminController.CaptureStatusResponse.builder()
          .capturing(true)
          .captureId(captureId)
          .filename(filename)
          .startTime(capture.startTime)
          .packetCount(0)
          .fileSizeBytes(0)
          .build();
    } catch (RuntimeException e) {
      log.error("Failed to start pcap capture", e);
      return PcapAdminController.CaptureStatusResponse.failed("Failed to start capture", e);
    }
  }

  protected FanOutPcapCaptureService newCaptureService() {
    return new FanOutPcapCaptureService();
  }

  @Override
  public boolean isKnownCapture(String captureId) {
    return captureId != null
        && (captures.containsKey(captureId) || closedFiles.containsKey(captureId));
  }

  @Override
  public PcapAdminController.CaptureStatusResponse stopCapture(String captureId) {
    PcapCapture capture = captures.remove(captureId);
    if (capture == null) {
      return PcapAdminController.CaptureStatusResponse.builder()
          .capturing(false)
          .error("Unknown or already-closed capture: " + captureId)
          .build();
    }

    ZonedDateTime stopTime = ZonedDateTime.now(ZoneId.systemDefault());
    try {
      long packetCount = 0;
      synchronized (engineLock) {
        if (captureService != null) {
          packetCount = captureService.closeCapture(captureId);
        }
        stopSharedServiceIfIdle();
      }

      File capturedFile = capture.pcapFile;
      if (!capturedFile.exists()) {
        log.warn("Pcap file not found after capture stop: {}", capturedFile.getAbsolutePath());
        return PcapAdminController.CaptureStatusResponse.builder()
            .capturing(false)
            .error("Failed to properly close capture")
            .build();
      }

      List<DownstreamCapture> downstream = downstreamCaptures.remove(captureId);
      if (downstream != null && !downstream.isEmpty()) {
        packetCount = mergeDownstreamIntoLocalFile(capturedFile, packetCount, downstream);
      }

      closedFiles.put(captureId, new ClosedFile(capturedFile, Instant.now()));
      long fileSize = capturedFile.length();
      String downloadUrl = "/pcap/admin/download?captureId=" + encodeUrl(captureId);

      log.info(
          "Pcap capture stopped: {} ({}, {} bytes, {} packets)",
          captureId,
          capture.filename,
          fileSize,
          packetCount);

      return PcapAdminController.CaptureStatusResponse.builder()
          .capturing(false)
          .captureId(captureId)
          .filename(capture.filename)
          .startTime(capture.startTime)
          .stopTime(stopTime)
          .packetCount(packetCount)
          .fileSizeBytes(fileSize)
          .downloadUrl(downloadUrl)
          .build();
    } catch (RuntimeException e) {
      log.error("Failed to stop pcap capture", e);
      return PcapAdminController.CaptureStatusResponse.failed(
          "Failed to properly close capture", e);
    }
  }

  @Override
  public PcapAdminController.CaptureStatusResponse suspendCapture(String captureId) {
    PcapCapture capture = captures.get(captureId);
    if (capture == null || captureService == null || !captureService.suspendCapture(captureId)) {
      return PcapAdminController.CaptureStatusResponse.builder()
          .capturing(false)
          .error("Unknown or already-closed capture: " + captureId)
          .build();
    }
    relaySuspendOrResume(captureId, true);
    log.info("Pcap capture suspended: {} ({})", captureId, capture.filename);
    return PcapAdminController.CaptureStatusResponse.builder()
        .capturing(true)
        .captureId(captureId)
        .filename(capture.filename)
        .startTime(capture.startTime)
        .build();
  }

  @Override
  public PcapAdminController.CaptureStatusResponse resumeCapture(String captureId) {
    PcapCapture capture = captures.get(captureId);
    if (capture == null || captureService == null || !captureService.resumeCapture(captureId)) {
      return PcapAdminController.CaptureStatusResponse.builder()
          .capturing(false)
          .error("Unknown or already-closed capture: " + captureId)
          .build();
    }
    relaySuspendOrResume(captureId, false);
    log.info("Pcap capture resumed: {} ({})", captureId, capture.filename);
    return PcapAdminController.CaptureStatusResponse.builder()
        .capturing(true)
        .captureId(captureId)
        .filename(capture.filename)
        .startTime(capture.startTime)
        .build();
  }

  @Override
  public PcapAdminController.CaptureStatusResponse getStatus(String captureId) {
    PcapCapture capture = captures.get(captureId);
    if (capture == null || captureService == null) {
      return PcapAdminController.CaptureStatusResponse.builder().capturing(false).build();
    }

    return PcapAdminController.CaptureStatusResponse.builder()
        .capturing(true)
        .captureId(captureId)
        .filename(capture.filename)
        .startTime(capture.startTime)
        .packetCount(captureService.getCapturePacketCount(captureId))
        .fileSizeBytes(0)
        .build();
  }

  @Override
  public File getPcapFile(String captureId) {
    ClosedFile closed = closedFiles.get(captureId);
    return closed == null ? null : closed.file();
  }

  @Override
  public void discardPcapFile(String captureId) {
    ClosedFile closed = closedFiles.remove(captureId);
    if (closed != null) {
      deleteQuietly(closed.file());
    }
  }

  @VisibleForTesting
  void setClosedFileRetention(Duration retention) {
    this.closedFileRetention = retention;
  }

  private void purgeExpiredClosedFiles() {
    Instant cutoff = Instant.now().minus(closedFileRetention);
    closedFiles
        .entrySet()
        .removeIf(
            entry -> {
              if (entry.getValue().closedAt().isBefore(cutoff)) {
                deleteQuietly(entry.getValue().file());
                return true;
              }
              return false;
            });
  }

  private static void deleteQuietly(File file) {
    try {
      Files.deleteIfExists(file.toPath());
    } catch (IOException e) {
      log.debug("Could not delete pcap file {}: {}", file, e.getMessage());
    }
  }

  private static void enforceCapturePolicy(
      PcapCaptureConfiguration requested, PcapCaptureConfiguration policy) {
    if (requested == null) {
      return;
    }

    List<String> allowed = interfacesAllowedByPolicy(policy);
    List<String> asked =
        requested.getInterfaceNames() == null ? List.of() : requested.getInterfaceNames();
    if (!allowed.contains(ANY_INTERFACE)) {
      List<String> denied = asked.stream().filter(name -> !allowed.contains(name)).toList();
      if (!denied.isEmpty()) {
        throw new PcapCaptureNotAllowedException(
            "This proxy does not allow capturing on "
                + denied
                + "; allowed: "
                + (allowed.isEmpty() ? "loopback only" : allowed)
                + ". Name them in 'allowedInterfaces' (or 'interfaceNames') of this proxy's own"
                + " 'pcapCapture' block.");
      }
    }

    String filter = requested.getBpfFilter();
    if (filter != null
        && !filter.isBlank()
        && !policy.isAllowCustomFilter()
        && !filter
            .trim()
            .equals(policy.getBpfFilter() == null ? null : policy.getBpfFilter().trim())) {
      throw new PcapCaptureNotAllowedException(
          "This proxy does not accept a capture filter of its own callers: set 'allowCustomFilter:"
              + " true' in its own 'pcapCapture' block, or use this block's own 'bpfFilter'.");
    }

    int mostAllowedKb = Math.max(MAX_REQUESTED_BUFFER_SIZE_KB, policy.getBufferSizeKb());
    if (requested.getBufferSizeKb() > mostAllowedKb) {
      throw new PcapCaptureNotAllowedException(
          "This proxy does not give a caller a kernel buffer of "
              + requested.getBufferSizeKb() / KB
              + " MB; the most is "
              + mostAllowedKb / KB
              + " MB.");
    }
  }

  private static List<String> interfacesAllowedByPolicy(PcapCaptureConfiguration policy) {
    if (policy.getAllowedInterfaces() != null && !policy.getAllowedInterfaces().isEmpty()) {
      return policy.getAllowedInterfaces();
    }
    return policy.getInterfaceNames() == null ? List.of() : policy.getInterfaceNames();
  }

  private PcapCaptureConfiguration requestedConfigOrOwn(PcapCaptureConfiguration requestedConfig) {
    if (requestedConfig != null) {
      return requestedConfig;
    }
    PcapCaptureConfiguration own = tigerProxy.getTigerProxyConfiguration().getPcapCapture();
    return own != null ? own : PcapCaptureConfiguration.builder().build();
  }

  private List<DownstreamCapture> relayStartToDownstreamProxies(
      String filename, boolean gap, String suiteId) {
    List<TigerRemoteProxyClient> clients = tigerProxy.getRemoteProxyClients();
    if (clients.isEmpty()) {
      return List.of();
    }

    List<DownstreamCapture> downstream = new ArrayList<>();
    for (TigerRemoteProxyClient client : clients) {
      try {
        var request =
            PcapAdminController.StartCaptureRequest.builder()
                .filename(filename)
                .metadata("Relayed from " + tigerProxy.getTigerProxyConfiguration().getName())
                .gap(gap)
                .suiteId(suiteId)
                .build();
        var response =
            Unirest.post(client.getRemoteProxyUrl() + "/pcap/admin/start")
                .header("Content-Type", "application/json")
                .requestTimeout(RELAY_REQUEST_TIMEOUT_MS)
                .body(request)
                .asObject(PcapAdminController.CaptureStatusResponse.class);
        if (PcapAdminController.CaptureStatusResponse.answeredCapturing(response)) {
          downstream.add(
              new DownstreamCapture(
                  client.getRemoteProxyUrl(),
                  response.getBody().getCaptureId(),
                  client.getRemoteClockOffset()));
          log.info(
              "Relayed pcap capture start to downstream proxy {}: capture {}",
              client.getRemoteProxyUrl(),
              response.getBody().getCaptureId());
        } else {
          log.atWarn()
              .addArgument(client::getRemoteProxyUrl)
              .addArgument(response::getStatus)
              .log("Failed to relay pcap capture start to downstream proxy {}: status={}");
        }
      } catch (RuntimeException e) {
        log.warn(
            "Failed to relay pcap capture start to downstream proxy {}: {}",
            client.getRemoteProxyUrl(),
            e.getMessage());
        log.debug("Downstream relay start error:", e);
      }
    }
    return downstream;
  }

  private long mergeDownstreamIntoLocalFile(
      File localFile, long localPacketCount, List<DownstreamCapture> downstream) {
    List<File> downloaded = new CopyOnWriteArrayList<>();
    try {
      PcapMerger merger = new PcapMerger();
      if (localFile.exists() && localFile.length() > 0) {
        merger.addRemotePcap("local", localFile, 0L);
      }

      long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(RELAY_STOP_BUDGET_MS);
      CompletableFuture.allOf(
              downstream.stream()
                  .map(
                      ds ->
                          CompletableFuture.runAsync(
                              () ->
                                  stopAndDownloadDownstreamCapture(
                                      ds, downloaded, merger, deadline),
                              relayExecutor))
                  .toArray(CompletableFuture[]::new))
          .join();

      File mergedTemp = File.createTempFile("pcap-merged-", ".pcapng", captureDir);
      long packetsWritten = merger.mergeToFile(mergedTemp);
      Files.move(mergedTemp.toPath(), localFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
      log.debug(
          "Merged {} packets from this proxy + {} downstream proxy/proxies into {}",
          packetsWritten,
          downstream.size(),
          localFile);
      return packetsWritten;
    } catch (IOException | RuntimeException e) {
      log.warn("Failed to merge downstream pcap captures into {}: {}", localFile, e.getMessage());
      log.debug("Downstream merge error:", e);
      return localPacketCount;
    } finally {
      for (File f : downloaded) {
        try {
          Files.deleteIfExists(f.toPath());
        } catch (IOException ignored) {
          log.debug("Could not delete downloaded relay pcap temp file: {}", f);
        }
      }
    }
  }

  private static int timeoutWithin(long deadlineNanos, int maxMs) {
    long remainingMs = TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime());
    return (int) Math.max(1, Math.min(maxMs, remainingMs));
  }

  private void stopAndDownloadDownstreamCapture(
      DownstreamCapture ds, List<File> downloaded, PcapMerger merger, long deadlineNanos) {
    try {
      var stopResponse =
          Unirest.post(ds.proxyUrl() + "/pcap/admin/stop")
              .queryString("captureId", ds.captureId())
              .requestTimeout(timeoutWithin(deadlineNanos, RELAY_REQUEST_TIMEOUT_MS))
              .asObject(PcapAdminController.CaptureStatusResponse.class);
      if (!PcapAdminController.CaptureStatusResponse.answeredStopped(stopResponse)
          || stopResponse.getBody().getDownloadUrl() == null) {
        log.warn(
            "Failed to stop downstream pcap capture on {}: HTTP status {}: {}",
            ds.proxyUrl(),
            stopResponse.getStatus(),
            stopResponse.getBody() == null ? "no body" : stopResponse.getBody().getError());
        return;
      }
      File downloadedFile = File.createTempFile("pcap-relay-", ".pcapng", captureDir);
      downloaded.add(downloadedFile);
      // Unirest only writes to a file that does not exist yet.
      Files.delete(downloadedFile.toPath());
      Unirest.get(ds.proxyUrl() + stopResponse.getBody().getDownloadUrl())
          .requestTimeout(timeoutWithin(deadlineNanos, RELAY_DOWNLOAD_TIMEOUT_MS))
          .asFile(downloadedFile.getAbsolutePath());
      if (downloadedFile.exists() && downloadedFile.length() > 0) {
        synchronized (merger) {
          merger.addRemotePcap(ds.proxyUrl(), downloadedFile, ds.clockOffset());
        }
      }
    } catch (IOException | RuntimeException e) {
      log.warn("Failed to relay-stop/download pcap from downstream proxy {}", ds.proxyUrl(), e);
    }
  }

  private void relaySuspendOrResume(String captureId, boolean suspend) {
    List<DownstreamCapture> downstream = downstreamCaptures.get(captureId);
    if (downstream == null || downstream.isEmpty()) {
      return;
    }
    String path = suspend ? "/pcap/admin/suspend" : "/pcap/admin/resume";
    for (DownstreamCapture ds : downstream) {
      try {
        Unirest.post(ds.proxyUrl() + path)
            .queryString("captureId", ds.captureId())
            .requestTimeout(RELAY_REQUEST_TIMEOUT_MS)
            .asObject(PcapAdminController.CaptureStatusResponse.class);
      } catch (RuntimeException e) {
        log.warn(
            "Failed to relay {} to downstream proxy {}",
            suspend ? "suspend" : "resume",
            ds.proxyUrl(),
            e);
      }
    }
  }

  private void stopSharedServiceIfIdle() {
    if (captures.isEmpty() && captureService != null) {
      captureService.stop();
      captureService = null;
    }
  }

  private String encodeUrl(String value) {
    return value.replace(" ", "%20").replace("&", "%26").replace("=", "%3D");
  }

  @Builder
  @Data
  static class PcapCapture {
    String captureId;

    String filename;
    File pcapFile;
    ZonedDateTime startTime;
  }
}
