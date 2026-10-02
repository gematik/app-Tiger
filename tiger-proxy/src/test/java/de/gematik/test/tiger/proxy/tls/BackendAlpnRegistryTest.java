/*
 *
 * Copyright 2021-2025 gematik GmbH
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
package de.gematik.test.tiger.proxy.tls;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.test.tiger.common.data.config.tigerproxy.AlpnProtocol;
import de.gematik.test.tiger.proxy.H1TlsServer;
import de.gematik.test.tiger.proxy.H2TestServer;
import de.gematik.test.tiger.proxy.data.TigerProxyRoute;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link BackendAlpnRegistry}, in particular that probing happens in the background and
 * is only awaited when a TLS handshake actually needs the result.
 */
class BackendAlpnRegistryTest {

  /** A backend in TEST-NET-1 (RFC 5737): guaranteed never to answer. */
  private static final String BLACKHOLE_BACKEND = "https://192.0.2.1:443";

  private final CountDownLatch gate = new CountDownLatch(1);
  private final AtomicInteger submissions = new AtomicInteger();
  private final ExecutorService delegate = Executors.newCachedThreadPool();

  @AfterEach
  void releaseProbes() {
    gate.countDown();
    delegate.shutdownNow();
  }

  @SneakyThrows
  @Test
  void schedulingAProbe_shouldNotWaitForIt() {
    var registry = new BackendAlpnRegistry(gatedExecutor());

    // the executor is gated, so the probe cannot possibly have run yet
    registry.probeBackendAlpnIfHttps(BLACKHOLE_BACKEND, null);

    assertThat(registry.getProbeResults()).isEmpty();
    assertThat(resolveFor(registry, BLACKHOLE_BACKEND))
        .as("resolution must stay pending instead of blocking on the probe")
        .isNotDone();
  }

  @SneakyThrows
  @Test
  void probeStillRunning_resolutionShouldCompleteOnceTheProbeFinishes() {
    var registry = new BackendAlpnRegistry(gatedExecutor());
    try (H1TlsServer h1Backend = new H1TlsServer(0)) {
      h1Backend.start();
      var backend = "https://localhost:" + h1Backend.getPort();

      registry.probeBackendAlpnIfHttps(backend, null);
      var resolution =
          registry
              .resolveAlpnForSniHostname("localhost", List.of(routeTo(backend)))
              .toCompletableFuture();
      assertThat(resolution).isNotDone();

      gate.countDown();

      assertThat(resolution.get(10, TimeUnit.SECONDS)).containsExactly(AlpnProtocol.HTTP_1_1);
    }
  }

  @SneakyThrows
  @Test
  void failedProbe_shouldBeCachedAndNotRetried() {
    var registry = new BackendAlpnRegistry(countingExecutor());

    registry.probeBackendAlpnIfHttps(BLACKHOLE_BACKEND, null);
    registry.probeBackendAlpnIfHttps(BLACKHOLE_BACKEND, null);
    registry.probeBackendAlpnIfHttps(BLACKHOLE_BACKEND, null);

    assertThat(registry.awaitAllProbes(Duration.ofSeconds(30))).isTrue();
    assertThat(submissions)
        .as("a backend must be probed exactly once, successful or not")
        .hasValue(1);

    // re-adding the route after the failure still does not trigger a new probe
    registry.probeBackendAlpnIfHttps(BLACKHOLE_BACKEND, null);
    assertThat(submissions).hasValue(1);
  }

  @SneakyThrows
  @Test
  void unreachableBackend_resolutionShouldFallBackToDefaults() {
    var registry = new BackendAlpnRegistry(countingExecutor());
    registry.probeBackendAlpnIfHttps(BLACKHOLE_BACKEND, null);

    var resolved = resolveFor(registry, BLACKHOLE_BACKEND).get(30, TimeUnit.SECONDS);

    assertThat(resolved).as("no probe result means: let the caller use its defaults").isEmpty();
  }

  @SneakyThrows
  @Test
  void explicitAlpnDeclaration_shouldNotWaitForAnyProbe() {
    var registry = new BackendAlpnRegistry(gatedExecutor());
    var route =
        TigerProxyRoute.builder()
            .from("/")
            .to(BLACKHOLE_BACKEND)
            .alpnProtocols(List.of(AlpnProtocol.HTTP_1_1))
            .build();
    registry.probeBackendAlpnIfHttps(BLACKHOLE_BACKEND, null);

    var resolution =
        registry.resolveAlpnForSniHostname("192.0.2.1", List.of(route)).toCompletableFuture();

    assertThat(resolution).isDone();
    assertThat(resolution.get()).containsExactly(AlpnProtocol.HTTP_1_1);
  }

