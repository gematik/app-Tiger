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

import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.data.TigerProxyRoute;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProxyPortDiscovery {

  private static final int HTTP_DEFAULT_PORT = 80;
  private static final int HTTPS_DEFAULT_PORT = 443;

  private ProxyPortDiscovery() {}

  public static Set<Integer> discoverPorts(TigerProxy proxy) {
    return discoverPorts(proxy, true);
  }

  public static Set<Integer> discoverPorts(TigerProxy proxy, boolean includeAdminPort) {
    Set<Integer> ports = new HashSet<>();

    if (proxy == null) {
      return ports;
    }

    try {
      ports.add(proxy.getProxyPort());
      if (includeAdminPort) {
        ports.add(proxy.getAdminPort());
      }
      addRouteTargetPorts(proxy, ports);
    } catch (RuntimeException e) {
      log.warn("Failed to discover proxy ports: {}", e.getMessage());
      log.debug("Port discovery error:", e);
    }

    return ports.stream().filter(p -> p > 0).collect(Collectors.toSet());
  }

  public static int portOf(String uri) {
    try {
      URI parsed = new URI(uri);
      if (parsed.getPort() > 0) {
        return parsed.getPort();
      }
      return defaultPortOf(parsed.getScheme());
    } catch (URISyntaxException e) {
      log.debug("Could not extract port from {}", uri, e);
      return -1;
    }
  }

  private static int defaultPortOf(String scheme) {
    if (scheme == null) {
      return -1;
    }
    return switch (scheme.toLowerCase(Locale.ROOT)) {
      case "http", "ws" -> HTTP_DEFAULT_PORT;
      case "https", "wss" -> HTTPS_DEFAULT_PORT;
      default -> -1;
    };
  }

  private static void addRouteTargetPorts(TigerProxy proxy, Set<Integer> ports) {
    List<TigerProxyRoute> routes = proxy.getRoutes();
    if (routes != null) {
      routes.stream()
          .map(TigerProxyRoute::getTo)
          .filter(Objects::nonNull)
          .forEach(target -> ports.add(portOf(target)));
    }
  }
}
