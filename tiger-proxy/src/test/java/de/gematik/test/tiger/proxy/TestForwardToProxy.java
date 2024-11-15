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
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerConfigurationRoute;
import java.util.List;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.TestSocketUtils;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;

@Slf4j
class TestForwardToProxy extends AbstractTigerProxyTest {

  private final int freePort = TestSocketUtils.findAvailableTcpPort();
  
  @SneakyThrows
  @Test
  void checkNoProxyHosts() {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerConfigurationRoute.builder()
                        .from("http://hostWithoutProxying")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .forwardToProxy(
                ForwardProxyInfo.builder()
                    .port(freePort)
                    .hostname("localhost")
                    .noProxyHosts(List.of("localhost"))
                    .build())
            .build());

    final HttpResponse<JsonNode> response = proxyRest.get("http://hostWithoutProxying/ok").asJson();

    assertThat(response.getStatus()).isEqualTo(200);
  }

  @SneakyThrows
  @Test
  void checkNoProxyHostsFromSystem() {
    new EnvironmentVariables("http_proxy", "http://localhost:" + freePort)
      .and("no_proxy", "localhost")
        .execute(
            () -> {
              spawnTigerProxyWith(
                  TigerProxyConfiguration.builder()
                      .proxyRoutes(
                          List.of(
                              TigerConfigurationRoute.builder()
                                  .from("http://hostWithoutProxying")
                                  .to("http://localhost:" + fakeBackendServerPort)
                                  .build()))
                      .forwardToProxy(ForwardProxyInfo.builder().hostname("$SYSTEM").build())
                      .build());

              final HttpResponse<JsonNode> response =
                  proxyRest.get("http://hostWithoutProxying/ok").asJson();

              assertThat(response.getStatus()).isEqualTo(200);
            });
  }
}
