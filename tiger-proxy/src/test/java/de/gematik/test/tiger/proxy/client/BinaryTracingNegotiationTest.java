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
package de.gematik.test.tiger.proxy.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import java.io.IOException;
import java.net.ServerSocket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * What a receiving Tiger proxy makes of the answers of a remote (ADR 026): it uses binary frames
 * only if the remote offers them, and in every other case the legacy frames.
 */
@WireMockTest
class BinaryTracingNegotiationTest {

  private static final String CAPABILITIES = "/tracing/capabilities";

  private TigerRemoteProxyClient receiver;

  @AfterEach
  void tearDown() {
    if (receiver != null) {
      receiver.close();
    }
  }

  private TigerRemoteProxyClient receiverOf(String remoteUrl, boolean legacyTraffic) {
    receiver =
        new TigerRemoteProxyClient(
            remoteUrl,
            TigerProxyConfiguration.builder()
                .proxyLogLevel("WARN")
                .name("receiver")
                .enableLegacyTraffic(legacyTraffic)
                .build());
    return receiver;
  }

  private static void remoteAnswers(
      WireMockRuntimeInfo wireMock, ResponseDefinitionBuilder answer) {
    wireMock.getWireMock().register(get(urlEqualTo(CAPABILITIES)).willReturn(answer));
  }

  private static ResponseDefinitionBuilder capabilities(String body) {
    return aResponse().withHeader("Content-Type", "application/json").withBody(body);
  }

  private static ResponseDefinitionBuilder capabilitiesWithBinaryDestination(String destination) {
    return capabilities("{\"binaryDestination\": \"%s\"}".formatted(destination));
  }

  @Test
  void aRemoteThatOffersBinaryFrames_getsThem(WireMockRuntimeInfo wireMock) {
    remoteAnswers(
        wireMock, capabilitiesWithBinaryDestination(TigerRemoteProxyClient.WS_RAW_BINARY));

    assertThat(receiverOf(wireMock.getHttpBaseUrl(), false).remoteOffersBinaryFrames()).isTrue();
  }

  @Test
  void aRemoteThatOffersNothing_getsTheLegacyFrames(WireMockRuntimeInfo wireMock) {
    remoteAnswers(wireMock, capabilities("{}"));

    assertThat(receiverOf(wireMock.getHttpBaseUrl(), false).remoteOffersBinaryFrames()).isFalse();
  }

  @Test
  void aRemoteThatOffersThemOnAnotherDestination_getsTheLegacyFrames(WireMockRuntimeInfo wireMock) {
    remoteAnswers(wireMock, capabilitiesWithBinaryDestination("/topic/something-else"));

    assertThat(receiverOf(wireMock.getHttpBaseUrl(), false).remoteOffersBinaryFrames()).isFalse();
  }

  @Test
  void aRemoteWithoutTheEndpoint_getsTheLegacyFrames(WireMockRuntimeInfo wireMock) {
    remoteAnswers(wireMock, aResponse().withStatus(404));

    assertThat(receiverOf(wireMock.getHttpBaseUrl(), false).remoteOffersBinaryFrames()).isFalse();
  }

  @Test
  void aRemoteThatAnswersWithAnError_getsTheLegacyFrames(WireMockRuntimeInfo wireMock) {
    remoteAnswers(wireMock, aResponse().withStatus(500));

    assertThat(receiverOf(wireMock.getHttpBaseUrl(), false).remoteOffersBinaryFrames()).isFalse();
  }

  @Test
  void aRemoteThatAnswersWithSomethingElse_getsTheLegacyFrames(WireMockRuntimeInfo wireMock) {
    remoteAnswers(
        wireMock,
        aResponse().withHeader("Content-Type", "text/html").withBody("<html>not a proxy</html>"));

    assertThat(receiverOf(wireMock.getHttpBaseUrl(), false).remoteOffersBinaryFrames()).isFalse();
  }

  @Test
  void aRemoteThatCannotBeReached_getsTheLegacyFrames() throws IOException {
    int unusedPort;
    try (var socket = new ServerSocket(0)) {
      unusedPort = socket.getLocalPort();
    }

    assertThat(receiverOf("http://localhost:" + unusedPort, false).remoteOffersBinaryFrames())
        .isFalse();
  }

  @Test
  void aReceiverOfLegacyTraffic_doesNotAsk(WireMockRuntimeInfo wireMock) {
    remoteAnswers(
        wireMock, capabilitiesWithBinaryDestination(TigerRemoteProxyClient.WS_RAW_BINARY));

    assertThat(receiverOf(wireMock.getHttpBaseUrl(), true).remoteOffersBinaryFrames()).isFalse();
    wireMock.getWireMock().verifyThat(0, getRequestedFor(urlEqualTo(CAPABILITIES)));
  }
}
