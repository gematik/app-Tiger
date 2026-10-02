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
package de.gematik.test.tiger.proxy.tracing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.proxy.TigerProxyApplication;
import de.gematik.test.tiger.proxy.client.TigerRemoteProxyClient;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

/** Regression tests for {@link TracingEndpointConfiguration}. */
@SpringBootTest(
        classes = TigerProxyApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties =
        "tiger-proxy.stompHeartbeatInSeconds=" + TracingEndpointConfigurationTest.HEARTBEAT_SECONDS)
@Slf4j
@DirtiesContext
@ResetTigerConfiguration
class TracingEndpointConfigurationTest {

  static final String HEARTBEAT_SECONDS = "30";

  private static final String TRACES_PROBE = "probe-traces";
  private static final String DATA_PROBE = "probe-data";

  @LocalServerPort private int port;

  @Autowired private SimpMessagingTemplate template;

  private final List<WebSocketStompClient> openedClients = new ArrayList<>();
  private final List<StompSession> openedSessions = new ArrayList<>();

  @AfterEach
  void closeStompSessions() {
    openedSessions.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
    openedSessions.clear();
    openedClients.forEach(WebSocketStompClient::stop);
    openedClients.clear();
  }

  private StompSession openSession(
      WebSocketClient webSocketClient,
      MessageConverter messageConverter,
      String url,
      WebSocketHttpHeaders headers)
      throws Exception {
    var stompClient = new WebSocketStompClient(webSocketClient);
    stompClient.setMessageConverter(messageConverter);
    openedClients.add(stompClient);
    var session =
        stompClient
            .connectAsync(url, headers, new StompHeaders(), new StompSessionHandlerAdapter() {})
            .get(5, TimeUnit.SECONDS);
    openedSessions.add(session);
    return session;
  }

  /**
   * Heartbeating is negotiated in the CONNECTED frame, not decided unilaterally: the broker
   * advertises {@code heart-beat:sx,sy} - it beats every sx ms and wants a beat every sy ms - and
   * the client arms its dead-connection watchdog from sx. A broker advertising 0 leaves the client
   * with no watchdog at all, which is how an idle connection cut by a load balancer stayed
   * unnoticed until the next message was already lost.
   *
   * <p>The interval is pinned to something other than 10s on purpose: {@code
   * SimpleBrokerMessageHandler.setTaskScheduler} silently enables a {@code {10000, 10000}}
   * heartbeat of its own whenever none was configured, so at the configured default of 10s this
   * test would pass just as happily with our heartbeat wiring deleted.
   */
  @Test
  void tracingEndpointShouldOfferHeartbeatsSoAnIdleConnectionIsNoticed() throws Exception {
    var connectedHeaders = new CompletableFuture<StompHeaders>();
    var stompClient = new WebSocketStompClient(new StandardWebSocketClient());
    stompClient.setMessageConverter(new StringMessageConverter());
    openedClients.add(stompClient);

    var session =
        stompClient
            .connectAsync(
                "ws://localhost:" + port + "/tracing",
                new WebSocketHttpHeaders(),
                new StompHeaders(),
                new StompSessionHandlerAdapter() {
                  @Override
                  public void afterConnected(StompSession session, StompHeaders headers) {
                    connectedHeaders.complete(headers);
                  }
                })
            .get(5, TimeUnit.SECONDS);
    openedSessions.add(session);

    var expectedInterval = Duration.ofSeconds(Long.parseLong(HEARTBEAT_SECONDS)).toMillis();

    assertThat(connectedHeaders.get(5, TimeUnit.SECONDS).getHeartbeat())
        .as("the configured heartbeat has to reach the wire, in both directions")
        .containsExactly(expectedInterval, expectedInterval);
  }

