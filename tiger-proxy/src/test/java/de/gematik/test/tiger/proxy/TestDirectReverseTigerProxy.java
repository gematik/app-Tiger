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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import de.gematik.rbellogger.data.RbelElementAssertion;
import de.gematik.rbellogger.data.facet.RbelHttpRequestFacet;
import de.gematik.rbellogger.data.facet.RbelMessageTimingFacet;
import de.gematik.test.tiger.common.data.config.tigerproxy.*;
import de.gematik.test.tiger.common.pki.TigerConfigurationPkiIdentity;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.http.HttpClient;
import java.time.ZonedDateTime;
import java.util.Optional;
import javax.net.ssl.SSLContext;
import kong.unirest.core.Unirest;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@Slf4j
@TestInstance(Lifecycle.PER_CLASS)
@ResetTigerConfiguration
class TestDirectReverseTigerProxy extends AbstractTigerProxyTest {

  @Test
  void directForward_shouldForwardBinaryContent() throws IOException {
    try (ServerSocket backendServer = new ServerSocket(0)) {
      spawnTigerProxyWithDefaultRoutesAndWith(
          TigerProxyConfiguration.builder()
              .directReverseProxy(
                  DirectReverseProxyInfo.builder()
                      .hostname("localhost")
                      .port(backendServer.getLocalPort())
                      .build())
              .build());
      log.info("Backendserver running on port {}", backendServer.getLocalPort());
      try (Socket clientSocket = new Socket("localhost", tigerProxy.getProxyPort())) {
        final byte[] requestPayload = "{'msg':'Hallo Welt!'}".getBytes(UTF_8);
        final byte[] responsePayload = "{'msg':'Response String'}".getBytes(UTF_8);

        ZonedDateTime beforeRequest = ZonedDateTime.now();
        clientSocket.getOutputStream().write(ArrayUtils.subarray(requestPayload, 0, 10));
        clientSocket.getOutputStream().flush();
        clientSocket
            .getOutputStream()
            .write(ArrayUtils.subarray(requestPayload, 10, requestPayload.length));

        final Socket serverSocket = backendServer.accept();
        assertThat(serverSocket.getInputStream().readNBytes(requestPayload.length))
            .isEqualTo(requestPayload);

        serverSocket.getOutputStream().write(responsePayload);

        assertThat(clientSocket.getInputStream().readNBytes(responsePayload.length))
            .isEqualTo(responsePayload);
        ZonedDateTime afterRespone = ZonedDateTime.now();

        await().until(() -> tigerProxy.getRbelMessages().size() >= 2);

        // check content
        assertThat(tigerProxy.getRbelMessagesList().get(0).getRawContent())
            .isEqualTo(requestPayload);
        assertThat(tigerProxy.getRbelMessagesList().get(1).getRawContent())
            .isEqualTo(responsePayload);

        // check request adresses
        assertThat(
                tigerProxy
                    .getRbelMessagesList()
                    .get(0)
                    .findElement("$.receiver.port")
                    .get()
                    .getRawStringContent())
            .isEqualTo("" + serverSocket.getLocalPort());
        assertThat(
                tigerProxy
                    .getRbelMessagesList()
                    .get(0)
                    .findElement("$.sender.port")
                    .get()
                    .getRawStringContent())
            .isEqualTo("" + clientSocket.getLocalPort());

        // check response adresses
        assertThat(
                tigerProxy
                    .getRbelMessagesList()
                    .get(1)
                    .findElement("$.sender.port")
                    .get()
                    .getRawStringContent())
            .isEqualTo("" + serverSocket.getLocalPort());
        assertThat(
                tigerProxy
                    .getRbelMessagesList()
                    .get(1)
                    .findElement("$.receiver.port")
                    .get()
                    .getRawStringContent())
            .isEqualTo("" + clientSocket.getLocalPort());

        // check timing
        final ZonedDateTime requestTime =
            tigerProxy
                .getRbelMessagesList()
                .get(0)
                .getFacetOrFail(RbelMessageTimingFacet.class)
                .getTransmissionTime();
        final ZonedDateTime responseTime =
            tigerProxy
                .getRbelMessagesList()
                .get(0)
                .getFacetOrFail(RbelMessageTimingFacet.class)
                .getTransmissionTime();

        assertThat(requestTime)
            .isAfterOrEqualTo(beforeRequest)
            .isBeforeOrEqualTo(responseTime)
            .isBeforeOrEqualTo(afterRespone);

        assertThat(responseTime)
            .isAfter(beforeRequest)
            .isAfterOrEqualTo(responseTime)
            .isBeforeOrEqualTo(afterRespone);
      }
    }
  }

