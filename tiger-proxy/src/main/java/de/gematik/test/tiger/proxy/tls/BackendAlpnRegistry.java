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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;

/**
 * Tracks the ALPN capability of every HTTPS backend the Tiger Proxy is routing to, and resolves a
 * per-connection ALPN list during the TLS handshake.
 */
@Slf4j
public class BackendAlpnRegistry {

  /** Cached ALPN probe results, keyed by {@code host:port}. */
  private final Map<String, AlpnProtocol> backendAlpnResults = new ConcurrentHashMap<>();

  public void probeBackendAlpnIfHttps(String targetUrl, ProxyConfiguration forwardProxy) {
    try {
      URL url = new URL(targetUrl);
      if (!"https".equalsIgnoreCase(url.getProtocol())) {
        return;
      }
      parseBackendAddress(targetUrl)
          .map(HostAndPort::toString)
          .filter(key -> !backendAlpnResults.containsKey(key))
          .ifPresent(
              key ->
                  BackendAlpnProber.probe(url, forwardProxy)
                      .ifPresent(
                          protocol -> {
                            backendAlpnResults.put(key, protocol);
                            log.info("ALPN probe for backend {}: negotiated '{}'", key, protocol);
                          }));
    } catch (MalformedURLException | RuntimeException e) {
      log.debug("Could not probe backend ALPN for {}: {}", targetUrl, e.getMessage());
    }
  }

  public Optional<List<AlpnProtocol>> resolveAlpnForSniHostname(
      String sniHostname, Collection<TigerProxyRoute> routes) {
    return collectAlpnForMatchingRoutes(routes, routeHostnameMatches(sniHostname))
        .or(() -> collectAlpnForMatchingRoutes(routes, backendHostnameMatches(sniHostname)))
        .or(this::probeAllBackends);
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

  private Optional<List<AlpnProtocol>> probeAllBackends() {
    if (backendAlpnResults.isEmpty()) {
      return Optional.empty();
    }
    boolean anyHttp1Only =
        backendAlpnResults.values().stream().anyMatch(p -> p == AlpnProtocol.HTTP_1_1);
    if (anyHttp1Only) {
      return Optional.of(List.of(AlpnProtocol.HTTP_1_1));
    }
    boolean allH2 = backendAlpnResults.values().stream().allMatch(p -> p == AlpnProtocol.H2);
    return allH2 ? Optional.of(List.of(AlpnProtocol.H2, AlpnProtocol.HTTP_1_1)) : Optional.empty();
  }

  /** Visible for testing — returns an unmodifiable snapshot of the probe-result cache. */
  public Map<String, AlpnProtocol> getProbeResults() {
    return Map.copyOf(backendAlpnResults);
  }

  private Optional<List<AlpnProtocol>> collectAlpnForMatchingRoutes(
      Collection<TigerProxyRoute> routes, Predicate<TigerProxyRoute> routePredicate) {
    var matchingHttpsRoutes =
        routes.stream()
            .filter(route -> route.getTo() != null && route.getTo().startsWith("https"))
            .filter(routePredicate)
            .toList();

    if (matchingHttpsRoutes.isEmpty()) {
      return Optional.empty();
    }

    var explicit = mergeExplicitDeclarations(matchingHttpsRoutes);
    if (explicit.isPresent()) {
      return explicit;
    }

    var probed = lookupProbedProtocols(matchingHttpsRoutes);
    if (probed.isEmpty()) {
      return Optional.empty();
    }
    boolean allH2 = probed.stream().allMatch(p -> p == AlpnProtocol.H2);
    return Optional.of(
        allH2 ? List.of(AlpnProtocol.H2, AlpnProtocol.HTTP_1_1) : List.of(AlpnProtocol.HTTP_1_1));
  }

  private Optional<List<AlpnProtocol>> mergeExplicitDeclarations(List<TigerProxyRoute> routes) {
    return routes.stream()
        .map(TigerProxyRoute::getAlpnProtocols)
        .filter(protocols -> protocols != null && !protocols.isEmpty())
        .reduce((a, b) -> a.stream().filter(b::contains).toList());
  }

  private List<AlpnProtocol> lookupProbedProtocols(List<TigerProxyRoute> routes) {
    return routes.stream()
        .filter(route -> route.getAlpnProtocols() == null || route.getAlpnProtocols().isEmpty())
        .map(route -> parseBackendAddress(route.getTo()))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .map(HostAndPort::toString)
        .map(backendAlpnResults::get)
        .filter(Objects::nonNull)
        .toList();
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
