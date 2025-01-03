/*
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

import static de.gematik.rbellogger.data.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.gematik.test.tiger.common.data.config.tigerproxy.*;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.proxy.exceptions.TigerRoutingErrorFacet;
import kong.unirest.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.http.NoHttpResponseException;
import org.junit.jupiter.api.Test;

@Slf4j
@ResetTigerConfiguration
class TigerProxyExceptionsTest extends AbstractTigerProxyTest {

  @SneakyThrows
  @Test
  void testRoutingExceptionHtmlRendering() {
    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());

    proxyRest.config().automaticRetries(false);
    proxyRest.get("http://backend/foobar").asString();
    assertThatThrownBy(() -> proxyRest.get("http://backend/error").asString())
        .isInstanceOf(UnirestException.class)
        .hasCauseInstanceOf(NoHttpResponseException.class);

    awaitMessagesInTiger(2);
  }

  @SneakyThrows
  @Test
  void forwardProxyConnectionError_shouldKeepRequestInLog() {
    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());

    proxyRest.config().automaticRetries(false);
    assertThatThrownBy(() -> proxyRest.get("http://backend/error").asString())
        .isInstanceOf(UnirestException.class)
        .hasCauseInstanceOf(NoHttpResponseException.class);

    awaitMessagesInTiger(2);

    assertThat(tigerProxy.getRbelMessagesList().get(1))
        .extractChildWithPath("$.sender")
        .hasStringContentEqualTo("backend:80")
        .andTheInitialElement()
        .hasFacet(TigerRoutingErrorFacet.class)
        .extractChildWithPath("$.error.message")
        .asString()
        .contains("Exception during handling of HTTP request: Connection reset");
  }

  @SneakyThrows
  @Test
  void reverseProxyConnectionError_shouldKeepRequestInLog() {
    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());

    val noProxyInstance = Unirest.spawnInstance();
    noProxyInstance.config().automaticRetries(false);
    assertThatThrownBy(
            () ->
                noProxyInstance
                    .get("http://localhost:" + tigerProxy.getProxyPort() + "/error")
                    .asString())
        .isInstanceOf(UnirestException.class)
        .hasCauseInstanceOf(NoHttpResponseException.class);

    awaitMessagesInTiger(2);

    renderTrafficTo("error.html");

    assertThat(tigerProxy.getRbelMessagesList().get(1))
        .extractChildWithPath("$.sender")
        .hasStringContentEqualTo("localhost:" + fakeBackendServerPort)
        .andTheInitialElement()
        .hasFacet(TigerRoutingErrorFacet.class)
        .extractChildWithPath("$.error.message")
        .asString()
        .contains("Exception during handling of HTTP request: Connection reset");
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

    val noProxyInstance = Unirest.spawnInstance();
    noProxyInstance.config().automaticRetries(false);
    noProxyInstance.config().socketTimeout(1000);
    noProxyInstance.config().connectTimeout(1000);
    assertThatThrownBy(
            () ->
                noProxyInstance
                    .get("http://localhost:" + tigerProxy.getProxyPort() + "/error")
                    .asString())
        .isInstanceOf(UnirestException.class);

    awaitMessagesInTiger(2);

    assertThat(tigerProxy.getRbelMessagesList().get(1))
        .extractChildWithPath("$.sender")
        .hasStringContentEqualTo("localhost:" + fakeBackendServerPort)
        .andTheInitialElement()
        .hasFacet(TigerRoutingErrorFacet.class)
        .extractChildWithPath("$.error.message")
        .asString()
        .contains("Exception during handling of HTTP request: Connection reset");
  }
}
