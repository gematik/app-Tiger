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

package de.gematik.test.tiger.testenvmgr.utils;

import static de.gematik.rbellogger.testutil.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.facets.websocket.RbelWebsocketMessageFacet;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerConfigurationRoute;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import java.lang.reflect.Type;
import java.net.Socket;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.*;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.Banner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@Slf4j
class StompOverWebSocketTest {

  @ParameterizedTest(name = "routeTarget={0}, connectionHost={1}, hostHeader={2}, secure={3}")
  @CsvSource({
    "localhost, localhost,, false",
    "127.0.0.1, 127.0.0.1, websockets.local, false",
    "localhost, localhost,, true",
    "127.0.0.1, 127.0.0.1, websockets.local, true"
  })
  @SneakyThrows
  @TigerTest(
      tigerYaml =
          """
          tigerProxy:
            proxyPort: ${free.port.0}
            tls.masterSecretsFile: masterSecrets.txt
          """,
      skipEnvironmentSetup = true)
  void testStompOverWebSocket(
      String routeTarget,
      String connectionHost,
      String hostHeader,
      boolean secure,
      TigerTestEnvMgr tigerTestEnvMgr) {
    int backendPort = TigerGlobalConfiguration.readIntegerOptional("free.port.1").orElseThrow();

    try (ConfigurableApplicationContext appContext =
        new SpringApplicationBuilder()
            .bannerMode(Banner.Mode.OFF)
            .properties(Map.of("server.port", backendPort))
            .sources(TestApplication.class)
            .run()) {
      await()
          .atMost(Duration.ofSeconds(10))
          .pollInterval(Duration.ofMillis(100))
          .until(appContext::isRunning);

      tigerTestEnvMgr.setUpEnvironment();

      var localProxy = tigerTestEnvMgr.getLocalTigerProxyOptional().orElseThrow();

      localProxy.addRoute(
          TigerConfigurationRoute.builder()
              .from("/")
              .to("http://" + routeTarget + ":" + backendPort)
              .matchForProxyType(false)
              .build());

      localProxy
          .getRbelLogger()
          .getRbelConverter()
          .reinitializeConverters(
              RbelConfiguration.builder().activateRbelParsingFor(List.of("websocket")).build());

      int actualProxyPort = localProxy.getProxyPort();

      String wsProtocol = secure ? "wss" : "ws";
      var proxyUrl = wsProtocol + "://" + connectionHost + ":" + actualProxyPort + "/websocket";

      var stompClient = createStompClient(secure);
      var webSocketHeaders = new WebSocketHttpHeaders();
      if (hostHeader != null) {
        webSocketHeaders.set("Host", hostHeader);
      }
      var stompSession =
          stompClient
              .connectAsync(
                  proxyUrl, webSocketHeaders, new StompHeaders(), new NoOpStompSessionHandler())
              .get(5, TimeUnit.SECONDS);

      CompletableFuture<String> result = new CompletableFuture<>();
      stompSession.subscribe("/topic/greetings", new CompletingStompFrameHandler(result));

      stompSession.send("/app/hello", "Tiger");

      assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo("Hello, Tiger!");

      localProxy.waitForAllCurrentMessagesToBeParsed();

      var messages = localProxy.getRbelMessagesList();

      var websocketMessages =
          messages.stream().filter(msg -> msg.hasFacet(RbelWebsocketMessageFacet.class)).toList();
      assertThat(websocketMessages).isNotEmpty();

      assertThat(websocketMessages.get(websocketMessages.size() - 1))
          .hasStringContentEqualToAtPosition("$.payload.body", "Hello, Tiger!");
    }
  }

  private static void tryDirectNonSecureWebsocketConnection(
      String connectionHost, int backendPort, WebSocketStompClient stompClient)
      throws InterruptedException, ExecutionException, TimeoutException {
    final String directUrl = "ws://" + connectionHost + ":" + backendPort + "/websocket";
    stompClient
        .connectAsync(directUrl, new NoOpStompSessionHandler())
        .get(5, TimeUnit.SECONDS)
        .disconnect();
  }

  private static X509ExtendedTrustManager trustAllManager() {
    return new X509ExtendedTrustManager() {
      @Override
      public void checkClientTrusted(X509Certificate[] chain, String authType) {}

      @Override
      public void checkServerTrusted(X509Certificate[] chain, String authType) {}

      @Override
      public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
      }

      @Override
      public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {}

      @Override
      public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {}

      @Override
      public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {}

      @Override
      public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {}
    };
  }

  @SneakyThrows
  private static WebSocketStompClient createStompClient(boolean secure) {
    StandardWebSocketClient webSocketClient = new StandardWebSocketClient();
    if (secure) {
      // For Tomcat's WebSocket client, we need to disable host verification
      // by providing a custom SSLContext with a permissive trust manager that skips hostname
      // verification (similar to Tyrus's SslEngineConfigurator.setHostVerificationEnabled(false))
      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, new TrustManager[] {trustAllManager()}, new SecureRandom());

      SSLContext.setDefault(sslContext);
      HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());

      // Spring passes this context into ClientEndpointConfig;
      // Tomcat 11 uses ClientEndpointConfig#getSSLContext() for WSS handshakes.
      webSocketClient.setSslContext(sslContext);

      webSocketClient
          .getUserProperties()
          .put("org.apache.tomcat.websocket.SSL_CONTEXT", sslContext);
    }

    var stompClient = new WebSocketStompClient(webSocketClient);
    stompClient.setMessageConverter(new StringMessageConverter());
    return stompClient;
  }

  static class NoOpStompSessionHandler extends StompSessionHandlerAdapter {}

  @RequiredArgsConstructor
  static class CompletingStompFrameHandler implements StompFrameHandler {
    private final CompletableFuture<String> resultFuture;

    @Override
    public Type getPayloadType(StompHeaders headers) {
      return String.class;
    }

    @Override
    public void handleFrame(StompHeaders headers, Object payload) {
      resultFuture.complete((String) payload);
    }
  }

  @SpringBootApplication
  @Import({
    StompOverWebSocketTest.WebSocketConfig.class,
    StompOverWebSocketTest.GreetingController.class
  })
  static class TestApplication {}

  @Configuration
  @EnableWebSocketMessageBroker
  static class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
      config.enableSimpleBroker("/topic");
      config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
      registry.addEndpoint("/websocket").setAllowedOrigins("*");
    }
  }

  @Controller
  static class GreetingController {
    @MessageMapping("/hello")
    @SendTo("/topic/greetings")
    public String greeting(String name) {
      return "Hello, " + name + "!";
    }
  }
}