  static Stream<Arguments> crossOriginTransports() {
    return Stream.of(
        Arguments.of(
            "SockJS",
            new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))),
            "http"),
        Arguments.of("NativeWebSocket", new StandardWebSocketClient(), "ws"));
  }

  /**
   * The CORS wildcard validation bug fixed by switching from setAllowedOrigins("*") to
   * setAllowedOriginPatterns("*"): with the old setAllowedOrigins("*"), Spring rejects cross-origin
   * WebSocket connections when allowCredentials is implied (SockJS).
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("crossOriginTransports")
  void stompConnectionWithCrossOrigin_shouldSucceed(
      String displayName, WebSocketClient wsClient, String scheme) throws Exception {
    var headers = new WebSocketHttpHeaders();
    headers.setOrigin("http://some-other-origin.example.com");

    var session =
        openSession(
            wsClient,
            new JacksonJsonMessageConverter(),
            scheme + "://localhost:" + port + "/tracing",
            headers);

    assertThat(session.isConnected()).isTrue();
  }

  /**
   * A traced message is sent as one metadata frame on /topic/traces plus n data frames on
   * /topic/data. If the broker delivers them out of order, the receiving proxy sees data without
   * metadata (or the other way round) and keeps the message half-assembled - so send order has to
   * survive the broker, across both topics.
   */
  @Test
  void framesOfBothTracingTopics_shouldArriveInSendOrder() throws Exception {
    final int burstSize = 200;
    var received = new LinkedBlockingQueue<String>();

    var session =
        openSession(
            new StandardWebSocketClient(),
            new StringMessageConverter(),
            "ws://localhost:" + port + "/tracing",
            new WebSocketHttpHeaders());
    session.subscribe(TigerRemoteProxyClient.WS_TRACING, collectingHandler(received));
    session.subscribe(TigerRemoteProxyClient.WS_DATA, collectingHandler(received));
    awaitSubscriptionsAreActive(received);

    var expected =
        IntStream.range(0, burstSize)
            .boxed()
            .flatMap(i -> Stream.of("trace-" + i, "data-" + i))
            .toList();
    for (int i = 0; i < burstSize; i++) {
      template.convertAndSend(TigerRemoteProxyClient.WS_TRACING, "trace-" + i);
      template.convertAndSend(TigerRemoteProxyClient.WS_DATA, "data-" + i);
    }

    await()
        .atMost(Duration.ofSeconds(30))
        .until(() -> payloadsWithoutProbes(received).size() >= expected.size());
    assertThat(payloadsWithoutProbes(received)).containsExactlyElementsOf(expected);
  }

  @Test
  void burstWithSlowConsumer_shouldNotCloseSession() throws Exception {
    final int burstSize = 100;
    final int payloadSize = 40 * 1024;
    final String payload = "x".repeat(payloadSize);
    var received = new LinkedBlockingQueue<String>();

    WebSocketContainer container = ContainerProvider.getWebSocketContainer();
    container.setDefaultMaxTextMessageBufferSize(payloadSize * 2);
    var session =
        openSession(
            new StandardWebSocketClient(container),
            new StringMessageConverter(),
            "ws://localhost:" + port + "/tracing",
            new WebSocketHttpHeaders());
    session.subscribe(TigerRemoteProxyClient.WS_TRACING, collectingHandler(received));
    session.subscribe(TigerRemoteProxyClient.WS_DATA, slowCollectingHandler(received));
    awaitSubscriptionsAreActive(received);

    for (int i = 0; i < burstSize; i++) {
      template.convertAndSend(TigerRemoteProxyClient.WS_DATA, payload + i);
    }

    await()
        .atMost(Duration.ofSeconds(30))
        .until(() -> payloadsWithoutProbes(received).size() >= burstSize);
    assertThat(session.isConnected())
        .as("session must survive a burst even when the subscriber lags behind")
        .isTrue();
  }

  /**
   * SUBSCRIBE is processed asynchronously, so anything sent before the broker has registered the
   * subscription is dropped without a trace. Probing until a marker comes back is the only reliable
   * way to know it is there. Each topic needs its own marker - counting markers in aggregate is
   * already satisfied by two polls on a single live subscription.
   */
  private void awaitSubscriptionsAreActive(LinkedBlockingQueue<String> received) {
    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(50))
        .until(
            () -> {
              template.convertAndSend(TigerRemoteProxyClient.WS_TRACING, TRACES_PROBE);
              template.convertAndSend(TigerRemoteProxyClient.WS_DATA, DATA_PROBE);
              return received.contains(TRACES_PROBE) && received.contains(DATA_PROBE);
            });
  }

  private static List<String> payloadsWithoutProbes(LinkedBlockingQueue<String> received) {
    var snapshot = new ArrayList<>(received);
    return snapshot.stream()
        .filter(payload -> !TRACES_PROBE.equals(payload) && !DATA_PROBE.equals(payload))
        .toList();
  }

  private static StompFrameHandler collectingHandler(LinkedBlockingQueue<String> target) {
    return new StompFrameHandler() {
      @Override
      public Type getPayloadType(StompHeaders headers) {
        return String.class;
      }

      @Override
      public void handleFrame(StompHeaders headers, Object payload) {
        target.add((String) payload);
      }
    };
  }

  private static StompFrameHandler slowCollectingHandler(LinkedBlockingQueue<String> target) {
    return new StompFrameHandler() {
      @Override
      public Type getPayloadType(StompHeaders headers) {
        return String.class;
      }

      @Override
      @SneakyThrows(InterruptedException.class)
      public void handleFrame(StompHeaders headers, Object payload) {
        Thread.sleep(2);
        target.add((String) payload);
      }
    };
  }
}
