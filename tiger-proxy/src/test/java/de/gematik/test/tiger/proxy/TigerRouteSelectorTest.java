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

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.test.tiger.common.data.config.tigerproxy.ForwardProxyInfo;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerConfigurationRoute;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import java.util.List;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.TestSocketUtils;

class TigerRouteSelectorTest extends AbstractTigerProxyTest {

  @SneakyThrows
  @Test
  void twoDestinationsDirectlyReachable() {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerConfigurationRoute.builder()
                        .from("/")
                        .to(
                            List.of(
                                "http://localhost:" + TestSocketUtils.findAvailableTcpPort(),
                                "http://localhost:" + fakeBackendServerPort))
                        .build()))
            .build());

    val response =
        proxyRest.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar").asString();
    assertThat(response.getStatus()).isEqualTo(666);
  }

  @SneakyThrows
  @Test
  void twoDestinationsWithProxy() {
    final TigerProxy forwardProxy =
        new TigerProxy(
            TigerProxyConfiguration.builder()
                .proxyPort(TestSocketUtils.findAvailableTcpPort())
                .adminPort(TestSocketUtils.findAvailableTcpPort())
                .proxyRoutes(
                    List.of(
                        TigerConfigurationRoute.builder()
                            .from("http://hostOnlyReachableViaProxy")
                            .to("http://localhost:" + fakeBackendServerPort)
                            .build(),
                        TigerConfigurationRoute.builder()
                            .from("/")
                            .to("http://localhost:" + fakeBackendServerPort)
                            .build()))
                .build());

    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .forwardToProxy(
                ForwardProxyInfo.builder()
                    .hostname("localhost")
                    .port(forwardProxy.getProxyPort())
                    .build())
            .proxyRoutes(
                List.of(
                    TigerConfigurationRoute.builder()
                        .from("/")
                        .to(
                            List.of(
                                "http://localhost:" + TestSocketUtils.findAvailableTcpPort(),
                                "http://hostOnlyReachableViaProxy"))
                        .build()))
            .build());

    val response =
        proxyRest.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar").asString();
    assertThat(response.getStatus()).isEqualTo(666);
  }
}
