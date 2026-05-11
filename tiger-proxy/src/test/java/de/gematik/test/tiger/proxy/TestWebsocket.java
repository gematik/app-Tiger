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

import com.sun.net.httpserver.HttpServer;
import de.gematik.rbellogger.RbelConverter;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.facets.websocket.RbelWebsocketHandshakeFacet;
import de.gematik.rbellogger.facets.websocket.RbelWebsocketMessageFacet;
import de.gematik.rbellogger.util.RbelSocketAddress;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.ForwardProxyInfo;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerConfigurationRoute;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.proxy.client.TigerRemoteProxyClient;
import de.gematik.test.tiger.proxy.data.TigerProxyRoute;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;
import kong.unirest.core.Config;
import kong.unirest.core.UnirestInstance;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.io.FileUtils;
import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.server.DefaultSSLWebSocketServerFactory;
import org.java_websocket.server.WebSocketServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ResetTigerConfiguration
class TestWebsocket {

  @Autowired private TigerProxy tigerProxy;

  private void addWebsocketConverters(RbelConverter rbelConverter) {
    rbelConverter.reinitializeConverters(
        RbelConfiguration.builder().activateRbelParsingFor(List.of("websocket")).build());
  }

  @BeforeEach
  void init() {
    tigerProxy.clearAllMessages();
    tigerProxy.clearAllRoutes();
    addWebsocketConverters(tigerProxy.getRbelLogger().getRbelConverter());
  }

  @ValueSource(strings = {"/", "http://localhost:${free.port.0}/"})
  @SneakyThrows
  @ParameterizedTest
  void connectToTigerProxyViaAnotherTigerProxy(String from) {
    val resolvedFrom = TigerGlobalConfiguration.resolvePlaceholders(from);
    val uri = URI.create(resolvedFrom);
    val port = uri.getPort();
    val path = uri.getPath();

    try (val proxyingTigerProxy =
        new TigerProxy(
            TigerProxyConfiguration.builder()
                .proxyPort(port == -1 ? 0 : port)
                .activateRbelParsingFor(List.of("websocket"))
                .proxyRoutes(
                    List.of(
                        TigerConfigurationRoute.builder()
                            .from(path)
                            .to("http://localhost:" + tigerProxy.getAdminPort())
                            .build()))
                .build())) {
      tigerProxy.addRoute(
          TigerProxyRoute.builder()
              .from("/")
              .to("http://localhost:" + tigerProxy.getAdminPort())
              .build());
      try (val remoteClient =
          new TigerRemoteProxyClient("http://127.0.0.1:" + proxyingTigerProxy.getProxyPort())) {
        addWebsocketConverters(remoteClient.getRbelLogger().getRbelConverter());
        remoteClient.connect();
        generateTraffic();

        awaitMessagesInTigerProxy(proxyingTigerProxy, 8);
        assertThat(proxyingTigerProxy.getRbelMessagesList().get(4))
            .hasFacet(RbelWebsocketHandshakeFacet.class);
        assertThat(proxyingTigerProxy.getRbelMessagesList().get(5))
            .hasFacet(RbelWebsocketHandshakeFacet.class);
        val firstWebsocketMessage =
            proxyingTigerProxy.getRbelMessagesList().stream()
                .filter(el -> el.hasFacet(RbelWebsocketMessageFacet.class))
                .findFirst()
                .orElseThrow();
        assertThat(firstWebsocketMessage)
            .extractChildWithPath("$.payload")
            .asString()
            .startsWith("o");
      }
    } finally {
      tigerProxy.clearAllRoutes();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"/", "http://localhost:${free.port.1}"})
  @SneakyThrows
  void connectToANonSocksWebsocketServer(String from) {
    CountDownLatch latch = new CountDownLatch(2);

    final Integer serverPort =
        TigerGlobalConfiguration.readIntegerOptional("free.port.1").orElseThrow();
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
        TigerProxyRoute.builder()
            .from(TigerGlobalConfiguration.resolvePlaceholders(from))
            .to("http://localhost:" + serverPort)
            .matchForProxyType(false)
            .build());

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

