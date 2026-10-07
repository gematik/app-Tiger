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

import com.sun.net.httpserver.HttpServer;
import de.gematik.test.tiger.common.data.config.tigerproxy.PcapCaptureConfiguration;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RemotePcapCoordinator")
class RemotePcapCoordinatorTest {

  private RemotePcapCoordinator coordinator;

  @BeforeEach
  void setUp() {
    coordinator = new RemotePcapCoordinator();
  }

  @Test
  @DisplayName("should handle empty proxy list gracefully")
  void testEmptyProxyList() {
    coordinator.startRemoteCapture(Map.of(), "test-scenario");
    Map<String, RemotePcapMetadata> result = coordinator.stopRemoteCapture();
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("should handle null proxy list gracefully")
  void testNullProxyList() {
    coordinator.startRemoteCapture(null, "test-scenario");
    Map<String, RemotePcapMetadata> result = coordinator.stopRemoteCapture();
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("should clear captures")
  void testClear() {
    Map<String, Duration> proxies = new HashMap<>();
    proxies.put("http://proxy1:8080", Duration.ZERO);
    coordinator.startRemoteCapture(proxies, "test-scenario");

    coordinator.clear();

    Map<String, RemotePcapMetadata> result = coordinator.stopRemoteCapture();
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("capture tracking is isolated per thread, not shared across concurrent scenarios")
  void captureTrackingIsIsolatedPerThread() throws InterruptedException {
    // The single coordinator instance is shared for the whole suite (see class javadoc). This
    // proves thread A registering proxies never becomes visible to thread B's own capture
    // bookkeeping, which is the property that makes two scenarios sharing one remote proxy safe
    // under parallel execution.
    CountDownLatch threadAStarted = new CountDownLatch(1);
    CountDownLatch threadBChecked = new CountDownLatch(1);
    Map<String, RemotePcapMetadata>[] threadBResult = new Map[1];

    Thread threadA =
        new Thread(
            () -> {
              Map<String, Duration> proxies = new HashMap<>();
              proxies.put("http://shared-proxy:8080", Duration.ZERO);
              coordinator.startRemoteCapture(proxies, "scenario-a");
              threadAStarted.countDown();
              awaitQuietly(threadBChecked);
              coordinator.stopRemoteCapture();
            });

    Thread threadB =
        new Thread(
            () -> {
              awaitQuietly(threadAStarted);
              // Thread B never started anything itself; if state were shared, thread A's
              // "shared-proxy" entry would leak in here.
              threadBResult[0] = coordinator.stopRemoteCapture();
              threadBChecked.countDown();
            });

    threadA.start();
    threadB.start();
    threadA.join(10_000);
    threadB.join(10_000);

    assertThat(threadBResult[0]).isEmpty();
  }

  @Test
  @DisplayName("suspend()/resume() are safe no-ops with no active captures")
  void suspendResumeAreNoopsWithNoActiveCaptures() {
    assertThatCode(() -> coordinator.suspend()).doesNotThrowAnyException();
    assertThatCode(() -> coordinator.resume()).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("startRemoteSuiteCapture/stopRemoteSuiteCapture handle empty proxy list gracefully")
  void suiteCaptureHandlesEmptyProxyList() {
    coordinator.startRemoteSuiteCapture(Map.of(), "suite.pcapng");
    Map<String, RemotePcapMetadata> result = coordinator.stopRemoteSuiteCapture();
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("getInstance()/setInstance() round-trip")
  void staticInstanceRoundTrips() {
    RemotePcapCoordinator.setInstance(coordinator);
    try {
      assertThat(RemotePcapCoordinator.getInstance()).isSameAs(coordinator);
    } finally {
      RemotePcapCoordinator.setInstance(null);
    }
  }

  @Test
  @DisplayName("sends each proxy the capture settings the resolver has for it, and only that one")
  void sendsPerProxyCaptureSettingsWithStart() throws Exception {
    Map<String, String> bodyByProxy = new ConcurrentHashMap<>();
    HttpServer proxy1 = fakeRemoteProxy("proxy1", bodyByProxy);
    HttpServer proxy2 = fakeRemoteProxy("proxy2", bodyByProxy);
    try {
      String url1 = "http://localhost:" + proxy1.getAddress().getPort();
      String url2 = "http://localhost:" + proxy2.getAddress().getPort();
      coordinator.setCaptureConfigResolver(
          url ->
              url.equals(url1)
                  ? PcapCaptureConfiguration.builder().interfaceNames(List.of("eth0")).build()
                  : null);

      coordinator.startRemoteCapture(Map.of(url1, Duration.ZERO, url2, Duration.ZERO), "scenario");

      assertThat(bodyByProxy.get("proxy1")).contains("\"interfaceNames\":[\"eth0\"]");
      assertThat(bodyByProxy.get("proxy2")).doesNotContain("pcapCapture");
    } finally {
      proxy1.stop(0);
      proxy2.stop(0);
      coordinator.clear();
    }
  }

  @Test
  @DisplayName("a scenario on the suite's thread neither replaces nor stops the gap capture")
  void scenarioOnSameThreadLeavesGapCaptureAlone() throws Exception {
    List<String> stoppedCaptures = new CopyOnWriteArrayList<>();
    HttpServer proxy = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    proxy.createContext(
        "/pcap/admin/start",
        exchange -> {
          String id =
              new String(exchange.getRequestBody().readAllBytes()).contains("\"gap\":true")
                  ? "gap-capture"
                  : "scenario-capture";
          reply(exchange, "{\"capturing\":true,\"captureId\":\"" + id + "\"}");
        });
    proxy.createContext(
        "/pcap/admin/stop",
        exchange -> {
          stoppedCaptures.add(exchange.getRequestURI().getQuery());
          reply(exchange, "{\"capturing\":false,\"filename\":\"f.pcapng\"}");
        });
    proxy.start();
    try {
      String url = "http://localhost:" + proxy.getAddress().getPort();
      Map<String, Duration> proxies = Map.of(url, Duration.ZERO);

      coordinator.startRemoteSuiteCapture(proxies, "suite.pcapng");
      coordinator.startRemoteCapture(proxies, "scenario");
      assertThat(coordinator.stopRemoteCapture()).containsOnlyKeys(url);
      assertThat(coordinator.stopRemoteSuiteCapture()).containsOnlyKeys(url);

      assertThat(stoppedCaptures)
          .containsExactly("captureId=scenario-capture", "captureId=gap-capture");
    } finally {
      proxy.stop(0);
      coordinator.clear();
    }
  }

  private static void reply(com.sun.net.httpserver.HttpExchange exchange, String json)
      throws IOException {
    byte[] response = json.getBytes();
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, response.length);
    exchange.getResponseBody().write(response);
    exchange.close();
  }

  private static HttpServer fakeRemoteProxy(String name, Map<String, String> bodyByProxy)
      throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/pcap/admin/start",
        exchange -> {
          bodyByProxy.put(name, new String(exchange.getRequestBody().readAllBytes()));
          byte[] response = "{\"capturing\":true,\"captureId\":\"s1\"}".getBytes();
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    server.start();
    return server;
  }

  private static void awaitQuietly(CountDownLatch latch) {
    try {
      latch.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