  @Test
  void directForward_shouldForwardHttpContent() {
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
            .directReverseProxy(
                DirectReverseProxyInfo.builder()
                    .hostname("localhost")
                    .port(fakeBackendServerPort)
                    .build())
            .build());

    try (var unirestInstance = Unirest.spawnInstance()) {
      unirestInstance.config().version(HttpClient.Version.HTTP_1_1);

      // no proxyRest, direct connection (assume reverseProxy behavior)
      unirestInstance.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar").asString();
      unirestInstance.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar").asString();

      awaitMessagesInTiger(4);

      val messages = tigerProxy.getRbelMessagesList();

      val request = messages.get(0);
      val response = messages.get(1);
      val request2 = messages.get(2);
      val response2 = messages.get(3);

      RbelElementAssertion.assertThat(request)
          .extractChildWithPath("$.path")
          .hasStringContentEqualTo("/foobar");
      RbelElementAssertion.assertThat(response)
          .extractChildWithPath("$.responseCode")
          .hasStringContentEqualTo("666");

      RbelElementAssertion.assertThat(request2)
          .extractChildWithPath("$.path")
          .hasStringContentEqualTo("/foobar");
      RbelElementAssertion.assertThat(response2)
          .extractChildWithPath("$.responseCode")
          .hasStringContentEqualTo("666");

      val responseInRequestFacet = request.getFacet(RbelHttpRequestFacet.class).get().getResponse();
      assertThat(responseInRequestFacet).isEqualTo(response);

      val responseInRequest2Facet =
          request2.getFacet(RbelHttpRequestFacet.class).get().getResponse();
      assertThat(responseInRequest2Facet).isEqualTo(response2);
    }
  }

  @Test
  void directForwardWithForwardProxy_shouldGiveError() {
    final TigerProxyConfiguration proxyConfiguration =
        TigerProxyConfiguration.builder()
            .directReverseProxy(
                DirectReverseProxyInfo.builder()
                    .hostname("localhost")
                    .port(fakeBackendServerPort)
                    .build())
            .forwardToProxy(ForwardProxyInfo.builder().hostname("localhost").port(1234).build())
            .build();

    assertThatThrownBy(() -> spawnTigerProxyWithDefaultRoutesAndWith(proxyConfiguration))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void forwardWithoutTlsTermination_shouldNotTerminateTls() throws Exception {
    int serverPort = startKonnektorAlikeServerReturningAlways555(Optional.empty());

    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
            .directReverseProxy(
                DirectReverseProxyInfo.builder().hostname("localhost").port(serverPort).build())
            .activateTlsTermination(false)
            .build());

    final TigerConfigurationPkiIdentity serverCert =
        new TigerConfigurationPkiIdentity("src/test/resources/eccStoreWithChain.jks;gematik");

    SSLContext sslContext =
        SSLContextBuilder.create()
            .loadTrustMaterial(
                serverCert.toKeyStoreWithPassword("00"), new TrustSelfSignedStrategy())
            .build();

    try (var apacheClient =
        HttpClients.custom()
            .setSSLSocketFactory(
                new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE))
            .build()) {
      CloseableHttpResponse response =
          apacheClient.execute(
              RequestBuilder.get("https://localhost:" + tigerProxy.getProxyPort()).build());

      assertThat(response.getStatusLine().getStatusCode()).isEqualTo(555);
    }
  }
}
