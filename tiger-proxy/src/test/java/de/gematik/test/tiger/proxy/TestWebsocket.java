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
import static de.gematik.test.tiger.proxy.AbstractTigerProxyTest.awaitMessagesInTigerProxy;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelConverter;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.facets.websocket.RbelWebsocketConverter;
import de.gematik.rbellogger.facets.websocket.RbelWebsocketHandshakeConverter;
import de.gematik.rbellogger.facets.websocket.RbelWebsocketHandshakeFacet;
import de.gematik.rbellogger.facets.websocket.RbelWebsocketMessageFacet;
import de.gematik.rbellogger.util.RbelSocketAddress;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerConfigurationRoute;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.proxy.client.TigerRemoteProxyClient;
import de.gematik.test.tiger.proxy.data.TigerProxyRoute;
import java.io.File;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.io.FileUtils;
import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.server.WebSocketServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ResetTigerConfiguration
class TestWebsocket {

  @Autowired private TigerProxy tigerProxy;

  private final RbelWebsocketHandshakeConverter handshakeConverter =
      new RbelWebsocketHandshakeConverter();
  private final RbelWebsocketConverter websocketConverter = new RbelWebsocketConverter();

  private void addWebsocketConverters(RbelConverter rbelConverter) {
    rbelConverter.addConverter(handshakeConverter);
    rbelConverter.addConverter(websocketConverter);
  }

  private void removeWebsocketConverters(RbelConverter converter) {
    converter.getConverterPlugins().remove(handshakeConverter);
    converter.getConverterPlugins().remove(websocketConverter);
  }

  @BeforeEach
  void init() {
    tigerProxy.clearAllMessages();
    tigerProxy.clearAllRoutes();
    addWebsocketConverters(tigerProxy.getRbelLogger().getRbelConverter());
  }

  @AfterEach
  void tearDown() {
    removeWebsocketConverters(tigerProxy.getRbelLogger().getRbelConverter());
  }

  @SneakyThrows
  @Test
  void connectToTigerProxyViaAnotherTigerProxy() {
    try (val proxyingTigerProxy =
        new TigerProxy(
            TigerProxyConfiguration.builder()
                .activateRbelParsingFor(List.of("websocket"))
                .proxyRoutes(
                    List.of(
                        TigerConfigurationRoute.builder()
                            .from("/")
                            .to("http://localhost:" + tigerProxy.getAdminPort())
                            .build()))
                .build())) {
      tigerProxy.addRoute(
          TigerProxyRoute.builder()
              .from("/")
              .to("http://localhost:" + tigerProxy.getAdminPort())
              .build());
      val remoteClient =
          new TigerRemoteProxyClient("http://127.0.0.1:" + proxyingTigerProxy.getProxyPort());
      addWebsocketConverters(remoteClient.getRbelLogger().getRbelConverter());
      remoteClient.connect();
      generateTraffic();

      awaitMessagesInTigerProxy(proxyingTigerProxy, 8);
      assertThat(proxyingTigerProxy.getRbelMessagesList().get(4))
          .hasFacet(RbelWebsocketHandshakeFacet.class);
      assertThat(proxyingTigerProxy.getRbelMessagesList().get(5))
          .hasFacet(RbelWebsocketHandshakeFacet.class);
      assertThat(proxyingTigerProxy.getRbelMessagesList().get(7))
          .extractChildWithPath("$.payload")
          .asString()
          .startsWith("[\"CONNECT\\nheart-beat:0,0");
    } finally {
      tigerProxy.clearAllRoutes();
    }
  }

  @Test
  @SneakyThrows
  void connectToANonSocksWebsocketServer() {
    CountDownLatch latch = new CountDownLatch(2);

    final Integer serverPort =
        TigerGlobalConfiguration.readIntegerOptional("free.port.0").orElseThrow();
    WebSocketServer server =
        new WebSocketServer(new InetSocketAddress("localhost", serverPort)) {
          @Override
          public void onOpen(WebSocket webSocket, ClientHandshake clientHandshake) {
            webSocket.send("hello from server");
          }

          @Override
          public void onMessage(WebSocket conn, String message) {
            log.info("Received message in server: " + message);
            conn.send("echo: " + message);
          }

          public void onClose(WebSocket conn, int code, String reason, boolean remote) {}

          public void onError(WebSocket conn, Exception ex) {}

          public void onStart() {}
        };
    server.start();
    tigerProxy.addRoute(
        TigerProxyRoute.builder().from("/").to("http://localhost:" + serverPort).build());

    WebSocketClient client =
        new WebSocketClient(new URI("ws://localhost:" + tigerProxy.getProxyPort())) {
          public void onOpen(ServerHandshake handshake) {}

          @Override
          public void onMessage(String message) {
            log.info("Received message: " + message);
            if (!message.startsWith("echo:")) {
              this.send("right back at ya");
            }
            latch.countDown();
          }

          public void onClose(int code, String reason, boolean remote) {}

          public void onError(Exception ex) {}
        };

    client.connectBlocking(10, TimeUnit.SECONDS); // wait for handshake

    assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
    tigerProxy.waitForAllCurrentMessagesToBeParsed();

    client.close();
    awaitMessagesInTigerProxy(tigerProxy, 6);
    server.stop();
    awaitMessagesInTigerProxy(tigerProxy, 7);

    assertThat(tigerProxy.getRbelMessagesList()).hasSize(7);
    assertThat(tigerProxy.getRbelMessagesList().get(0))
        .hasStringContentEqualToAtPosition("$.method", "GET")
        .hasStringContentEqualToAtPosition("$.path", "/");
    assertThat(tigerProxy.getRbelMessagesList().get(2))
        .hasFacet(RbelWebsocketMessageFacet.class)
        .hasStringContentEqualToAtPosition("$.payload", "hello from server");
    assertThat(tigerProxy.getRbelMessagesList().get(3))
        .hasStringContentEqualToAtPosition("$.payload", "right back at ya");
    assertThat(tigerProxy.getRbelMessagesList().get(4))
        .hasStringContentEqualToAtPosition("$.payload", "echo: right back at ya");
  }

  @SneakyThrows
  private void generateTraffic() {
    tigerProxy
        .getRbelLogger()
        .getRbelConverter()
        .parseMessage(
            FileUtils.readFileToByteArray(new File("src/test/resources/messages/getRequest.curl")),
            new RbelMessageMetadata()
                .withSender(RbelSocketAddress.create("localhost", 8080))
                .withReceiver(RbelSocketAddress.create("localhost", 8080)));
  }
}