  @Test
  @SneakyThrows
  void multiHopForwardProxy_withMachineHostname_shouldCorrectlyRouteToTarget() {
    String machineHostname = InetAddress.getLocalHost().getHostName();

    var backend = HttpServer.create(new InetSocketAddress(0), 0);
    backend.createContext(
        "/foobar",
        exchange -> {
          exchange.sendResponseHeaders(666, -1);
          exchange.close();
        });
    backend.start();
    int backendPort = backend.getAddress().getPort();

    try (val forwardProxy =
        new TigerProxy(
            TigerProxyConfiguration.builder()
                .name("forwardProxy")
                .proxyRoutes(
                    List.of(
                        TigerConfigurationRoute.builder()
                            .from("http://localhost:" + backendPort)
                            .to("http://localhost:" + backendPort)
                            .build()))
                .build())) {

      try (val reverseProxy =
          new TigerProxy(
              TigerProxyConfiguration.builder()
                  .name("reverseProxy")
                  .proxyRoutes(
                      List.of(
                          TigerConfigurationRoute.builder()
                              .from("/")
                              .to("http://localhost:" + backendPort)
                              .build()))
                  .forwardToProxy(
                      ForwardProxyInfo.builder()
                          .hostname("localhost")
                          .port(forwardProxy.getProxyPort())
                          .build())
                  .build())) {

        try (var unirest =
            new UnirestInstance(
                new Config()
                    .sslContext(reverseProxy.buildSslContext())
                    .connectTimeout(10_000)
                    .requestTimeout(10_000)
                    .verifySsl(false))) {

          var response =
              unirest
                  .get("https://" + machineHostname + ":" + reverseProxy.getProxyPort() + "/foobar")
                  .asString();

          assertThat(response.getStatus()).isEqualTo(666);
        }
      }
    } finally {
      backend.stop(0);
    }
  }

  @Test
  @SneakyThrows
  void multiHopForwardProxy_withWebSocket_shouldCorrectlyRouteToTarget() {
    String machineHostname = InetAddress.getLocalHost().getHostName();
    CountDownLatch latch = new CountDownLatch(2);

    final Integer wsServerPort =
        TigerGlobalConfiguration.readIntegerOptional("free.port.2").orElseThrow();
    WebSocketServer wsServer =
        new WebSocketServer(new InetSocketAddress("localhost", wsServerPort)) {
          public void onOpen(WebSocket ws, ClientHandshake handshake) {
            ws.send("hello from ws backend");
          }

          public void onMessage(WebSocket conn, String message) {
            conn.send("echo: " + message);
          }

          public void onClose(WebSocket conn, int code, String reason, boolean remote) {
            // not needed for this test
          }

          public void onError(WebSocket conn, Exception ex) {
            // not needed for this test
          }

          public void onStart() {
            // not needed for this test
          }
        };
    wsServer.start();

    try (val forwardProxy =
        new TigerProxy(
            TigerProxyConfiguration.builder()
                .name("forwardProxy-ws")
                .proxyRoutes(
                    List.of(
                        TigerConfigurationRoute.builder()
                            .from("http://localhost:" + wsServerPort)
                            .to("http://localhost:" + wsServerPort)
                            .build()))
                .build())) {

      try (val reverseProxy =
          new TigerProxy(
              TigerProxyConfiguration.builder()
                  .name("reverseProxy-ws")
                  .proxyRoutes(
                      List.of(
                          TigerConfigurationRoute.builder()
                              .from("/")
                              .to("http://localhost:" + wsServerPort)
                              .build()))
                  .forwardToProxy(
                      ForwardProxyInfo.builder()
                          .hostname("localhost")
                          .port(forwardProxy.getProxyPort())
                          .build())
                  .build())) {

        WebSocketClient client =
            new WebSocketClient(
                new URI("ws://" + machineHostname + ":" + reverseProxy.getProxyPort())) {
              public void onOpen(ServerHandshake handshake) {
                // not needed for this test
              }

              public void onMessage(String message) {
                log.info("WS received: {}", message);
                if (!message.startsWith("echo:")) {
                  this.send("ping");
                }
                latch.countDown();
              }

              public void onClose(int code, String reason, boolean remote) {
                // not needed for this test
              }

              public void onError(Exception ex) {
                log.error("WS error", ex);
              }
            };

        assertThat(client.connectBlocking(10, TimeUnit.SECONDS))
            .as("WebSocket connection through multi-hop proxy chain should succeed")
            .isTrue();
        assertThat(latch.await(10, TimeUnit.SECONDS))
            .as("Should receive server greeting and echo response")
            .isTrue();

        client.close();
      }
    } finally {
      wsServer.stop();
    }
  }

