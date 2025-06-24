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

import de.gematik.test.tiger.common.data.config.tigerproxy.ForwardProxyInfo;
import de.gematik.test.tiger.common.web.InsecureTrustAllManager;
import de.gematik.test.tiger.util.NoProxyUtils;
import java.io.IOException;
import java.net.*;
import java.net.Proxy.Type;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Executor for selecting a route destination from a list of potential destinations. */
@Slf4j
@AllArgsConstructor
public class TigerRouteSelector {
  private final List<String> routeDestinations;
  private final ForwardProxyInfo forwardProxyInfo;

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
      HttpURLConnection connection = openConnection(URI.create(destination).toURL());
      connection.setConnectTimeout(5000);
      connection.setReadTimeout(5000);
      connection.setInstanceFollowRedirects(false);
      connection.setRequestMethod("HEAD");
      InsecureTrustAllManager.allowAllSsl(connection);
      connection.connect();
      var responseCode = connection.getResponseCode();
      log.debug("Got response code {} for destination '{}'", responseCode, destination);
      return responseCode != -1;
    } catch (IOException e) {
      log.warn("Destination '{}' is not reachable:", destination, e);
      return false;
    }
  }

  private HttpURLConnection openConnection(URL destination) throws IOException {
    HttpURLConnection connection;
    if (forwardProxyInfo != null) {
      final InetAddress targetHost = InetAddress.getByName(forwardProxyInfo.getHostname());
      if (NoProxyUtils.shouldUseProxyForHost(targetHost, forwardProxyInfo.getNoProxyHosts())) {
        final InetSocketAddress inetSocketAddress =
            new InetSocketAddress(targetHost, forwardProxyInfo.getPort());
        log.debug(
            "Using forward proxy for host '{}': {}:{}",
            targetHost,
            inetSocketAddress.getHostName(),
            inetSocketAddress.getPort());
        connection =
            (HttpURLConnection) destination.openConnection(new Proxy(Type.HTTP, inetSocketAddress));
      } else {
        log.debug("No forward proxy configured for host '{}', using direct connection", targetHost);
        connection = (HttpURLConnection) destination.openConnection(Proxy.NO_PROXY);
      }
    } else {
      log.debug("No forward proxy configured, using direct connection for {}", destination);
      connection = (HttpURLConnection) destination.openConnection(Proxy.NO_PROXY);
    }
    return connection;
  }
}
