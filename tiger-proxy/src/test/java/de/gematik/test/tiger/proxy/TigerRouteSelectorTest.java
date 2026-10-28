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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.sun.net.httpserver.HttpServer;
import de.gematik.test.tiger.common.data.config.tigerproxy.ForwardProxyInfo;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerConfigurationRoute;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.mockserver.proxyconfiguration.ProxyConfiguration;
import java.net.InetSocketAddress;
import java.util.List;
import lombok.Cleanup;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.TestSocketUtils;

class TigerRouteSelectorTest extends AbstractTigerProxyTest {

  @SneakyThrows
  @ParameterizedTest
  @ValueSource(ints = {403, 502, 503, 504})
  void proxyErrorAnswerMakesTargetUnreachableButDirectTargetStillWorks(int proxyAnswer) {
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          exchange.sendResponseHeaders(proxyAnswer, -1);
          exchange.close();
        });
    server.start();
    try {
      ProxyConfiguration proxy =
          ProxyConfiguration.proxyConfiguration(
              ProxyConfiguration.Type.HTTP,
              new InetSocketAddress("localhost", server.getAddress().getPort()));
      proxy.getNoProxyHosts().add("localhost");
      String direct = "http://localhost:" + server.getAddress().getPort() + "/";

      assertThat(
              new TigerRouteSelector(List.of("http://internal-only.invalid:1/", direct), proxy)
                  .selectFirstReachableDestination())
          .isEqualTo(direct);
    } finally {
      server.stop(0);
    }
  }

  @SneakyThrows
  @ParameterizedTest
  @ValueSource(ints = {403, 502, 503, 504})
  void onlyProxyErrorAnswersMeansNoReachableDestination(int proxyAnswer) {
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          exchange.sendResponseHeaders(proxyAnswer, -1);
          exchange.close();
        });
    server.start();
    try {
      ProxyConfiguration proxy =
          ProxyConfiguration.proxyConfiguration(
              ProxyConfiguration.Type.HTTP,
              new InetSocketAddress("localhost", server.getAddress().getPort()));
      TigerRouteSelector selector =
          new TigerRouteSelector(
              List.of("http://internal-one.invalid:1/", "http://internal-two.invalid:1/"), proxy);

      assertThatExceptionOfType(IllegalStateException.class)
          .isThrownBy(selector::selectFirstReachableDestination)
          .withMessageContaining("No reachable destination");
    } finally {
      server.stop(0);
    }
  }

  @SneakyThrows
  @Test
  void twoDestinationsDirectlyReachable() {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerConfigurationRoute.builder()
                        .from("http://backend")
                        .to(
                            List.of(
                                "http://localhost:" + TestSocketUtils.findAvailableTcpPort(),
                                "http://localhost:" + fakeBackendServerPort))
                        .build()))
            .build());

    val response = proxyRest.get("http://backend/foobar").asString();
    assertThat(response.getStatus()).isEqualTo(666);
  }

  @SneakyThrows
  @Test
  void twoDestinationsWithProxy() {
    @Cleanup
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
                        .from("http://backend")
                        .to(
                            List.of(
                                "http://localhost:" + TestSocketUtils.findAvailableTcpPort(),
                                "http://hostOnlyReachableViaProxy"))
                        .build()))
            .build());

    val response = proxyRest.get("http://backend/foobar").asString();
    assertThat(response.getStatus()).isEqualTo(666);
  }

  @Test
  void twoUnreachableDestinations() {
    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy( // NOSONAR
            () -> {
              spawnTigerProxyWith(
                  TigerProxyConfiguration.builder()
                      .proxyRoutes(
                          List.of(
                              TigerConfigurationRoute.builder()
                                  .from("http://backend")
                                  .to(
                                      List.of(
                                          "http://localhost:"
                                              + TestSocketUtils.findAvailableTcpPort(),
                                          "http://localhost:"
                                              + TestSocketUtils.findAvailableTcpPort()))
                                  .build()))
                      .build());
            })
        .withMessageContaining("No reachable destination found among");
  }

  @Test
  void twoUnreachableDestinationsWithProxy() {
    @Cleanup
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

    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy( // NOSONAR
            () -> {
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
                                  .from("http://backend")
                                  .to(
                                      List.of(
                                          "http://localhost:"
                                              + TestSocketUtils.findAvailableTcpPort(),
                                          "http://localhost:"
                                              + TestSocketUtils.findAvailableTcpPort()))
                                  .build()))
                      .build());
            })
        .withMessageContaining("No reachable destination found among");
  }
}
