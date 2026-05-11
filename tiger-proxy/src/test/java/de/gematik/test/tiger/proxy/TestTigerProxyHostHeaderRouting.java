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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.gematik.test.tiger.common.data.config.tigerproxy.TigerConfigurationRoute;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import java.util.List;
import kong.unirest.core.UnirestException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@Slf4j
@TestInstance(Lifecycle.PER_CLASS)
@ResetTigerConfiguration
class TestTigerProxyHostHeaderRouting extends AbstractTigerProxyTest {

  @Test
  void unmatchedReverseProxyRequest_withHonorHostHeaderRoutingEnabled_shouldForwardViaHostHeader() {
    spawnTigerProxyWith(TigerProxyConfiguration.builder().honorHostHeaderRouting(true).build());

    var response =
        unirestInstance
            .get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar")
            .header("Host", "localhost:" + fakeBackendServerPort)
            .asString();

    assertThat(response.getStatus()).isEqualTo(666);
    awaitMessagesInTigerProxy(2);
  }

  @Test
  void unmatchedReverseProxyRequest_withHonorHostHeaderRoutingDisabled_shouldReturnError() {
    spawnTigerProxyWith(TigerProxyConfiguration.builder().honorHostHeaderRouting(false).build());

    assertThatThrownBy(
            () ->
                unirestInstance
                    .get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar")
                    .header("Host", "localhost:" + fakeBackendServerPort)
                    .asString())
        .isInstanceOf(UnirestException.class);
  }

  @Test
  void matchedRoute_shouldStillBeUsedEvenWhenHostHeaderRoutingEnabled() {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .honorHostHeaderRouting(true)
            .proxyRoutes(
                List.of(
                    TigerConfigurationRoute.builder()
                        .from("/")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());

    var response =
        unirestInstance.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar").asString();

    assertThat(response.getStatus()).isEqualTo(666);
    awaitMessagesInTigerProxy(2);
  }

  @Test
  void selfReferencingHostHeader_shouldCloseConnection() {
    spawnTigerProxyWith(TigerProxyConfiguration.builder().honorHostHeaderRouting(true).build());

    assertThatThrownBy(
            () ->
                unirestInstance
                    .get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar")
                    .header("Host", "localhost:" + tigerProxy.getProxyPort())
                    .asString())
        .isInstanceOf(UnirestException.class);
  }

  @Test
  void selfReferencingHostHeader127_shouldCloseConnection() {
    spawnTigerProxyWith(TigerProxyConfiguration.builder().honorHostHeaderRouting(true).build());

    assertThatThrownBy(
            () ->
                unirestInstance
                    .get("http://127.0.0.1:" + tigerProxy.getProxyPort() + "/foobar")
                    .header("Host", "127.0.0.1:" + tigerProxy.getProxyPort())
                    .asString())
        .isInstanceOf(UnirestException.class);
  }
}
