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
package de.gematik.test.tiger.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import de.gematik.test.tiger.config.ResetTigerConfiguration;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.server.WebSocketServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(Lifecycle.PER_CLASS)
@ResetTigerConfiguration
class TestConnectionPooling extends AbstractTigerProxyTest {

  @Autowired private TigerProxy tigerProxy;

  @Test
  void concurrentClientsToSameBackend_shouldNotMixChannels() throws Exception {
    CountDownLatch client1Done = new CountDownLatch(1);
    CountDownLatch client2Done = new CountDownLatch(1);

    tigerProxy.addRoute(
        de.gematik.test.tiger.proxy.data.TigerProxyRoute.builder()
            .from("/")
            .to("http://localhost:" + fakeBackendServerPort)
            .build());

    new Thread(
            () -> {
              assertDoesNotThrow(
                  () -> {
                    try (var unirestInstance = kong.unirest.core.Unirest.spawnInstance()) {
                      unirestInstance
                          .get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar")
                          .asString();
                      client1Done.countDown();
                    }
                  });
            })
        .start();

    new Thread(
            () -> {
              assertDoesNotThrow(
                  () -> {
                    try (var unirestInstance = kong.unirest.core.Unirest.spawnInstance()) {
                      unirestInstance
                          .get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar")
                          .asString();
                      client2Done.countDown();
                    }
                  });
            })
        .start();

    assertThat(client1Done.await(10, TimeUnit.SECONDS))
        .as("Client 1 should complete without hanging")
        .isTrue();
    assertThat(client2Done.await(10, TimeUnit.SECONDS))
        .as("Client 2 should complete without hanging")
        .isTrue();
  }

  @Test
  void websocketUpgrade_shouldGetDedicatedIsolatedChannel() throws Exception {
    final int wsServerPort = 19997;
    CountDownLatch wsHandshakeDone = new CountDownLatch(1);
    CountDownLatch wsMessageReceived = new CountDownLatch(1);

    WebSocketServer wsServer =
        new WebSocketServer(new InetSocketAddress("localhost", wsServerPort)) {
          @Override
          public void onOpen(WebSocket webSocket, ClientHandshake clientHandshake) {
            log.info("WebSocket connection opened");
            wsHandshakeDone.countDown();
            webSocket.send("ws-hello");
          }

          @Override
          public void onMessage(WebSocket conn, String message) {
            log.info("WebSocket message received: {}", message);
            wsMessageReceived.countDown();
          }

          @Override
          public void onClose(WebSocket conn, int code, String reason, boolean remote) {
            // do nothing
          }

          @Override
          public void onError(WebSocket conn, Exception ex) {
            log.error("WebSocket server error", ex);
          }

          @Override
          public void onStart() {
            // do nothing
          }
        };
    wsServer.start();

    try {
      CountDownLatch clientConnected = new CountDownLatch(1);
      WebSocketClient client =
          new WebSocketClient(new URI("ws://localhost:" + wsServerPort + "/")) {
            @Override
            public void onOpen(ServerHandshake handshake) {
              clientConnected.countDown();
            }

            @Override
            public void onMessage(String message) {
              log.info("Client received: {}", message);
              if ("ws-hello".equals(message)) {
                this.send("ws-response");
              }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
              // do nothing
            }

            @Override
            public void onError(Exception ex) {
              log.error("WebSocket client error", ex);
            }
          };

      client.connectBlocking(5, TimeUnit.SECONDS);
      assertThat(clientConnected.await(5, TimeUnit.SECONDS))
          .as("WebSocket client should connect to server")
          .isTrue();
      assertThat(wsHandshakeDone.await(5, TimeUnit.SECONDS))
          .as("WebSocket server should receive handshake")
          .isTrue();
      assertThat(wsMessageReceived.await(5, TimeUnit.SECONDS))
          .as("WebSocket server should receive message from client")
          .isTrue();
      client.closeBlocking();
    } finally {
      wsServer.stop();
    }
  }
}
