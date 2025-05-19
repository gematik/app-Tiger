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

package de.gematik.test.tiger.proxy.client;

import static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder.responseDefinition;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static de.gematik.rbellogger.data.RbelElementAssertion.assertThat;
import static de.gematik.test.tiger.proxy.AbstractTigerProxyTest.awaitMessagesInTigerProxy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.AssertionsForClassTypes.fail;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import de.gematik.rbellogger.RbelConverter;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.data.core.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.facets.http.RbelHttpRequestFacet;
import de.gematik.rbellogger.facets.timing.RbelMessageTimingFacet;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerConfigurationRoute;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.TigerProxyTestHelper;
import de.gematik.test.tiger.proxy.client.TigerTracingDto.TigerTracingDtoBuilder;
import de.gematik.test.tiger.proxy.controller.TigerWebUiController;
import de.gematik.test.tiger.proxy.data.TigerProxyRoute;
import de.gematik.test.tiger.proxy.tracing.TracingPushService;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import kong.unirest.core.Config;
import kong.unirest.core.Unirest;
import kong.unirest.core.UnirestException;
import kong.unirest.core.UnirestInstance;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(SpringExtension.class)
@WireMockTest
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "tigerProxy.activateRbelParsing: false")
@RequiredArgsConstructor
@Slf4j
@TestInstance(Lifecycle.PER_CLASS)
@DirtiesContext
@ResetTigerConfiguration
class TigerRemoteProxyClientTest {

  private static final String JSON_PART1 = "{'blub':";
  private static final String JSON_PART2 = "'blab'}";
  private static final String SIMPLE_JSON = JSON_PART1 + JSON_PART2;

  /*
   *  Our Testsetup:
   *
   *
   * --------------------------     --------------    ---------------------------
   * | unirestInstance        |  -> | tigerProxy | -> | remoteServer (Wiremock) |
   * | tigerRemoteProxyClient |  -> |            | -> |                         |
   * --------------------------     ------    ----    ---------------------------
   *          ^                           \ /
   *          ?                            V
   *          ----<-----<-----<-----<---Tracing
   *
   */

  // the remote server (to which we send requests) is WireMock (see @WireMockTest above the class)

  // the remote proxy (routing the requests to the remote server)
  @Autowired private TigerProxy tigerProxy;
  @SpyBean private TigerWebUiController tigerWebUiController;

  // the local TigerProxy-Client (which syphons the message from the remote Tiger Proxy)
  private static TigerRemoteProxyClient tigerRemoteProxyClient;
  private static UnirestInstance unirestInstance;

  @LocalServerPort private int springServerPort;
  private static final byte[] request;
  private static final byte[] response;

