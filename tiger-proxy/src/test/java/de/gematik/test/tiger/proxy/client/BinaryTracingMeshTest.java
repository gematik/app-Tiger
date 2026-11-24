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

import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerConfigurationRoute;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.TigerProxyApplication;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import kong.unirest.core.Config;
import kong.unirest.core.Unirest;
import kong.unirest.core.UnirestInstance;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.AbstractSubscribableChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * A Tiger proxy and the proxies that are connected to it (ADR 026): what they agree on when they
 * connect, and that the messages arrive however they are sent.
 */
@ExtendWith(SpringExtension.class)
@WireMockTest
@SpringBootTest(
    classes = TigerProxyApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "tigerProxy.activateRbelParsing: false")
@DirtiesContext
@ResetTigerConfiguration
class BinaryTracingMeshTest {

  @Autowired private TigerProxy tigerProxy;

  @Autowired
  @Qualifier("brokerChannel")
  private AbstractSubscribableChannel brokerChannel;

  @LocalServerPort private int springServerPort;

  private final List<TigerRemoteProxyClient> receivers = new ArrayList<>();
  private final List<String> sentDestinations = new CopyOnWriteArrayList<>();

  private final ChannelInterceptor destinationRecorder =
      new ChannelInterceptor() {
        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
          sentDestinations.add(
              String.valueOf(SimpMessageHeaderAccessor.getDestination(message.getHeaders())));
          return message;
        }
      };
  private UnirestInstance unirest;

  @BeforeEach
  void setup(WireMockRuntimeInfo runtimeInfo) {
    runtimeInfo.getWireMock().register(post("/foo").willReturn(ok().withBody("bar")));
    tigerProxy.clearAllMessages();
    tigerProxy.clearAllRoutes();
    tigerProxy.addRoute(
        TigerConfigurationRoute.builder()
            .from("http://myserv.er")
            .to("http://localhost:" + runtimeInfo.getHttpPort())
            .build());
    unirest = new UnirestInstance(new Config().proxy("localhost", tigerProxy.getProxyPort()));
    sentDestinations.clear();
    brokerChannel.addInterceptor(destinationRecorder);
  }

  @AfterEach
  void tearDown() {
    brokerChannel.removeInterceptor(destinationRecorder);
    receivers.forEach(TigerRemoteProxyClient::close);
    receivers.clear();
    unirest.close();
  }

  /**
   * A receiver of this proxy; one that is told the proxy offers no binary frames behaves as one of
   * an older proxy would.
   */
  private TigerRemoteProxyClient connectReceiver(boolean binaryFrames) {
    var configuration =
        TigerProxyConfiguration.builder()
            .proxyLogLevel("WARN")
            .name("receiver-" + receivers.size())
            .build();
    var url = "http://localhost:" + springServerPort;
    var receiver =
        binaryFrames
            ? new TigerRemoteProxyClient(url, configuration)
            : new TigerRemoteProxyClient(url, configuration) {
              @Override
              boolean remoteOffersBinaryFrames() {
                return false;
              }
            };
    receivers.add(receiver);
    receiver.connect();
    await().atMost(10, TimeUnit.SECONDS).until(receiver::isConnected);
    return receiver;
  }

  private void awaitRequestAndResponseIn(TigerRemoteProxyClient receiver) {
    await()
        .atMost(20, TimeUnit.SECONDS)
        .until(() -> receiver.getMessages().size() == 2 && tigerProxy.getMessages().size() == 2);
  }

  private void assertThatTheMessagesAreTheSame(TigerRemoteProxyClient receiver) {
    for (RbelElement sent : tigerProxy.getMessages()) {
      var received = receiver.getRbelLogger().getRbelConverter().findMessageByUuid(sent.getUuid());
      assertThat(received).as("message %s", sent.getUuid()).isPresent();
      assertThat(received.get().getRawContent()).isEqualTo(sent.getRawContent());
    }
  }

  @Test
  void aProxyThatOffersBinaryFrames_isTold() {
    var capabilities =
        Unirest.get("http://localhost:" + springServerPort + "/tracing/capabilities")
            .asObject(TracingCapabilities.class)
            .getBody();

    assertThat(capabilities.getBinaryDestination()).isEqualTo(TigerRemoteProxyClient.WS_RAW_BINARY);
  }

  @Test
  void messagesArriveInBinaryFramesWhenBothSidesAgree() {
    var receiver = connectReceiver(true);
    assertThat(receiver.isBinaryTracing()).isTrue();

    unirest.post("http://myserv.er/foo").body("hello binary").asString();

    awaitRequestAndResponseIn(receiver);
    assertThatTheMessagesAreTheSame(receiver);
    assertThat(receiver.getMessages().iterator().next().getRawStringContent())
        .contains("hello binary");
  }

  @Test
  void aMessageOfSeveralFrames_arrivesAsAWhole() {
    var receiver = connectReceiver(true);
    String body = RandomStringUtils.insecure().nextAlphanumeric(200_000);

    unirest.post("http://myserv.er/foo").body(body).asString();

    awaitRequestAndResponseIn(receiver);
    assertThatTheMessagesAreTheSame(receiver);
    assertThat(receiver.getMessages().iterator().next().getRawContent().length)
        .isGreaterThan(200_000);
  }

  @Test
  void aReceiverOfARemoteWithoutBinaryFrames_getsTheLegacyFrames() {
    var receiver = connectReceiver(false);
    assertThat(receiver.isBinaryTracing()).isFalse();

    unirest.post("http://myserv.er/foo").body("hello legacy").asString();

    awaitRequestAndResponseIn(receiver);
    assertThatTheMessagesAreTheSame(receiver);
  }

  @Test
  void aProxyWithOnlyBinaryReceivers_doesNotSendTheDataInTheLegacyFormat() {
    var receiver = connectReceiver(true);
    assertThat(receiver.isBinaryTracing()).isTrue();

    unirest.post("http://myserv.er/foo").body("hello binary only").asString();

    awaitRequestAndResponseIn(receiver);
    assertThat(sentDestinations)
        .contains(TigerRemoteProxyClient.WS_TRACING, TigerRemoteProxyClient.WS_RAW_BINARY)
        .doesNotContain(TigerRemoteProxyClient.WS_DATA);
  }

  @Test
  void aProxyWithOnlyLegacyReceivers_doesNotSendTheBinaryFrames() {
    var receiver = connectReceiver(false);
    assertThat(receiver.isBinaryTracing()).isFalse();

    unirest.post("http://myserv.er/foo").body("hello legacy only").asString();

    awaitRequestAndResponseIn(receiver);
    assertThat(sentDestinations)
        .contains(TigerRemoteProxyClient.WS_TRACING, TigerRemoteProxyClient.WS_DATA)
        .doesNotContain(TigerRemoteProxyClient.WS_RAW_BINARY);
  }

  @Test
  void receiversOfBothKinds_getTheSameMessagesFromOneProxy() {
    var binary = connectReceiver(true);
    var legacy = connectReceiver(false);
    assertThat(binary.isBinaryTracing()).isTrue();
    assertThat(legacy.isBinaryTracing()).isFalse();

    unirest.post("http://myserv.er/foo").body("hello both").asString();

    awaitRequestAndResponseIn(binary);
    awaitRequestAndResponseIn(legacy);
    assertThatTheMessagesAreTheSame(binary);
    assertThatTheMessagesAreTheSame(legacy);
  }
}
