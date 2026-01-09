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

import static de.gematik.rbellogger.data.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.gematik.test.tiger.common.data.config.tigerproxy.*;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.proxy.exceptions.TigerRoutingErrorFacet;
import java.io.IOException;
import java.net.http.HttpClient;
import kong.unirest.core.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.Test;

@Slf4j
@ResetTigerConfiguration
class TigerProxyExceptionsTest extends AbstractTigerProxyTest {

  @SneakyThrows
  @Test
  void testRoutingExceptionHtmlRendering() {
    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());

    proxyRest.config().retryAfter(false);
    proxyRest.get("http://backend/foobar").asString();
    assertThatThrownBy(() -> proxyRest.get("http://backend/error").asString())
        .isInstanceOf(UnirestException.class)
        .hasCauseInstanceOf(IOException.class);

    awaitMessagesInTigerProxy(2);
  }

  @SneakyThrows
  @Test
  void forwardProxyConnectionError_shouldKeepRequestInLog() {
    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());

    proxyRest.config().retryAfter(false);
    assertThatThrownBy(() -> proxyRest.get("http://backend/error").asString())
        .isInstanceOf(UnirestException.class)
        .hasCauseInstanceOf(IOException.class);

    awaitMessagesInTigerProxy(2);

    assertThat(tigerProxy.getRbelMessagesList().get(1))
        .extractChildWithPath("$.sender")
        .hasStringContentEqualTo("backend:80")
        .andTheInitialElement()
        .hasFacet(TigerRoutingErrorFacet.class)
        .extractChildWithPath("$.error.message")
        .asString()
        .contains("Exception during handling of HTTP request: SocketException: Connection reset");
  }

  @SneakyThrows
  @Test
  void reverseProxyConnectionError_shouldKeepRequestInLog() {
    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());

    val noProxyInstance = Unirest.spawnInstance();
    noProxyInstance.config().retryAfter(false);
    assertThatThrownBy(
            () ->
                noProxyInstance
                    .get("http://localhost:" + tigerProxy.getProxyPort() + "/error")
                    .asString())
        .isInstanceOf(UnirestException.class)
        .hasCauseInstanceOf(IOException.class);

    awaitMessagesInTigerProxy(2);

    renderTrafficTo("error.html");

    assertThat(tigerProxy.getRbelMessagesList().get(1))
        .extractChildWithPath("$.sender")
        .hasStringContentEqualTo("localhost:" + fakeBackendServerPort)
        .andTheInitialElement()
        .hasFacet(TigerRoutingErrorFacet.class)
        .extractChildWithPath("$.error.message")
        .asString()
        .contains("Exception during handling of HTTP request: SocketException: Connection reset");
  }

  @SneakyThrows
  @Test
  void directForwardConnectionError_shouldKeepRequestInLog() {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .directReverseProxy(
                DirectReverseProxyInfo.builder()
                    .hostname("localhost")
                    .port(fakeBackendServerPort)
                    .build())
            .build());

    try (val noProxyInstance = Unirest.spawnInstance()) {
      noProxyInstance.config().retryAfter(false);
      noProxyInstance.config().version(HttpClient.Version.HTTP_1_1);
      noProxyInstance.config().connectTimeout(1000);
      noProxyInstance.config().requestTimeout(1000);
      assertThatThrownBy(
              () ->
                  noProxyInstance
                      .get("http://localhost:" + tigerProxy.getProxyPort() + "/error")
                      .asString())
          .isInstanceOf(UnirestException.class);

      awaitMessagesInTigerProxy(2);

      final String localhostRegex = "(view-|)localhost:" + fakeBackendServerPort;
      assertThat(tigerProxy.getRbelMessagesList().get(1))
          .extractChildWithPath("$.sender")
          .matches(
              el -> el.getRawStringContent().matches(localhostRegex),
              "sender matches '" + localhostRegex + "'")
          .andTheInitialElement()
          .hasFacet(TigerRoutingErrorFacet.class)
          .extractChildWithPath("$.error.message")
          .asString()
          .contains("Exception during handling of HTTP request: Connection reset");
    }
  }

  @SneakyThrows
  @Test
  void unknownHost_shouldGiveErrorDetails() {
    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());

    proxyRest.config().retryAfter(false);
    assertThatThrownBy(() -> proxyRest.get("http://xyzDoesNotExistXyz").asString())
        .isInstanceOf(UnirestException.class)
        .hasCauseInstanceOf(IOException.class);

    awaitMessagesInTigerProxy(2);
  }
}