  @Test
  @SneakyThrows
  void multiHopForwardProxy_withSecureWebSocket_shouldCorrectlyRouteToTarget() {
    String machineHostname = InetAddress.getLocalHost().getHostName();
    CountDownLatch greetingLatch = new CountDownLatch(1);
    CountDownLatch echoLatch = new CountDownLatch(3);

    SSLContext backendSslContext = buildBackendSslContext();

    final Integer wsServerPort =
        TigerGlobalConfiguration.readIntegerOptional("free.port.3").orElseThrow();
    WebSocketServer wsServer =
        new WebSocketServer(new InetSocketAddress("localhost", wsServerPort)) {
          public void onOpen(WebSocket ws, ClientHandshake handshake) {
            log.info("WSS backend: connection opened");
            ws.send("hello from wss backend");
          }

          public void onMessage(WebSocket conn, String message) {
            log.info("WSS backend received: {}", message);
            conn.send("echo: " + message);
          }

          public void onClose(WebSocket conn, int code, String reason, boolean remote) {
            log.info("WSS backend closed: code={} reason={} remote={}", code, reason, remote);
          }

          public void onError(WebSocket conn, Exception ex) {
            log.error("WSS backend error", ex);
          }

          public void onStart() {
            log.info("WSS backend started on port {}", wsServerPort);
          }
        };
    wsServer.setWebSocketFactory(new DefaultSSLWebSocketServerFactory(backendSslContext));
    wsServer.start();

    try (val forwardProxy =
        new TigerProxy(
            TigerProxyConfiguration.builder()
                .name("forwardProxy-wss")
                .proxyRoutes(
                    List.of(
                        TigerConfigurationRoute.builder()
                            .from("https://localhost:" + wsServerPort)
                            .to("https://localhost:" + wsServerPort)
                            .build()))
                .build())) {

      try (val reverseProxy =
          new TigerProxy(
              TigerProxyConfiguration.builder()
                  .name("reverseProxy-wss")
                  .proxyRoutes(
                      List.of(
                          TigerConfigurationRoute.builder()
                              .from("/")
                              .to("https://localhost:" + wsServerPort)
                              .build()))
                  .forwardToProxy(
                      ForwardProxyInfo.builder()
                          .hostname("localhost")
                          .port(forwardProxy.getProxyPort())
                          .build())
                  .build())) {

        WebSocketClient client =
            new WebSocketClient(
                new URI("wss://" + machineHostname + ":" + reverseProxy.getProxyPort())) {
              public void onOpen(ServerHandshake handshake) {
                log.info("WSS client: open");
              }

              public void onMessage(String message) {
                log.info("WSS client received: {}", message);
                if (message.startsWith("hello")) {
                  greetingLatch.countDown();
                  this.send("ping 1");
                } else if (message.startsWith("echo:")) {
                  echoLatch.countDown();
                  long remaining = echoLatch.getCount();
                  if (remaining > 0) {
                    this.send("ping " + (4 - remaining));
                  }
                }
              }

              public void onClose(int code, String reason, boolean remote) {
                log.info("WSS client closed: code={} reason={} remote={}", code, reason, remote);
              }

              public void onError(Exception ex) {
                log.error("WSS client error", ex);
              }

              @Override
              protected void onSetSSLParameters(javax.net.ssl.SSLParameters sslParameters) {
                sslParameters.setEndpointIdentificationAlgorithm(null);
                super.onSetSSLParameters(sslParameters);
              }
            };
        client.setSocketFactory(
            new NoHostnameVerificationSslSocketFactory(
                buildTrustAllSslContext().getSocketFactory()));

        assertThat(client.connectBlocking(10, TimeUnit.SECONDS))
            .as("WSS connection through multi-hop proxy chain should succeed")
            .isTrue();
        assertThat(greetingLatch.await(10, TimeUnit.SECONDS))
            .as("Should receive greeting from backend")
            .isTrue();
        assertThat(echoLatch.await(10, TimeUnit.SECONDS))
            .as(
                "Should receive multiple echo responses without the session breaking due"
                    + " to a spurious second CONNECT")
            .isTrue();

        client.close();
      }
    } finally {
      wsServer.stop();
    }
  }

