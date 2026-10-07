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

import de.gematik.test.tiger.common.data.config.tigerproxy.PcapCaptureConfiguration;
import de.gematik.test.tiger.proxy.controller.PcapAdminController;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import kong.unirest.core.HttpResponse;
import kong.unirest.core.Unirest;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RemotePcapCoordinator {

  private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);

  @Setter @Getter private static RemotePcapCoordinator instance;

  private final ThreadLocal<Map<String, RemoteProxyCapture>> threadCaptures =
      ThreadLocal.withInitial(HashMap::new);

  private final Map<String, RemoteProxyCapture> gapCaptures = new ConcurrentHashMap<>();

  private final String suiteId = UUID.randomUUID().toString();

  private final ExecutorService executor =
      Executors.newCachedThreadPool(
          new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger();

            @Override
            public Thread newThread(Runnable runnable) {
              Thread thread =
                  new Thread(runnable, "pcap-remote-coordinator-" + counter.incrementAndGet());
              thread.setDaemon(true);
              return thread;
            }
          });

  @Setter private volatile Duration requestTimeout = DEFAULT_REQUEST_TIMEOUT;

  @Setter
  private Function<String, PcapCaptureConfiguration> captureConfigResolver = proxyUrl -> null;

  static class RemoteProxyCapture {
    String baseUrl;
    String captureId;
    String lastFilename;
    Duration clockOffset;
    boolean captureActive;

    RemoteProxyCapture(String baseUrl, Duration clockOffset) {
      this.baseUrl = baseUrl;
      this.clockOffset = clockOffset;
      this.captureActive = false;
    }
  }

  public void startRemoteCapture(
      Map<String, Duration> proxyUrlsWithClockOffsets, String scenarioId) {
    if (proxyUrlsWithClockOffsets == null || proxyUrlsWithClockOffsets.isEmpty()) {
      log.debug("No remote proxies configured for distributed capture.");
      return;
    }

    log.info(
        "Starting remote pcap capture for scenario: {} on {} proxies",
        scenarioId,
        proxyUrlsWithClockOffsets.size());

    startOnAll(
        threadCaptures.get(),
        proxyUrlsWithClockOffsets,
        scenarioId + ".pcapng",
        "Scenario: " + scenarioId,
        false);
  }

  public boolean hasActiveCaptures() {
    return !activeCaptures().isEmpty();
  }

  public Map<String, RemotePcapMetadata> stopRemoteCapture() {
    Map<String, RemotePcapMetadata> metadata = stopAllActiveCaptures(threadCaptures.get());
    threadCaptures.remove();
    return metadata;
  }

  public void startRemoteSuiteCapture(
      Map<String, Duration> proxyUrlsWithClockOffsets, String filename) {
    if (proxyUrlsWithClockOffsets == null || proxyUrlsWithClockOffsets.isEmpty()) {
      log.debug("No remote proxies configured for distributed capture.");
      return;
    }

    log.info("Starting remote pcap gap capture on {} proxies", proxyUrlsWithClockOffsets.size());

    startOnAll(gapCaptures, proxyUrlsWithClockOffsets, filename, "Suite gap capture", true);
  }

  public Map<String, RemotePcapMetadata> stopRemoteSuiteCapture() {
    Map<String, RemotePcapMetadata> metadata = stopAllActiveCaptures(gapCaptures);
    gapCaptures.clear();
    return metadata;
  }

  public void suspend() {
    runInParallel(activeCaptures(), capture -> setSuspendedOnProxy(capture, true));
  }

  public void resume() {
    runInParallel(activeCaptures(), capture -> setSuspendedOnProxy(capture, false));
  }

  private void startOnAll(
      Map<String, RemoteProxyCapture> captures,
      Map<String, Duration> proxyUrlsWithClockOffsets,
      String filename,
      String metadataLabel,
      boolean gap) {
    List<RemoteProxyCapture> toStart = new ArrayList<>();
    for (Map.Entry<String, Duration> entry : proxyUrlsWithClockOffsets.entrySet()) {
      RemoteProxyCapture capture = new RemoteProxyCapture(entry.getKey(), entry.getValue());
      captures.put(entry.getKey(), capture);
      toStart.add(capture);
      log.debug(
          "Proxy {} registered with clock offset: {} ms",
          entry.getKey(),
          entry.getValue().toMillis());
    }
    runInParallel(toStart, capture -> startProxyCapture(capture, filename, metadataLabel, gap));
  }

  private List<RemoteProxyCapture> activeCaptures() {
    return threadCaptures.get().values().stream().filter(s -> s.captureActive).toList();
  }

  private void runInParallel(
      Collection<RemoteProxyCapture> items, Consumer<RemoteProxyCapture> action) {
    if (items.size() <= 1) {
      items.forEach(action);
      return;
    }
    List<CompletableFuture<Void>> futures =
        items.stream()
            .map(item -> CompletableFuture.runAsync(() -> action.accept(item), executor))
            .toList();
    futures.forEach(CompletableFuture::join);
  }

  private static String errorOf(HttpResponse<PcapAdminController.CaptureStatusResponse> response) {
    PcapAdminController.CaptureStatusResponse body = response.getBody();
    return body != null && body.getError() != null ? body.getError() : "no error given";
  }

  private int timeoutMillis() {
    return (int) Math.min(requestTimeout.toMillis(), Integer.MAX_VALUE);
  }

  private void setSuspendedOnProxy(RemoteProxyCapture capture, boolean suspend) {
    String path = suspend ? "/pcap/admin/suspend" : "/pcap/admin/resume";
    try {
      var response =
          Unirest.post(capture.baseUrl + path)
              .queryString("captureId", capture.captureId)
              .requestTimeout(timeoutMillis())
              .asObject(PcapAdminController.CaptureStatusResponse.class);
      if (!response.isSuccess()) {
        log.error(
            "Failed to {} capture on {} (capture {}): HTTP status {}: {}",
            suspend ? "suspend" : "resume",
            capture.baseUrl,
            capture.captureId,
            response.getStatus(),
            errorOf(response));
        return;
      }
      log.info(
          "Capture {} on {} (capture {})",
          suspend ? "suspended" : "resumed",
          capture.baseUrl,
          capture.captureId);
    } catch (RuntimeException e) {
      log.error("Failed to {} capture on {}", suspend ? "suspend" : "resume", capture.baseUrl, e);
    }
  }

  private Map<String, RemotePcapMetadata> stopAllActiveCaptures(
      Map<String, RemoteProxyCapture> captures) {
    Map<String, RemotePcapMetadata> metadata = new ConcurrentHashMap<>();
    List<RemoteProxyCapture> active =
        captures.values().stream().filter(s -> s.captureActive).toList();
    log.info("Stopping remote pcap capture ({} proxies)", active.size());

    runInParallel(
        active,
        capture -> {
          try {
            RemotePcapMetadata meta = stopProxyCapture(capture);
            if (meta != null) {
              metadata.put(capture.baseUrl, meta);
            }
          } catch (RuntimeException e) {
            log.error("Failed to stop capture on proxy {}", capture.baseUrl, e);
          }
        });

    return new HashMap<>(metadata);
  }

  private void startProxyCapture(
      RemoteProxyCapture capture, String filename, String metadataLabel, boolean gap) {
    try {
      log.debug("Signaling proxy {} to start capture: {}", capture.baseUrl, filename);

      var request =
          PcapAdminController.StartCaptureRequest.builder()
              .filename(filename)
              .metadata(metadataLabel)
              .gap(gap)
              .suiteId(suiteId)
              .pcapCapture(captureConfigResolver.apply(capture.baseUrl))
              .build();
      var response =
          Unirest.post(capture.baseUrl + "/pcap/admin/start")
              .header("Content-Type", "application/json")
              .requestTimeout(timeoutMillis())
              .body(request)
              .asObject(PcapAdminController.CaptureStatusResponse.class);

      if (PcapAdminController.CaptureStatusResponse.answeredCapturing(response)) {
        capture.captureActive = true;
        capture.captureId = response.getBody().getCaptureId();
        capture.lastFilename = filename;
        log.info(
            "Capture started on {}: capture {} ({})", capture.baseUrl, capture.captureId, filename);
      } else if (response.getStatus() == 403) {
        String reason = response.getBody() == null ? null : response.getBody().getError();
        log.error(
            "Remote proxy {} refused to capture: {}",
            capture.baseUrl,
            reason != null
                ? reason
                : "its configuration has no 'pcapCapture' block, or it does not allow what was"
                    + " asked for");
      } else {
        log.error(
            "Failed to start capture on {}: HTTP status {}: {}",
            capture.baseUrl,
            response.getStatus(),
            errorOf(response));
      }
    } catch (RuntimeException e) {
      log.error("Failed to signal proxy {} to start capture", capture.baseUrl, e);
    }
  }

  private RemotePcapMetadata stopProxyCapture(RemoteProxyCapture capture) {
    try {
      var response =
          Unirest.post(capture.baseUrl + "/pcap/admin/stop")
              .queryString("captureId", capture.captureId)
              .requestTimeout(timeoutMillis())
              .asObject(PcapAdminController.CaptureStatusResponse.class);

      if (!PcapAdminController.CaptureStatusResponse.answeredStopped(response)) {
        log.error(
            "Failed to stop capture on {} (capture {}): HTTP status {}: {}",
            capture.baseUrl,
            capture.captureId,
            response.getStatus(),
            errorOf(response));
        capture.captureActive = false;
        return null;
      }

      capture.captureActive = false;
      PcapAdminController.CaptureStatusResponse status = response.getBody();

      log.info(
          "Capture stopped on {} (capture {}): {} packets, {} bytes",
          capture.baseUrl,
          capture.captureId,
          status.getPacketCount(),
          status.getFileSizeBytes());

      return RemotePcapMetadata.builder()
          .proxyUrl(capture.baseUrl)
          .filename(status.getFilename())
          .downloadUrl(status.getDownloadUrl())
          .clockOffset(capture.clockOffset)
          .packetCount(status.getPacketCount())
          .fileSizeBytes(status.getFileSizeBytes())
          .build();
    } catch (RuntimeException e) {
      log.error("Failed to stop capture on {}", capture.baseUrl, e);
      capture.captureActive = false;
      return null;
    }
  }

  public void clear() {
    threadCaptures.remove();
  }
}
