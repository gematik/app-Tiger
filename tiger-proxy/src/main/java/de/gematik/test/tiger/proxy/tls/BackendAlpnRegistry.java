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

import com.google.common.net.HostAndPort;
import de.gematik.test.tiger.common.data.config.tigerproxy.AlpnProtocol;
import de.gematik.test.tiger.mockserver.proxyconfiguration.ProxyConfiguration;
import de.gematik.test.tiger.proxy.data.TigerProxyRoute;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * Tracks the ALPN capability of every HTTPS backend the Tiger Proxy is routing to, and resolves a
 * per-connection ALPN list during the TLS handshake.
 *
 * <p>Probes run in the background: {@link #probeBackendAlpnIfHttps} only schedules them and returns
 * immediately, so adding a route (and therefore starting the proxy) never waits for an unreachable
 * backend to time out. The result is awaited lazily, and only for the backends a given TLS
 * handshake actually depends on - see {@link #resolveAlpnForSniHostname}.
 */
@Slf4j
public class BackendAlpnRegistry {

  /**
   * How long a TLS handshake waits for a probe that is still in flight before falling back to the
   * proxy defaults. Probes are normally started at route-registration time and complete long before
   * the first client connects; this is only a safety net so a wedged probe can never stall a
   * handshake indefinitely. Slightly above {@link BackendAlpnProber#PROBE_TIMEOUT_MS} so a probe
   * that is merely hitting its own timeout is still awaited to completion.
   */
  static final Duration PROBE_WAIT_BUDGET =
      Duration.ofMillis(BackendAlpnProber.PROBE_TIMEOUT_MS + 1_000L);

  /**
   * Probe results, keyed by {@code host:port}. An entry is created when the probe is scheduled, so
   * the map doubles as the "already scheduled" guard: every backend is probed exactly once,
   * regardless of how often its route is (re-)added. A completed future holding {@link
   * Optional#empty()} is a cached negative result - a failed probe is never retried.
   */
  private final Map<String, CompletableFuture<Optional<AlpnProtocol>>> backendAlpnResults =
      new ConcurrentHashMap<>();

  /** The owning Tiger Proxy's thread pool - this registry does not manage its lifecycle. */
  private final Executor probeExecutor;

  public BackendAlpnRegistry(Executor probeExecutor) {
    this.probeExecutor = probeExecutor;
  }

  /**
   * Schedules a background ALPN probe for the given backend, if it is an HTTPS backend that has not
   * been probed yet. Returns immediately - it never blocks on the network.
   */
  public void probeBackendAlpnIfHttps(String targetUrl, ProxyConfiguration forwardProxy) {
    try {
      URL url = new URL(targetUrl);
      if (!"https".equalsIgnoreCase(url.getProtocol())) {
        return;
      }
      parseBackendAddress(targetUrl)
          .map(HostAndPort::toString)
          .ifPresent(key -> scheduleProbe(key, url, forwardProxy));
    } catch (MalformedURLException | RuntimeException e) {
      log.debug("Could not probe backend ALPN for {}: {}", targetUrl, e.getMessage());
    }
  }

  private void scheduleProbe(String key, URL url, ProxyConfiguration forwardProxy) {
    backendAlpnResults.computeIfAbsent(
        key,
        backend -> {
          log.debug("Scheduling background ALPN probe for backend {}", backend);
          return supplyProbe(url, forwardProxy)
              .whenComplete(
                  (result, throwable) ->
                      Optional.ofNullable(result)
                          .orElseGet(Optional::empty)
                          .ifPresentOrElse(
                              protocol ->
                                  log.info(
                                      "ALPN probe for backend {}: negotiated '{}'",
                                      backend,
                                      protocol),
                              () ->
                                  log.debug(
                                      "ALPN probe for backend {} yielded no result, connections to"
                                          + " it will use the proxy defaults",
                                      backend)));
        });
  }

  /**
   * {@link BackendAlpnProber#probe} reports every failure as an empty result, so the returned
   * future only ever completes exceptionally on an {@link Error} - which {@link #resultOf} already
   * treats as "nothing known".
   */
  private CompletableFuture<Optional<AlpnProtocol>> supplyProbe(
      URL url, ProxyConfiguration forwardProxy) {
    try {
      return CompletableFuture.supplyAsync(
          () -> BackendAlpnProber.probe(url, forwardProxy), probeExecutor);
    } catch (RejectedExecutionException e) {
      // the proxy's pool is already shut down - behave as if the probe had found nothing
      return CompletableFuture.completedFuture(Optional.empty());
    }
  }

  /**
   * Resolves the ALPN protocols to advertise towards a client that sent the given SNI hostname. An
   * empty list means "no opinion" - the caller then advertises its own defaults.
   *
   * <p>Routes with an explicit {@code alpnProtocols} declaration are answered without touching the
   * probe results at all. Otherwise the returned stage completes once the probes of the matching
   * backends - and only those - have finished, or once {@link #PROBE_WAIT_BUDGET} has elapsed.
   */
  public CompletionStage<List<AlpnProtocol>> resolveAlpnForSniHostname(
      String sniHostname, Collection<TigerProxyRoute> routes) {
    return collectAlpnForMatchingRoutes(routes, routeHostnameMatches(sniHostname))
        .thenCompose(
            byRouteHostname ->
                byRouteHostname.isEmpty()
                    ? collectAlpnForMatchingRoutes(routes, backendHostnameMatches(sniHostname))
                    : CompletableFuture.completedFuture(byRouteHostname))
        .thenApply(resolved -> resolved.isEmpty() ? aggregateCompletedProbes() : resolved);
  }

  private static Predicate<TigerProxyRoute> routeHostnameMatches(String sniHostname) {
    return route ->
        route.getHosts() != null
            && !route.getHosts().isEmpty()
            && route.getHosts().stream().anyMatch(h -> h.equalsIgnoreCase(sniHostname));
  }

  private static Predicate<TigerProxyRoute> backendHostnameMatches(String sniHostname) {
    return route ->
        parseBackendAddress(route.getTo())
            .map(addr -> addr.getHost().equalsIgnoreCase(sniHostname))
            .orElse(false);
  }

  /**
   * Conservative fallback for a client whose SNI hostname matches no route: aggregate over the
   * probes that have <em>already</em> completed. Deliberately does not wait - an unmatched
   * handshake must not block on the probe of some unrelated backend.
   */
  private List<AlpnProtocol> aggregateCompletedProbes() {
    var knownProtocols = completedProbeResults().values();
    if (knownProtocols.isEmpty()) {
      return List.of();
    }
    boolean anyHttp1Only = knownProtocols.stream().anyMatch(p -> p == AlpnProtocol.HTTP_1_1);
    if (anyHttp1Only) {
      return List.of(AlpnProtocol.HTTP_1_1);
    }
    boolean allH2 = knownProtocols.stream().allMatch(p -> p == AlpnProtocol.H2);
    return allH2 ? List.of(AlpnProtocol.H2, AlpnProtocol.HTTP_1_1) : List.of();
  }

  /** Visible for testing - returns a snapshot of the probes that have completed successfully. */
  public Map<String, AlpnProtocol> getProbeResults() {
    return completedProbeResults();
  }

  private Map<String, AlpnProtocol> completedProbeResults() {
    return backendAlpnResults.entrySet().stream()
        .map(entry -> Map.entry(entry.getKey(), resultOf(entry.getValue())))
        .filter(entry -> entry.getValue().isPresent())
        .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().orElseThrow()));
  }

  /**
   * The outcome of a probe, or {@link Optional#empty()} if it is still running or blew up. Never
   * throws - unlike {@link CompletableFuture#getNow}, which rethrows on an exceptionally completed
   * future.
   */
  private static Optional<AlpnProtocol> resultOf(CompletableFuture<Optional<AlpnProtocol>> probe) {
    if (!probe.isDone() || probe.isCompletedExceptionally()) {
      return Optional.empty();
    }
    return probe.getNow(Optional.empty());
  }

  /**
   * Waits for every probe scheduled so far to finish. Intended for tests and for callers that want
   * a deterministic view of the registry; the production TLS path never calls this.
   *
   * @return true if all probes finished within the timeout.
   */
  public boolean awaitAllProbes(Duration timeout) {
    try {
      CompletableFuture.allOf(backendAlpnResults.values().toArray(CompletableFuture[]::new))
          .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
      return true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    } catch (ExecutionException | TimeoutException e) {
      return false;
    }
  }

  private CompletionStage<List<AlpnProtocol>> collectAlpnForMatchingRoutes(
      Collection<TigerProxyRoute> routes, Predicate<TigerProxyRoute> routePredicate) {
    var matchingHttpsRoutes =
        routes.stream()
            .filter(route -> route.getTo() != null && route.getTo().startsWith("https"))
            .filter(routePredicate)
            .toList();

    if (matchingHttpsRoutes.isEmpty()) {
      return CompletableFuture.completedFuture(List.of());
    }

    var explicit = mergeExplicitDeclarations(matchingHttpsRoutes);
    if (!explicit.isEmpty()) {
      return CompletableFuture.completedFuture(explicit);
    }

    // Only here do we actually need a probe result, so only here do we wait for one.
    return awaitProbedProtocols(matchingHttpsRoutes).thenApply(BackendAlpnRegistry::reduceProbed);
  }

  /** The intersection of the explicit declarations, or empty if no matching route declares any. */
  private List<AlpnProtocol> mergeExplicitDeclarations(List<TigerProxyRoute> routes) {
    return routes.stream()
        .map(TigerProxyRoute::getAlpnProtocols)
        .filter(protocols -> protocols != null && !protocols.isEmpty())
        .reduce((a, b) -> a.stream().filter(b::contains).toList())
        .orElseGet(List::of);
  }

  private static List<AlpnProtocol> reduceProbed(List<AlpnProtocol> probed) {
    if (probed.isEmpty()) {
      return List.of();
    }
    boolean allH2 = probed.stream().allMatch(p -> p == AlpnProtocol.H2);
    return allH2 ? List.of(AlpnProtocol.H2, AlpnProtocol.HTTP_1_1) : List.of(AlpnProtocol.HTTP_1_1);
  }

  /**
   * Awaits the probes belonging to the given routes, bounded by {@link #PROBE_WAIT_BUDGET}. Probes
   * that have not finished by then contribute nothing, which lands us in the same fail-open
   * behaviour as a probe that found nothing.
   */
  private CompletionStage<List<AlpnProtocol>> awaitProbedProtocols(List<TigerProxyRoute> routes) {
    var relevantProbes =
        routes.stream()
            .filter(route -> route.getAlpnProtocols() == null || route.getAlpnProtocols().isEmpty())
            .map(route -> parseBackendAddress(route.getTo()))
            .flatMap(Optional::stream)
            .map(HostAndPort::toString)
            .map(backendAlpnResults::get)
            .filter(Objects::nonNull)
            .toList();

    if (relevantProbes.isEmpty()) {
      return CompletableFuture.completedFuture(List.of());
    }
    return CompletableFuture.allOf(relevantProbes.toArray(CompletableFuture[]::new))
        .orTimeout(PROBE_WAIT_BUDGET.toMillis(), TimeUnit.MILLISECONDS)
        .exceptionally(
            throwable -> {
              log.debug(
                  "Not all ALPN probes finished within {}, continuing with the results available"
                      + " so far",
                  PROBE_WAIT_BUDGET);
              return null;
            })
        .thenApply(
            ignored ->
                relevantProbes.stream()
                    .map(BackendAlpnRegistry::resultOf)
                    .flatMap(Optional::stream)
                    .toList());
  }

  static Optional<HostAndPort> parseBackendAddress(String targetUrl) {
    try {
      URI uri = new URI(targetUrl);
      String authority = uri.getAuthority();
      if (authority == null) {
        return Optional.empty();
      }
      HostAndPort hostAndPort = HostAndPort.fromString(authority);
      if (!hostAndPort.hasPort()) {
        int defaultPort = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
        hostAndPort = hostAndPort.withDefaultPort(defaultPort);
      }
      return Optional.of(hostAndPort);
    } catch (URISyntaxException e) {
      return Optional.empty();
    }
  }
}
