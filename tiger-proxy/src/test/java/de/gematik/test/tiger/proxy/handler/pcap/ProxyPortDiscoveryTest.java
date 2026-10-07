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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.data.TigerProxyRoute;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ProxyPortDiscovery")
class ProxyPortDiscoveryTest {

  @Test
  @DisplayName("an explicit port is the port")
  void explicitPortIsUsed() {
    assertThat(ProxyPortDiscovery.portOf("http://backend:3000/api")).isEqualTo(3000);
    assertThat(ProxyPortDiscovery.portOf("https://backend:8443")).isEqualTo(8443);
  }

  @Test
  @DisplayName("without a port, the scheme's default is the port")
  void defaultPortOfTheScheme() {
    assertThat(ProxyPortDiscovery.portOf("http://backend")).isEqualTo(80);
    assertThat(ProxyPortDiscovery.portOf("https://backend/path")).isEqualTo(443);
    assertThat(ProxyPortDiscovery.portOf("ws://backend")).isEqualTo(80);
    assertThat(ProxyPortDiscovery.portOf("wss://backend")).isEqualTo(443);
    assertThat(ProxyPortDiscovery.portOf("HTTPS://backend")).as("scheme case").isEqualTo(443);
  }

  @Test
  @DisplayName("no port at all for an unknown scheme or something that is not a URI")
  void noPortWhenThereIsNone() {
    assertThat(ProxyPortDiscovery.portOf("ftp://backend")).isEqualTo(-1);
    assertThat(ProxyPortDiscovery.portOf("no scheme or port")).isEqualTo(-1);
    assertThat(ProxyPortDiscovery.portOf("backend")).isEqualTo(-1);
  }

  @Test
  @DisplayName("the proxy's own ports and every route target's port are discovered")
  void discoversProxyAdminAndRoutePorts() {
    TigerProxy proxy = proxyWithRoutes("https://secure-backend", "http://localhost:3000", null);

    assertThat(ProxyPortDiscovery.discoverPorts(proxy))
        .containsExactlyInAnyOrder(8080, 9000, 443, 3000);
  }

  @Test
  @DisplayName("the admin port can be left out, and only it")
  void adminPortCanBeLeftOut() {
    TigerProxy proxy = proxyWithRoutes("https://secure-backend", "http://localhost:3000");

    assertThat(ProxyPortDiscovery.discoverPorts(proxy, false))
        .containsExactlyInAnyOrder(8080, 443, 3000);
  }

  @Test
  @DisplayName("no proxy, no ports")
  void noProxyNoPorts() {
    assertThat(ProxyPortDiscovery.discoverPorts(null)).isEmpty();
    assertThat(ProxyPortDiscovery.discoverPorts(null, false)).isEmpty();
  }

  private static TigerProxy proxyWithRoutes(String... targets) {
    TigerProxy proxy = mock(TigerProxy.class);
    when(proxy.getProxyPort()).thenReturn(8080);
    when(proxy.getAdminPort()).thenReturn(9000);
    List<TigerProxyRoute> routes =
        java.util.Arrays.stream(targets)
            .map(target -> TigerProxyRoute.builder().to(target).build())
            .toList();
    when(proxy.getRoutes()).thenReturn(routes);
    return proxy;
  }
}
