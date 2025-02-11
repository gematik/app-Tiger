/*
 *
 * Copyright 2024 gematik GmbH
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
      HttpURLConnection connection = (HttpURLConnection) new URL(destination).openConnection();
      if (forwardProxyInfo != null) {
        final InetAddress targetHost = InetAddress.getByName(forwardProxyInfo.getHostname());
        if (NoProxyUtils.shouldUseProxyForHost(targetHost, forwardProxyInfo.getNoProxyHosts())) {
          final InetSocketAddress inetSocketAddress =
              new InetSocketAddress(targetHost, forwardProxyInfo.getPort());
          connection =
              (HttpURLConnection)
                  new URL(destination).openConnection(new Proxy(Type.HTTP, inetSocketAddress));
        }
      }
      connection.setConnectTimeout(5000);
      connection.setReadTimeout(5000);
      connection.setInstanceFollowRedirects(false);
      connection.setRequestMethod("HEAD");
      InsecureTrustAllManager.allowAllSsl(connection);
      connection.connect();
      var responseCode = connection.getResponseCode();
      return responseCode != -1;
    } catch (IOException e) {
      return false;
    }
  }
}