  private static SSLContext buildBackendSslContext() throws Exception {
    KeyStore ks = KeyStore.getInstance("JKS");
    try (var in =
        new File("src/test/resources/eccStoreWithChain.jks").exists()
            ? new FileInputStream(new File("src/test/resources/eccStoreWithChain.jks"))
            : TestWebsocket.class.getResourceAsStream("/eccStoreWithChain.jks")) {
      ks.load(in, "gematik".toCharArray());
    }
    KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    kmf.init(ks, "gematik".toCharArray());
    SSLContext ctx = SSLContext.getInstance("TLS");
    ctx.init(kmf.getKeyManagers(), new javax.net.ssl.TrustManager[] {trustAllManager()}, null);
    return ctx;
  }

  private static SSLContext buildTrustAllSslContext() throws Exception {
    // Force Sun JSSE: BouncyCastle JSSE enforces hostname verification even when
    // endpointIdentificationAlgorithm is null, which breaks connections to a dynamic MITM cert.
    SSLContext ctx = SSLContext.getInstance("TLS", "SunJSSE");
    ctx.init(null, new TrustManager[] {trustAllManager()}, null);
    return ctx;
  }

  private static X509TrustManager trustAllManager() {
    return new X509ExtendedTrustManager() {
      public void checkClientTrusted(X509Certificate[] chain, String authType) {}

      public void checkServerTrusted(X509Certificate[] chain, String authType) {}

      public void checkClientTrusted(
          X509Certificate[] chain, String authType, java.net.Socket socket) {}

      public void checkServerTrusted(
          X509Certificate[] chain, String authType, java.net.Socket socket) {}

      public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {}

      public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {}

      public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
      }
    };
  }

  /** Wraps an SSLSocketFactory and disables hostname verification on every created socket. */
  private static class NoHostnameVerificationSslSocketFactory extends SSLSocketFactory {
    private final SSLSocketFactory delegate;

    NoHostnameVerificationSslSocketFactory(SSLSocketFactory delegate) {
      this.delegate = delegate;
    }

    private Socket configure(java.net.Socket s) {
      if (s instanceof SSLSocket sslSocket) {
        SSLParameters params = sslSocket.getSSLParameters();
        params.setEndpointIdentificationAlgorithm(null);
        sslSocket.setSSLParameters(params);
      }
      return s;
    }

    public String[] getDefaultCipherSuites() {
      return delegate.getDefaultCipherSuites();
    }

    public String[] getSupportedCipherSuites() {
      return delegate.getSupportedCipherSuites();
    }

    public Socket createSocket(Socket s, String host, int port, boolean autoClose)
        throws IOException {
      return configure(delegate.createSocket(s, host, port, autoClose));
    }

    public java.net.Socket createSocket(String host, int port) throws java.io.IOException {
      return configure(delegate.createSocket(host, port));
    }

    public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
        throws IOException {
      return configure(delegate.createSocket(host, port, localHost, localPort));
    }

    public Socket createSocket(java.net.InetAddress host, int port) throws IOException {
      return configure(delegate.createSocket(host, port));
    }

    public Socket createSocket(
        InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
      return configure(delegate.createSocket(address, port, localAddress, localPort));
    }

    public Socket createSocket() throws IOException {
      return configure(delegate.createSocket());
    }
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