  static {
    try {
      request =
          FileUtils.readFileToByteArray(new File("src/test/resources/messages/getRequest.curl"));
      response =
          FileUtils.readFileToByteArray(new File("src/test/resources/messages/getResponse.curl"));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @BeforeEach
  void setup(WireMockRuntimeInfo runtimeInfo) {
    tigerProxy.clearAllMessages();
    if (unirestInstance == null) {
      unirestInstance =
          new UnirestInstance(
              new Config().requestTimeout(5000).proxy("localhost", tigerProxy.getProxyPort()));
    }
    if (tigerRemoteProxyClient == null) {
      log.info("Setup remote client... {}", tigerProxy);
      tigerRemoteProxyClient =
          new TigerRemoteProxyClient(
              "http://localhost:" + springServerPort,
              TigerProxyConfiguration.builder()
                  .proxyLogLevel("WARN")
                  .trafficDownloadPageSize(10)
                  .name("tigerRemoteProxyClient")
                  .build());
      tigerRemoteProxyClient.connect();
      await().atMost(5, TimeUnit.SECONDS).until(tigerRemoteProxyClient::isConnected);
    } else {
      tigerRemoteProxyClient.clearAllMessages();
      tigerRemoteProxyClient.clearAllRoutes();
    }

    unirestInstance =
        new UnirestInstance(new Config().proxy("localhost", tigerProxy.getProxyPort()));

    runtimeInfo.getWireMock().register(get("/foo").willReturn(ok().withBody("bar")));
    runtimeInfo.getWireMock().register(post("/foo").willReturn(ok().withBody("bar")));
    runtimeInfo.getWireMock().register(get("/").willReturn(badRequest().withBody("emptyPath!!!")));
    runtimeInfo
        .getWireMock()
        .register(
            get("/error")
                .willReturn(responseDefinition().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    log.info("Configuring routes...");
    tigerRemoteProxyClient.clearAllMessages();
    tigerRemoteProxyClient.clearAllRoutes();
    ReflectionTestUtils.setField(tigerProxy, "name", Optional.of("Main TigerProxy"));
    tigerProxy.clearAllRoutes();
    tigerProxy.clearAllMessages();
    tigerProxy.getRbelLogger().getRbelConverter().setName("Main TigerProxy");
    tigerProxy.addRoute(
        TigerConfigurationRoute.builder()
            .from("http://myserv.er")
            .to("http://localhost:" + runtimeInfo.getHttpPort())
            .build());
  }

  @AfterAll
  void tearDown() {
    unirestInstance.close();
    unirestInstance = null;
    tigerRemoteProxyClient = null;
  }

  @Test
  void sendMessage_shouldTriggerListener() {
    AtomicInteger listenerCallCounter = new AtomicInteger(0);
    tigerRemoteProxyClient.addRbelMessageListener(msg -> listenerCallCounter.incrementAndGet());

    unirestInstance
        .get("http://myserv.er/foo")
        .asString()
        .ifFailure(res -> fail(res.getStatus() + ": " + res.getBody()));

    await().atMost(2, TimeUnit.SECONDS).until(() -> listenerCallCounter.get() > 0);

    // assert that only two messages are present
    assertThat(tigerProxy.getRbelMessagesList()).hasSize(2);
    // assert that the messages only have rudimentary information
    // (no parsing did take place on the sending tigerProxy)
    assertThat(tigerProxy.getRbelMessagesList().get(0).findRbelPathMembers("$.."))
        .hasSizeLessThan(10);
    assertThat(tigerProxy.getRbelMessagesList().get(1).findRbelPathMembers("$.."))
        .hasSizeLessThan(16);
  }

  @Test
  void rawBytesInMessage_shouldSurviveReconstruction(WireMockRuntimeInfo runtimeInfo) {
    runtimeInfo
        .getWireMock()
        .register(post("/binary").willReturn(ok().withBody(DigestUtils.sha256("helloResponse"))));

    unirestInstance
        .post("http://myserv.er/binary")
        .body(DigestUtils.sha256("helloRequest"))
        .asString()
        .ifFailure(r -> fail(""));

    TigerProxyTestHelper.waitUntilMessageListInRemoteProxyClientContainsCountMessagesWithTimeout(
        tigerRemoteProxyClient, 2, 10);
    tigerProxy.waitForAllCurrentMessagesToBeParsed();

    assertThat(tigerRemoteProxyClient.getRbelMessagesList().get(0))
        .extractChildWithPath("$.body")
        .getContent()
        .isEqualTo(DigestUtils.sha256("helloRequest"));
    assertThat(tigerRemoteProxyClient.getRbelMessagesList().get(1))
        .extractChildWithPath("$.body")
        .getContent()
        .isEqualTo(DigestUtils.sha256("helloResponse"));
  }

  @Test
  void giantMessage_shouldTriggerListener() {
    AtomicInteger listenerCallCounter = new AtomicInteger(0);
    tigerRemoteProxyClient.addRbelMessageListener(msg -> listenerCallCounter.incrementAndGet());

    final String body =
        RandomStringUtils.insecure().nextAlphanumeric(TracingPushService.MAX_MESSAGE_SIZE * 2);
    unirestInstance
        .post("http://myserv.er/foo")
        .body(body)
        .asString()
        .ifFailure(response -> fail(""));

    await().atMost(10, TimeUnit.SECONDS).until(() -> listenerCallCounter.get() > 0);
    awaitMessagesInTigerProxy(tigerRemoteProxyClient, 2);

    assertThat(
            new String(
                tigerRemoteProxyClient
                    .getRbelMessagesList()
                    .get(tigerRemoteProxyClient.getRbelMessagesList().size() - 2)
                    .findElement("$.body")
                    .get()
                    .getRawContent()))
        .isEqualTo(body);
  }

  @Test
  void addAndDeleteRoute_shouldWork(WireMockRuntimeInfo runtimeInfo) {
    final String routeId =
        tigerRemoteProxyClient
            .addRoute(
                TigerProxyRoute.builder()
                    .from("http://new.server")
                    .to("http://localhost:" + runtimeInfo.getHttpPort())
                    .build())
            .getId();

    assertThat(unirestInstance.post("http://new.server/foo").asString().getBody()).isEqualTo("bar");

    tigerRemoteProxyClient.removeRoute(routeId);

    assertThatThrownBy(() -> unirestInstance.post("http://new.server/foo").asString())
        .isInstanceOf(UnirestException.class);
  }

  @Test
  void listRoutes(WireMockRuntimeInfo runtimeInfo) {
    final List<TigerProxyRoute> routes = tigerRemoteProxyClient.getRoutes();

    assertThat(routes)
        .extracting("from", "to", "disableRbelLogging")
        .contains(
            tuple("http://myserv.er", "http://localhost:" + runtimeInfo.getHttpPort(), false));
  }

  @Test
  void deleteNonExistingRoute_shouldGiveNoException() {
    assertThatNoException().isThrownBy(() -> tigerRemoteProxyClient.removeRoute("nonExistingId"));
  }

  @Test
  void deleteInternalRoute_shouldGiveException() {
    val internalRouteId =
        tigerRemoteProxyClient.getRoutes().stream()
            .filter(TigerProxyRoute::isInternalRoute)
            .map(TigerProxyRoute::getId)
            .findFirst()
            .get();

    assertThatThrownBy(() -> tigerRemoteProxyClient.removeRoute(internalRouteId))
        .isInstanceOf(TigerRemoteProxyClientException.class)
        .hasMessageContaining("Is internal route!");
  }

  @Test
  void reverseProxyRoute_checkRemoteTransmission(WireMockRuntimeInfo runtimeInfo) {
    tigerProxy.addRoute(
        TigerConfigurationRoute.builder()
            .from("/blub")
            .to("http://localhost:" + runtimeInfo.getHttpPort())
            .build());

    AtomicInteger listenerCallCounter = new AtomicInteger(0);
    tigerRemoteProxyClient.addRbelMessageListener(msg -> listenerCallCounter.incrementAndGet());

    assertThat(
            Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/blub/foo")
                .asString()
                .ifFailure(
                    response ->
                        fail(
                            "Failure from server: HTTP "
                                + response.getStatus()
                                + ": "
                                + response.getBody()))
                .getBody())
        .isEqualTo("bar");

    await().atMost(2, TimeUnit.SECONDS).until(() -> listenerCallCounter.get() > 0);
  }

  @Test
  void reverseProxyRootRoute_checkRemoteTransmission(WireMockRuntimeInfo runtimeInfo) {
    tigerProxy.addRoute(
        TigerConfigurationRoute.builder()
            .from("/")
            .to("http://localhost:" + runtimeInfo.getHttpPort())
            .build());

    AtomicInteger listenerCallCounter = new AtomicInteger(0);
    tigerRemoteProxyClient.addRbelMessageListener(msg -> listenerCallCounter.incrementAndGet());

    assertThat(
            Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/foo")
                .asString()
                .ifFailure(response -> fail("Failure from server: " + response.getBody()))
                .getBody())
        .isEqualTo("bar");

    await().atMost(2, TimeUnit.SECONDS).until(() -> listenerCallCounter.get() > 0);
    System.out.println();
  }

  @SneakyThrows
  @Test
  void requestToBaseUrl_shouldBeForwarded() {
    unirestInstance.get("http://myserv.er").asString();

    awaitMessagesInTigerProxy(tigerRemoteProxyClient, 2);

    assertThat(tigerRemoteProxyClient.getRbelMessagesList().get(0).findElement("$.path"))
        .get()
        .extracting(RbelElement::getRawStringContent)
        .isEqualTo("/");
  }

  @ParameterizedTest
  @CsvSource({"message.url =$ 'faa'", "request.url =$ 'faa'", "response.statusCode == '404'"})
  @Disabled("deactivated due to buildserver problems")
  // TODO TGR-794
  void downstreamTigerProxyWithFilterCriterion_shouldOnlyShowMatchingMessages(
      String filterCriterion) {
    var filteredTigerProxy =
        new TigerProxy(
            TigerProxyConfiguration.builder()
                .trafficEndpoints(List.of("http://localhost:" + springServerPort))
                .trafficEndpointFilterString(filterCriterion)
                .proxyLogLevel("WARN")
                .build());

    AtomicInteger listenerCallCounter = new AtomicInteger(0);
    filteredTigerProxy.addRbelMessageListener(
        msg -> {
          if (msg.hasFacet(RbelHttpRequestFacet.class)
              && msg.getFacetOrFail(RbelHttpRequestFacet.class)
                  .getPath()
                  .getRawStringContent()
                  .endsWith("faa")) {
            // this ensures we only leave the wait after the /faa call
            listenerCallCounter.incrementAndGet();
          }
        });

    unirestInstance.get("http://myserv.er/foo").asString();
    unirestInstance.get("http://myserv.er/faa").asString();

    await()
        .pollDelay(200, TimeUnit.MILLISECONDS)
        .atMost(10, TimeUnit.SECONDS)
        .until(() -> listenerCallCounter.get() > 0);

    assertThat(filteredTigerProxy.getRbelMessages()).hasSize(2);
    assertThat(
            filteredTigerProxy
                .getRbelMessages()
                .getFirst()
                .findElement("$.path")
                .get()
                .getRawStringContent())
        .isEqualTo("/faa");
    assertThat(
            filteredTigerProxy
                .getRbelMessages()
                .getLast()
                .findElement("$.responseCode")
                .get()
                .getRawStringContent())
        .isEqualTo("404");
  }

  @Test
  void trafficForwardingShouldPreserveTimingAndAddressingInformation() {
    unirestInstance.get("http://myserv.er").asString();

    awaitMessagesInTigerProxy(tigerRemoteProxyClient, 2);

    assertThat(
            tigerRemoteProxyClient
                .getRbelMessagesList()
                .get(0)
                .getFacetOrFail(RbelMessageTimingFacet.class)
                .getTransmissionTime())
        .isEqualTo(
            tigerProxy
                .getRbelMessagesList()
                .get(0)
                .getFacetOrFail(RbelMessageTimingFacet.class)
                .getTransmissionTime());
    assertThat(
            tigerRemoteProxyClient
                .getRbelMessagesList()
                .get(1)
                .getFacetOrFail(RbelMessageTimingFacet.class)
                .getTransmissionTime())
        .isEqualTo(
            tigerProxy
                .getRbelMessagesList()
                .get(1)
                .getFacetOrFail(RbelMessageTimingFacet.class)
                .getTransmissionTime());
    assertThat(
            tigerRemoteProxyClient
                .getRbelMessagesList()
                .get(1)
                .getFacetOrFail(RbelTcpIpMessageFacet.class)
                .getSender()
                .getRawStringContent())
        .isEqualTo("myserv.er:80");
    // TODO TGR-651 wieder reaktivieren
    // assertThat(tigerRemoteProxyClient.getRbelMessagesList().get(1)
    //    .getFacetOrFail(RbelTcpIpMessageFacet.class).getReceiver().getRawStringContent())
    //    .matches("(view-localhost|localhost):[\\d]*");
  }

  @Test
  void laterConnect_shouldDownloadInitialTraffic() {
    unirestInstance.get("http://myserv.er/foobarString").asString();

    try (TigerRemoteProxyClient newlyConnectedRemoteClient =
        new TigerRemoteProxyClient(
            "http://localhost:" + springServerPort,
            TigerProxyConfiguration.builder().downloadInitialTrafficFromEndpoints(true).build())) {
      newlyConnectedRemoteClient.connect();

      TigerProxyTestHelper.waitUntilMessageListInRemoteProxyClientContainsCountMessagesWithTimeout(
          newlyConnectedRemoteClient, 2, 10);
      tigerProxy.waitForAllCurrentMessagesToBeParsed();

      Mockito.verify(tigerWebUiController)
          .downloadTraffic(Mockito.isNull(), Mockito.any(), Mockito.any(), Mockito.any());

      assertThat(
              newlyConnectedRemoteClient
                  .getRbelMessagesList()
                  .get(0)
                  .findElement("$.path")
                  .get()
                  .getRawStringContent())
          .isEqualTo("/foobarString");
    }
  }

  @Test
  void initialTrafficDownloadWithMultiplePages() {
    final int numberOfGeneratedMessages = 153;
    for (int i = 0; i < numberOfGeneratedMessages; i++) {
      unirestInstance.get("http://myserv.er/foobarString").asString();
    }

    try (TigerRemoteProxyClient newlyConnectedRemoteClient =
        new TigerRemoteProxyClient(
            "http://localhost:" + springServerPort,
            TigerProxyConfiguration.builder()
                .downloadInitialTrafficFromEndpoints(true)
                .trafficDownloadPageSize(20)
                .build())) {
      newlyConnectedRemoteClient.connect();

      TigerProxyTestHelper.waitUntilMessageListInRemoteProxyClientContainsCountMessagesWithTimeout(
          newlyConnectedRemoteClient, numberOfGeneratedMessages * 2, 10);
      tigerProxy.waitForAllCurrentMessagesToBeParsed();

      assertThat(newlyConnectedRemoteClient.getRbelMessagesList())
          .hasSize(numberOfGeneratedMessages * 2);
    }
  }

  @Test
  void multipleTrafficSources_shouldOnlySkipKnownUuidsForGivenRemote() {
    unirestInstance.get("http://myserv.er/foobarString").asString();

    try (TigerProxy masterTigerProxy = new TigerProxy(TigerProxyConfiguration.builder().build())) {
      addRequestResponsePair(masterTigerProxy.getRbelLogger().getRbelConverter());
      assertThat(masterTigerProxy.getRbelMessagesList()).hasSize(2);

      try (TigerRemoteProxyClient newlyConnectedRemoteClient =
          new TigerRemoteProxyClient(
              "http://localhost:" + springServerPort,
              TigerProxyConfiguration.builder().downloadInitialTrafficFromEndpoints(true).build(),
              masterTigerProxy)) {
        newlyConnectedRemoteClient.connect();

        TigerProxyTestHelper
            .waitUntilMessageListInRemoteProxyClientContainsCountMessagesWithTimeout(
                newlyConnectedRemoteClient, 4, 10); // 2 unirest + 2 master
        tigerProxy.waitForAllCurrentMessagesToBeParsed();

        Mockito.verify(tigerWebUiController)
            .downloadTraffic(Mockito.isNull(), Mockito.any(), Mockito.any(), Mockito.any());

        assertThat(
                ((AtomicReference<?>)
                        ReflectionTestUtils.getField(newlyConnectedRemoteClient, "lastMessageUuid"))
                    .get())
            .isNotNull();
      }
    }
  }

  @Test
  void outOfSyncReception1_shouldRecoverOrder() {
    AtomicInteger receivedMessages = new AtomicInteger(0);
    tigerRemoteProxyClient.addRbelMessageListener(msg -> receivedMessages.incrementAndGet());

    addMessagePart("responseUuid", 1, 2, JSON_PART2);
    addMessagePart("responseUuid", 0, 2, JSON_PART1);
    addMessagePart("requestUuid", 0, 1, SIMPLE_JSON);
    tigerRemoteProxyClient
        .getTigerStompSessionHandler()
        .getTracingStompHandler()
        .handleFrame(null, buildTracingDtoWithUuidAndSequenceNumber("responseUuid", 2L, false));
    tigerRemoteProxyClient
        .getTigerStompSessionHandler()
        .getTracingStompHandler()
        .handleFrame(null, buildTracingDtoWithUuidAndSequenceNumber("requestUuid", 1L, true));

    await().atMost(5, TimeUnit.SECONDS).until(() -> receivedMessages.get() == 2);
  }

  @Test
  void outOfSyncReception2_shouldRecoverOrder() {
    AtomicInteger receivedMessages = new AtomicInteger(0);
    tigerRemoteProxyClient.addRbelMessageListener(msg -> receivedMessages.incrementAndGet());

    addMessagePart("responseUuid", 1, 2, JSON_PART2);
    addMessagePart("requestUuid", 0, 1, SIMPLE_JSON);
    tigerRemoteProxyClient
        .getTigerStompSessionHandler()
        .getTracingStompHandler()
        .handleFrame(null, buildTracingDtoWithUuidAndSequenceNumber("responseUuid", 2L, false));
    tigerRemoteProxyClient
        .getTigerStompSessionHandler()
        .getTracingStompHandler()
        .handleFrame(null, buildTracingDtoWithUuidAndSequenceNumber("requestUuid", 1L, true));
    addMessagePart("responseUuid", 0, 2, JSON_PART1);

    await().atMost(5, TimeUnit.SECONDS).until(() -> receivedMessages.get() == 2);
  }

  @Test
  void outOfSyncReception3_shouldRecoverOrder() {
    AtomicInteger receivedMessages = new AtomicInteger(0);
    tigerRemoteProxyClient.addRbelMessageListener(el -> receivedMessages.incrementAndGet());

    addMessagePart("responseUuid", 1, 2, JSON_PART2);
    addMessagePart("responseUuid", 0, 2, JSON_PART1);
    tigerRemoteProxyClient
        .getTigerStompSessionHandler()
        .getTracingStompHandler()
        .handleFrame(null, buildTracingDtoWithUuidAndSequenceNumber("responseUuid", 2L, false));
    tigerRemoteProxyClient
        .getTigerStompSessionHandler()
        .getTracingStompHandler()
        .handleFrame(null, buildTracingDtoWithUuidAndSequenceNumber("requestUuid", 1L, true));
    addMessagePart("requestUuid", 0, 1, SIMPLE_JSON);

    await().atMost(5, TimeUnit.SECONDS).until(() -> receivedMessages.get() == 2);
  }

  private Object buildTracingDtoWithUuidAndSequenceNumber(
      String uuid, long sequenceNumber, boolean request) {
    final TigerTracingDtoBuilder builder =
        TigerTracingDto.builder().messageUuid(uuid).sequenceNumber(sequenceNumber);
    if (request) {
      builder
          .sender(RbelHostname.fromString("client:80").get())
          .receiver(RbelHostname.fromString("server:80").get());
    } else {
      builder
          .sender(RbelHostname.fromString("server:80").get())
          .receiver(RbelHostname.fromString("client:80").get());
    }
    return builder.build();
  }

  @Test
  void strayMessageReception_shouldBeCleanedAtInterval() throws InterruptedException {
    tigerRemoteProxyClient.getPartiallyReceivedMessageMap().clear();
    tigerRemoteProxyClient.setMaximumPartialMessageAge(Duration.ofMillis(100));

    tigerRemoteProxyClient
        .getTigerStompSessionHandler()
        .getTracingStompHandler()
        .handleFrame(
            null, TigerTracingDto.builder().messageUuid("responseUuid").sequenceNumber(2L).build());
    tigerRemoteProxyClient
        .getTigerStompSessionHandler()
        .getTracingStompHandler()
        .handleFrame(
            null, TigerTracingDto.builder().messageUuid("requestUuid").sequenceNumber(1L).build());
    addMessagePart("responseUuid", 1, 3, "blub");
    addMessagePart("responseUuid", 0, 3, "blub");

    tigerRemoteProxyClient.triggerPartialMessageCleanup();

    assertThat(tigerRemoteProxyClient.getPartiallyReceivedMessageMap()).hasSize(2);

    // ensure the 100ms for partial parsing timeout has occured
    Thread.sleep(110);
    tigerRemoteProxyClient.triggerPartialMessageCleanup();

    assertThat(tigerRemoteProxyClient.getPartiallyReceivedMessageMap()).isEmpty();
  }

  @Test
  void nonExistentRemoteProxy_shouldStartWhenIgnored() {
    try (final var tigerProxy =
        new TigerProxy(
            TigerProxyConfiguration.builder()
                .trafficEndpoints(List.of("http://localhost:1234"))
                .failOnOfflineTrafficEndpoints(true)
                .connectionTimeoutInSeconds(1)
                .build())) {
      assertThatNoException().isThrownBy(tigerProxy::subscribeToTrafficEndpoints);
    }
  }

  @SneakyThrows
  private void addRequestResponsePair(RbelConverter rbelConverter) {
    rbelConverter.parseMessage(request, new RbelMessageMetadata());
    rbelConverter.parseMessage(response, new RbelMessageMetadata());
  }

  @Disabled("waiting on TGR-1684")
  @Test
  void exceptionDuringRequest_shouldShowUpInLog() {
    AtomicInteger listenerCallCounter = new AtomicInteger(0);
    tigerRemoteProxyClient.addRbelMessageListener(msg -> listenerCallCounter.incrementAndGet());

    unirestInstance.config().retryAfter(false);
    assertThatThrownBy(() -> unirestInstance.get("http://myserv.er/error").asString()).isNotNull();

    await().atMost(8, TimeUnit.SECONDS).until(() -> listenerCallCounter.get() > 0);

    // wir erwarten zwei Nachrichten im tigerRemoteProxyClient
    // wir erwarten eine propagierte Exception im tigerRemoteProxyClient
  }

  private void addMessagePart(
      String responseUuid, int index, int numberOfMessages, String content) {
    tigerRemoteProxyClient
        .getTigerStompSessionHandler()
        .getDataStompHandler()
        .handleFrame(
            null,
            TracingMessagePart.builder()
                .uuid(responseUuid)
                .data(content.getBytes())
                .index(index)
                .numberOfMessages(numberOfMessages)
                .build());
  }
}
