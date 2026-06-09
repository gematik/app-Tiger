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
 * ******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.test.tiger.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.gematik.rbellogger.facets.websocket.RbelWebsocketHandshakeFacet;
import de.gematik.rbellogger.facets.websocket.RbelWebsocketMessageFacet;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.ForwardProxyInfo;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerConfigurationRoute;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.proxy.client.TigerRemoteProxyClient;
import de.gematik.test.tiger.proxy.data.TigerProxyRoute;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.awaitility.core.ConditionTimeoutException;
import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.server.WebSocketServer;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.ImagePullPolicy;
import org.testcontainers.utility.DockerImageName;

@Slf4j
@ResetTigerConfiguration
class TestWebsocketDockerProxy {
  private static final int DOCKER_INTERNAL_PROXY_PORT = 8080;
  private static final int DOCKER_INTERNAL_ADMIN_PORT = 8081;
  private static final String DOCKER_IMAGE_PROPERTY = "tigerProxy.docker.image";
  // Use the image tag that CI builds. Can be overridden via system property.
  private static final String DEFAULT_DOCKER_IMAGE = "tiger/tiger-proxy:latest";

  @Test
  void dockerProxyInMiddle_shouldCaptureWebsocketMessages() throws Exception {
    // Skip test if Docker is not available (common in CI environments without Docker daemon)
    if (!isDockerAvailable()) {
      log.warn("Docker not available, skipping WebSocket Docker proxy test");
      return;
    }

    CountDownLatch latch = new CountDownLatch(2);
    int backendPort = TigerGlobalConfiguration.readIntegerOptional("free.port.4").orElseThrow();
    WebSocketServer wsServer =
        new WebSocketServer(new InetSocketAddress("localhost", backendPort)) {
          @Override
          public void onOpen(WebSocket webSocket, ClientHandshake clientHandshake) {
            webSocket.send("hello from ws backend");
          }

          @Override
          public void onMessage(WebSocket conn, String message) {
            conn.send("echo: " + message);
          }

          @Override
          public void onClose(WebSocket conn, int code, String reason, boolean remote) {
            // not needed for this test
          }

          @Override
          public void onError(WebSocket conn, Exception ex) {
            log.error("WS backend error", ex);
          }

          @Override
          public void onStart() {
            // not needed for this test
          }
        };
    wsServer.start();

    Testcontainers.exposeHostPorts(backendPort);

    try (val dockerProxy = startDockerProxy();
        val remoteClient =
            new TigerRemoteProxyClient(
                dockerAdminUrl(dockerProxy),
                TigerProxyConfiguration.builder()
                    .activateRbelParsingFor(List.of("websocket"))
                    .proxyLogLevel("WARN")
                    .build())) {
      remoteClient.connect();
      await().atMost(20, TimeUnit.SECONDS).until(remoteClient::isConnected);

      remoteClient.addRoute(
          TigerProxyRoute.builder()
              .from("http://host.testcontainers.internal:" + backendPort)
              .to("http://host.testcontainers.internal:" + backendPort)
              .build());

      try (val localProxy =
          new TigerProxy(
              TigerProxyConfiguration.builder()
                  .name("local-proxy")
                  .activateRbelParsingFor(List.of("websocket"))
                  .proxyRoutes(
                      List.of(
                          TigerConfigurationRoute.builder()
                              .from("/")
                              .to("http://host.testcontainers.internal:" + backendPort)
                              .build()))
                  .forwardToProxy(
                      ForwardProxyInfo.builder()
                          .hostname(dockerProxy.getHost())
                          .port(dockerProxy.getMappedPort(DOCKER_INTERNAL_PROXY_PORT))
                          .build())
                  .build())) {

        WebSocketClient client =
            new WebSocketClient(new URI("ws://localhost:" + localProxy.getProxyPort())) {
              @Override
              public void onOpen(ServerHandshake handshake) {
                // not needed for this test
              }

              @Override
              public void onMessage(String message) {
                if (!message.startsWith("echo:")) {
                  send("ping");
                }
                latch.countDown();
              }

              @Override
              public void onClose(int code, String reason, boolean remote) {
                // not needed for this test
              }

              @Override
              public void onError(Exception ex) {
                log.error("WS client error", ex);
              }
            };

        assertThat(client.connectBlocking(10, TimeUnit.SECONDS)).isTrue();
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        client.close();

        awaitWebsocketTraffic(remoteClient);
        awaitWebsocketTraffic(localProxy);

        assertThat(localProxy.getRbelMessagesList())
            .anyMatch(el -> el.hasFacet(RbelWebsocketHandshakeFacet.class))
            .anyMatch(el -> el.hasFacet(RbelWebsocketMessageFacet.class));
        assertThat(remoteClient.getRbelMessagesList())
            .anyMatch(el -> el.hasFacet(RbelWebsocketHandshakeFacet.class))
            .anyMatch(el -> el.hasFacet(RbelWebsocketMessageFacet.class));
      }
    } finally {
      wsServer.stop();
    }
  }

  private static void awaitWebsocketTraffic(AbstractTigerProxy proxy) {
    try {
      await()
          .atMost(20, TimeUnit.SECONDS)
          .until(
              () ->
                  proxy.getRbelMessagesList().stream()
                          .anyMatch(el -> el.hasFacet(RbelWebsocketHandshakeFacet.class))
                      && proxy.getRbelMessagesList().stream()
                          .anyMatch(el -> el.hasFacet(RbelWebsocketMessageFacet.class)));
    } catch (ConditionTimeoutException e) {
      log.error("Timed out waiting for websocket traffic; dumping RBEL trees");
      proxy
          .getRbelMessagesList()
          .forEach(msg -> log.error("Message {}: {}", msg.getUuid(), msg.printTreeStructure()));
      throw e;
    }
  }

  private static GenericContainer<?> startDockerProxy() {
    String imageName = System.getProperty(DOCKER_IMAGE_PROPERTY, DEFAULT_DOCKER_IMAGE);
    DockerImageName dockerImage = DockerImageName.parse(imageName);
    ImagePullPolicy neverPullPolicy = imageNameToCheck -> false;

    val proxyContainer = new GenericContainer<>(dockerImage);
    proxyContainer
        .withImagePullPolicy(neverPullPolicy)
        .withLogConsumer(new Slf4jLogConsumer(log).withPrefix("docker-proxy"))
        .withEnv("TIGERPROXY_PROXYPORT", String.valueOf(DOCKER_INTERNAL_PROXY_PORT))
        .withEnv("TIGERPROXY_ADMINPORT", String.valueOf(DOCKER_INTERNAL_ADMIN_PORT))
        .withEnv("MANAGEMENT_SERVER_PORT", "")
        .withExposedPorts(DOCKER_INTERNAL_PROXY_PORT, DOCKER_INTERNAL_ADMIN_PORT)
        .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(60)));
    proxyContainer.start();
    return proxyContainer;
  }

  private static String dockerAdminUrl(GenericContainer<?> dockerProxy) {
    return "http://"
        + dockerProxy.getHost()
        + ":"
        + dockerProxy.getMappedPort(DOCKER_INTERNAL_ADMIN_PORT);
  }

  private static boolean isDockerAvailable() {
    try {
      // Try to get the Docker client - this will fail if Docker is not available
      DockerClientFactory.instance().client();
      return true;
    } catch (Exception e) {
      log.debug("Docker is not available ({}), test will be skipped", e.getMessage());
      return false;
    }
  }
}
