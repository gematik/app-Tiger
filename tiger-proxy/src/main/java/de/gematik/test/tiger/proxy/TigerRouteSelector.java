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
package de.gematik.test.tiger.proxy;

import de.gematik.test.tiger.common.web.InsecureTrustAllManager;
import de.gematik.test.tiger.mockserver.proxyconfiguration.ProxyConfiguration;
import de.gematik.test.tiger.util.NoProxyUtils;
import java.io.IOException;
import java.net.*;
import java.net.Proxy.Type;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Executor for selecting a route destination from a list of potential destinations. */
@Slf4j
@AllArgsConstructor
public class TigerRouteSelector {
  /** Answers the forward proxy sends when it cannot reach the target itself. */
  private static final Set<Integer> PROXY_FAILURE_CODES = Set.of(403, 502, 503, 504);

  private final List<String> routeDestinations;
  private final ProxyConfiguration forwardProxy;

  public String selectFirstReachableDestination() {
    if (routeDestinations.isEmpty()) {
      throw new IllegalStateException("No route destinations provided");
    }
    if (routeDestinations.size() == 1) {
      return routeDestinations.get(0);
    }
    return routeDestinations.parallelStream()
        .filter(this::isReachable)
        .findAny()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "No reachable destination found among " + routeDestinations));
  }

  private boolean isReachable(String destination) {
    try {
      log.debug("Checking if destination '{}' is reachable", destination);
      URL url = URI.create(destination).toURL();
      boolean viaProxy = shouldUseForwardProxy(url);
      HttpURLConnection connection = openConnection(url, viaProxy);
      connection.setConnectTimeout(5000);
      connection.setReadTimeout(5000);
      connection.setInstanceFollowRedirects(false);
      connection.setRequestMethod("HEAD");
      InsecureTrustAllManager.allowAllSsl(connection);
      connection.connect();
      var responseCode = connection.getResponseCode();
      log.debug("Got response code {} for destination '{}'", responseCode, destination);
      if (viaProxy && PROXY_FAILURE_CODES.contains(responseCode)) {
        log.warn(
            "Destination '{}' is not reachable, forward proxy answered {}",
            destination,
            responseCode);
        return false;
      }
      return responseCode != -1;
    } catch (IOException e) {
      log.warn("Destination '{}' is not reachable:", destination, e);
      return false;
    }
  }

  private boolean shouldUseForwardProxy(URL destination) {
    if (forwardProxy == null) {
      return false;
    }
    final String host = destination.getHost();
    final InetAddress resolved = NoProxyUtils.resolveIfNeeded(host, forwardProxy.getNoProxyHosts());
    final Optional<String> bypassEntry =
        NoProxyUtils.findBypassingNoProxyEntry(host, resolved, forwardProxy.getNoProxyHosts());
    if (bypassEntry.isPresent()) {
      log.debug("direct (matched {} from no_proxy) for '{}'", bypassEntry.get(), destination);
      return false;
    }
    log.debug("via proxy for '{}'", destination);
    return true;
  }

  private HttpURLConnection openConnection(URL destination, boolean viaProxy) throws IOException {
    if (viaProxy) {
      return (HttpURLConnection)
          destination.openConnection(new Proxy(Type.HTTP, forwardProxy.getProxyAddress()));
    }
    return (HttpURLConnection) destination.openConnection(Proxy.NO_PROXY);
  }
}