  @SneakyThrows
  @Test
  void sniMatchingNoRoute_shouldFallBackToAggregateOfProbedBackends() {
    var registry = new BackendAlpnRegistry(countingExecutor());
    try (H1TlsServer h1Backend = new H1TlsServer(0)) {
      h1Backend.start();
      var backend = "https://localhost:" + h1Backend.getPort();
      registry.probeBackendAlpnIfHttps(backend, null);
      assertThat(registry.awaitAllProbes(Duration.ofSeconds(30))).isTrue();

      var resolved =
          registry
              .resolveAlpnForSniHostname("unrelated.example", List.of(routeTo(backend)))
              .toCompletableFuture()
              .get(10, TimeUnit.SECONDS);

      assertThat(resolved)
          .as("an h1-only backend anywhere forces the conservative answer")
          .containsExactly(AlpnProtocol.HTTP_1_1);
    }
  }

  @SneakyThrows
  @Test
  void sniMatchingNoRoute_allBackendsH2_shouldOfferH2() {
    var registry = new BackendAlpnRegistry(countingExecutor());
    try (H2TestServer h2Backend = H2TestServer.h2Tls(0)) {
      h2Backend.start();
      var backend = "https://localhost:" + h2Backend.getPort();
      registry.probeBackendAlpnIfHttps(backend, null);
      assertThat(registry.awaitAllProbes(Duration.ofSeconds(30))).isTrue();

      var resolved =
          registry
              .resolveAlpnForSniHostname("unrelated.example", List.of(routeTo(backend)))
              .toCompletableFuture()
              .get(10, TimeUnit.SECONDS);

      assertThat(resolved).containsExactly(AlpnProtocol.H2, AlpnProtocol.HTTP_1_1);
    }
  }

  @SneakyThrows
  @Test
  void routeWhoseBackendWasNeverProbed_shouldResolveToNoOpinion() {
    var registry = new BackendAlpnRegistry(countingExecutor());

    // no probeBackendAlpnIfHttps call at all - the route matches, but nothing is known
    var resolved = resolveFor(registry, BLACKHOLE_BACKEND).get(10, TimeUnit.SECONDS);

    assertThat(resolved).isEmpty();
    assertThat(submissions).hasValue(0);
  }

  @Test
  void awaitAllProbes_shouldReportFalseWhenInterrupted() {
    var registry = new BackendAlpnRegistry(gatedExecutor());
    registry.probeBackendAlpnIfHttps(BLACKHOLE_BACKEND, null);

    Thread.currentThread().interrupt();
    try {
      assertThat(registry.awaitAllProbes(Duration.ofSeconds(30))).isFalse();
      assertThat(Thread.currentThread().isInterrupted())
          .as("the interrupt must be restored for the caller")
          .isTrue();
    } finally {
      Thread.interrupted(); // clear, so the flag does not leak into other tests
    }
  }

  @Test
  void awaitAllProbes_shouldReportFalseWhenProbesAreStillRunning() {
    var registry = new BackendAlpnRegistry(gatedExecutor());
    registry.probeBackendAlpnIfHttps(BLACKHOLE_BACKEND, null);

    assertThat(registry.awaitAllProbes(Duration.ofMillis(200))).isFalse();
  }

  @SneakyThrows
  @Test
  void poolAlreadyShutDown_shouldBeTreatedAsNoProbeResult() {
    var registry = new BackendAlpnRegistry(rejectingExecutor());

    registry.probeBackendAlpnIfHttps(BLACKHOLE_BACKEND, null);

    assertThat(registry.getProbeResults()).isEmpty();
    assertThat(resolveFor(registry, BLACKHOLE_BACKEND).get(10, TimeUnit.SECONDS)).isEmpty();
  }

  private static java.util.concurrent.CompletableFuture<List<AlpnProtocol>> resolveFor(
      BackendAlpnRegistry registry, String backend) {
    return registry
        .resolveAlpnForSniHostname("192.0.2.1", List.of(routeTo(backend)))
        .toCompletableFuture();
  }

  private static TigerProxyRoute routeTo(String backend) {
    return TigerProxyRoute.builder().from("/").to(backend).build();
  }

  /** Probes only start running once {@link #gate} is released. */
  private Executor gatedExecutor() {
    return command ->
        countingExecutor()
            .execute(
                () -> {
                  try {
                    gate.await();
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                  }
                  command.run();
                });
  }

  /** Stands in for a pool that has already been shut down. */
  private static Executor rejectingExecutor() {
    return command -> {
      throw new RejectedExecutionException("pool is shut down");
    };
  }

  /** Counts how many probes were handed to the pool. */
  private Executor countingExecutor() {
    return command -> {
      submissions.incrementAndGet();
      delegate.execute(command);
    };
  }
}
