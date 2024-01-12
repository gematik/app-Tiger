/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.client;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static de.gematik.rbellogger.data.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.AssertionsForClassTypes.fail;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelHttpRequestFacet;
import de.gematik.rbellogger.data.facet.RbelMessageTimingFacet;
import de.gematik.rbellogger.data.facet.RbelTcpIpMessageFacet;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerRoute;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.TigerProxyTestHelper;
import de.gematik.test.tiger.proxy.controller.TigerWebUiController;
import de.gematik.test.tiger.proxy.tracing.TracingPushController;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import kong.unirest.Config;
import kong.unirest.Unirest;
import kong.unirest.UnirestInstance;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
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
  /*
   *  Our Testsetup:
   *
   *
   * -------------------     --------------    ---------------------------
   * | unirestInstance |  -> | tigerProxy | -> | remoteServer (Wiremock) |
   * -------------------     ------    ----    ---------------------------
   *          ^                     \ /
   *          ?                      V
   *          ----<-----<-----<--Tracing
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
  private static byte[] request;
  private static byte[] response;

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
  public void setup(WireMockRuntimeInfo runtimeInfo) {
    if (tigerRemoteProxyClient == null) {
      log.info("Setup remote client... {}", tigerProxy);
      tigerRemoteProxyClient =
          new TigerRemoteProxyClient(
              "http://localhost:" + springServerPort,
              TigerProxyConfiguration.builder().proxyLogLevel("WARN").build());
      tigerRemoteProxyClient.connect();

      unirestInstance =
          new UnirestInstance(new Config().proxy("localhost", tigerProxy.getProxyPort()));
    }

    runtimeInfo.getWireMock().register(stubFor(get("/foo").willReturn(ok().withBody("bar"))));
    runtimeInfo.getWireMock().register(stubFor(post("/foo").willReturn(ok().withBody("bar"))));
    runtimeInfo
        .getWireMock()
        .register(stubFor(get("/").willReturn(badRequest().withBody("emptyPath!!!"))));

    log.info("Configuring routes...");
    tigerRemoteProxyClient.clearAllMessages();
    tigerRemoteProxyClient.clearAllRoutes();
    tigerProxy.clearAllRoutes();
    tigerProxy.clearAllMessages();
    tigerProxy.addRoute(
        TigerRoute.builder()
            .from("http://myserv.er")
            .to("http://localhost:" + runtimeInfo.getHttpPort())
            .build());
  }

  @Test
  void sendMessage_shouldTriggerListener() {
    AtomicInteger listenerCallCounter = new AtomicInteger(0);
    tigerRemoteProxyClient.addRbelMessageListener(message -> listenerCallCounter.incrementAndGet());

    unirestInstance
        .get("http://myserv.er/foo")
        .asString()
        .ifFailure(response -> fail(response.getStatus() + ": " + response.getBody()));

    await().atMost(2, TimeUnit.SECONDS).until(() -> listenerCallCounter.get() > 0);

    // assert that only two messages are present
    assertThat(tigerProxy.getRbelMessagesList()).hasSize(2);
    // assert that the messages only have rudimentary information
    // (no parsing did take place on the sending tigerProxy)
    assertThat(tigerProxy.getRbelMessagesList().get(0).findRbelPathMembers("$.."))
        .hasSizeLessThan(10);
    assertThat(tigerProxy.getRbelMessagesList().get(1).findRbelPathMembers("$.."))
        .hasSizeLessThan(10);
  }

  @Test
  void rawBytesInMessage_shouldSurviveReconstruction(WireMockRuntimeInfo runtimeInfo) {
    runtimeInfo
        .getWireMock()
        .register(
            stubFor(
                post("/binary").willReturn(ok().withBody(DigestUtils.sha256("helloResponse")))));

    unirestInstance
        .post("http://myserv.er/binary")
        .body(DigestUtils.sha256("helloRequest"))
        .asString()
        .ifFailure(response -> fail(""));

    TigerProxyTestHelper.waitUntilMessageListInRemoteProxyClientContainsCountMessagesWithTimeout(
        tigerRemoteProxyClient, 2, 10);

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
    tigerRemoteProxyClient.addRbelMessageListener(message -> listenerCallCounter.incrementAndGet());

    final String body =
        RandomStringUtils.randomAlphanumeric(TracingPushController.MAX_MESSAGE_SIZE * 2);
    unirestInstance
        .post("http://myserv.er/foo")
        .body(body)
        .asString()
        .ifFailure(response -> fail(""));

    await().atMost(20, TimeUnit.SECONDS).until(() -> listenerCallCounter.get() > 0);

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

  @ParameterizedTest
  @CsvSource({
    "/foo, /foo/bar",
    "/foo/bar, /foo",
    "/foo/robots.txt, /foo",
    "/foo, /foo",
    "/foo, /foo/robots.txt",
    "http://foo/bar, http://foo/",
    "http://foo/, http://foo/bar",
    "http://foo/, http://foo",
    "http://foo, http://foo/",
    "http://foo/, http://foo/",
    "https://foo, http://foo"
  })
  void addTwoCompetingRoutes_secondOneShouldFail(
      String firstRoute, String secondRoute, WireMockRuntimeInfo runtimeInfo) {
    tigerRemoteProxyClient.addRoute(
        TigerRoute.builder()
            .from(firstRoute)
            .to("http://localhost:" + runtimeInfo.getHttpPort())
            .build());

    var route =
        TigerRoute.builder()
            .from(secondRoute)
            .to("http://localhost:" + runtimeInfo.getHttpPort())
            .build();
    assertThatThrownBy(() -> tigerRemoteProxyClient.addRoute(route))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void addAndDeleteRoute_shouldWork(WireMockRuntimeInfo runtimeInfo) {
    final String routeId =
        tigerRemoteProxyClient
            .addRoute(
                TigerRoute.builder()
                    .from("http://new.server")
                    .to("http://localhost:" + runtimeInfo.getHttpPort())
                    .build())
            .getId();

    assertThat(unirestInstance.post("http://new.server/foo").asString().getBody()).isEqualTo("bar");

    tigerRemoteProxyClient.removeRoute(routeId);

    assertThat(unirestInstance.post("http://new.server/foo").asString().getStatus()).isEqualTo(404);
  }

  @Test
  void listRoutes(WireMockRuntimeInfo runtimeInfo) {
    final List<TigerRoute> routes = tigerRemoteProxyClient.getRoutes();

    assertThat(routes)
        .extracting("from", "to", "disableRbelLogging")
        .contains(
            tuple("http://myserv.er", "http://localhost:" + runtimeInfo.getHttpPort(), false));
  }

  @Test
  void reverseProxyRoute_checkRemoteTransmission(WireMockRuntimeInfo runtimeInfo) {
    tigerProxy.addRoute(
        TigerRoute.builder()
            .from("/blub")
            .to("http://localhost:" + runtimeInfo.getHttpPort())
            .build());

    AtomicInteger listenerCallCounter = new AtomicInteger(0);
    tigerRemoteProxyClient.addRbelMessageListener(message -> listenerCallCounter.incrementAndGet());

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
        TigerRoute.builder().from("/").to("http://localhost:" + runtimeInfo.getHttpPort()).build());

    AtomicInteger listenerCallCounter = new AtomicInteger(0);
    tigerRemoteProxyClient.addRbelMessageListener(message -> listenerCallCounter.incrementAndGet());

    assertThat(
            Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/foo")
                .asString()
                .ifFailure(response -> fail("Failure from server: " + response.getBody()))
                .getBody())
        .isEqualTo("bar");

    await().atMost(2, TimeUnit.SECONDS).until(() -> listenerCallCounter.get() > 0);
  }

  @Test
  void requestToBaseUrl_shouldBeForwarded() {
    AtomicInteger listenerCallCounter = new AtomicInteger(0);
    tigerRemoteProxyClient.addRbelMessageListener(message -> listenerCallCounter.incrementAndGet());

    unirestInstance.get("http://myserv.er").asString();

    await().atMost(8, TimeUnit.SECONDS).until(() -> listenerCallCounter.get() > 0);

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
        message -> {
          if (message.hasFacet(RbelHttpRequestFacet.class)
              && message
                  .getFacetOrFail(RbelHttpRequestFacet.class)
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
    AtomicInteger listenerCallCounter = new AtomicInteger(0);
    tigerRemoteProxyClient.addRbelMessageListener(message -> listenerCallCounter.incrementAndGet());

    unirestInstance.get("http://myserv.er").asString();

    await()
        .pollDelay(200, TimeUnit.MILLISECONDS)
        .atMost(10, TimeUnit.SECONDS)
        .until(() -> listenerCallCounter.get() > 0);

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
  void multipleTrafficSources_shouldOnlySkipKnownUuidsForGivenRemote() throws Exception {
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
                newlyConnectedRemoteClient, 4, 10);

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
  void longBuffer_shouldDownloadTrafficPaged() {
    for (int i = 0; i < 100; i++) {
      addRequestResponsePair(tigerProxy.getRbelLogger().getRbelConverter());
    }
    try (TigerRemoteProxyClient newlyConnectedRemoteClient =
        new TigerRemoteProxyClient(
            "http://localhost:" + springServerPort,
            TigerProxyConfiguration.builder().downloadInitialTrafficFromEndpoints(true).build())) {
      newlyConnectedRemoteClient.connect();

      log.info("after generation we now have {} messages", tigerProxy.getRbelMessagesList().size());

      await()
          .atMost(20, TimeUnit.SECONDS)
          .pollDelay(200, TimeUnit.MILLISECONDS)
          .until(
              () ->
                  newlyConnectedRemoteClient.getRbelMessagesList().size()
                      == tigerProxy.getRbelMessagesList().size());
    }

    Mockito.verify(tigerWebUiController, Mockito.times(1))
        .downloadTraffic(
            Mockito.isNull(), Mockito.any(), Mockito.eq(Optional.of(50)), Mockito.any());
    Mockito.verify(tigerWebUiController, Mockito.times(3))
        .downloadTraffic(
            Mockito.matches(".*"), Mockito.any(), Mockito.eq(Optional.of(50)), Mockito.any());
  }

  @Test
  void outOfSyncReception1_shouldRecoverOrder() {
    AtomicInteger receivedMessages = new AtomicInteger(0);
    tigerRemoteProxyClient.addRbelMessageListener(el -> receivedMessages.incrementAndGet());

    addMessagePart("responseUuid", 1, 2);
    addMessagePart("responseUuid", 0, 2);
    addMessagePart("requestUuid", 0, 1);
    tigerRemoteProxyClient
        .getTigerStompSessionHandler()
        .getTracingStompHandler()
        .handleFrame(
            null,
            TigerTracingDto.builder()
                .requestUuid("requestUuid")
                .responseUuid("responseUuid")
                .build());

    await().atMost(5, TimeUnit.SECONDS).until(() -> receivedMessages.get() == 2);
  }

  @Test
  void outOfSyncReception2_shouldRecoverOrder() {
    AtomicInteger receivedMessages = new AtomicInteger(0);
    tigerRemoteProxyClient.addRbelMessageListener(el -> receivedMessages.incrementAndGet());

    addMessagePart("responseUuid", 1, 2);
    addMessagePart("requestUuid", 0, 1);
    tigerRemoteProxyClient
        .getTigerStompSessionHandler()
        .getTracingStompHandler()
        .handleFrame(
            null,
            TigerTracingDto.builder()
                .requestUuid("requestUuid")
                .responseUuid("responseUuid")
                .build());
    addMessagePart("responseUuid", 0, 2);

    await().atMost(5, TimeUnit.SECONDS).until(() -> receivedMessages.get() == 2);
  }

  @Test
  void outOfSyncReception3_shouldRecoverOrder() {
    AtomicInteger receivedMessages = new AtomicInteger(0);
    tigerRemoteProxyClient.addRbelMessageListener(el -> receivedMessages.incrementAndGet());

    addMessagePart("responseUuid", 1, 2);
    addMessagePart("responseUuid", 0, 2);
    tigerRemoteProxyClient
        .getTigerStompSessionHandler()
        .getTracingStompHandler()
        .handleFrame(
            null,
            TigerTracingDto.builder()
                .requestUuid("requestUuid")
                .responseUuid("responseUuid")
                .build());
    addMessagePart("requestUuid", 0, 1);

    await().atMost(5, TimeUnit.SECONDS).until(() -> receivedMessages.get() == 2);
  }

  @Test
  void strayMessageReception_shouldBeCleanedAtInterval() throws InterruptedException {
    tigerRemoteProxyClient.getPartiallyReceivedMessageMap().clear();
    tigerRemoteProxyClient.setMaximumPartialMessageAge(Duration.ofMillis(100));

    tigerRemoteProxyClient
        .getTigerStompSessionHandler()
        .getTracingStompHandler()
        .handleFrame(
            null,
            TigerTracingDto.builder()
                .requestUuid("requestUuid")
                .responseUuid("responseUuid")
                .build());
    addMessagePart("responseUuid", 1, 3);
    addMessagePart("responseUuid", 0, 3);

    tigerRemoteProxyClient.triggerPartialMessageCleanup();

    assertThat(tigerRemoteProxyClient.getPartiallyReceivedMessageMap()).hasSize(2);

    // ensure the 100ms for partial parsing timeout has occured
    Thread.sleep(110);
    tigerRemoteProxyClient.triggerPartialMessageCleanup();

    assertThat(tigerRemoteProxyClient.getPartiallyReceivedMessageMap()).isEmpty();
  }

  @SneakyThrows
  private void addRequestResponsePair(RbelConverter rbelConverter) {
    rbelConverter.parseMessage(request, null, null, Optional.empty());
    rbelConverter.parseMessage(response, null, null, Optional.empty());
  }

  private void addMessagePart(String responseUuid, int index, int numberOfMessages) {
    tigerRemoteProxyClient
        .getTigerStompSessionHandler()
        .getDataStompHandler()
        .handleFrame(
            null,
            TracingMessagePart.builder()
                .uuid(responseUuid)
                .data("blub".getBytes())
                .index(index)
                .numberOfMessages(numberOfMessages)
                .build());
  }
}
