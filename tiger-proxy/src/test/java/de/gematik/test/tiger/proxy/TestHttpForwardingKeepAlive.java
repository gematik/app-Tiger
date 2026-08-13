/*
 *  Copyright 2021-2025 gematik GmbH
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
import static org.mockito.Mockito.*;

import de.gematik.rbellogger.util.RbelInternetAddressParser;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerConfigurationRoute;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.mockserver.httpclient.BinaryBridgeHandler;
import de.gematik.test.tiger.mockserver.httpclient.NettyHttpClient;
import de.gematik.test.tiger.mockserver.mock.action.http.HttpActionHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.Attribute;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

/**
 * Regression test for TGR-2201 / TGR-1930: When Tiger forwards HTTP requests to a backend, the
 * outgoing request must carry a {@code Connection: keep-alive} header so backends that require an
 * explicit signal keep the TCP connection open between requests. TGR-1930 accidentally removed this
 * header, forcing a new TCP+TLS handshake per forwarded request (~50 ms overhead each).
 */
@Slf4j
@TestInstance(Lifecycle.PER_CLASS)
@ResetTigerConfiguration
class TestHttpForwardingKeepAlive extends AbstractTigerProxyTest {

  @Test
  void twoConsecutiveRequestsShouldReuseSameBackendTcpConnection() throws Exception {
    try (var backend = new KeepAliveAwareBackend()) {
      backend.start();

      spawnTigerProxyWith(
          TigerProxyConfiguration.builder()
              .proxyRoutes(
                  List.of(
                      TigerConfigurationRoute.builder()
                          .from("/")
                          .to("http://127.0.0.1:" + backend.getPort())
                          .build()))
              .build());

      // Two consecutive requests through the proxy.
      unirestInstance.get("http://localhost:" + tigerProxy.getProxyPort() + "/").asString();
      unirestInstance.get("http://localhost:" + tigerProxy.getProxyPort() + "/").asString();

      await()
          .atMost(java.time.Duration.ofSeconds(5))
          .until(() -> backend.requestsHandled.get() >= 2);

      assertThat(backend.acceptedConnections.get())
          .as(
              "Backend should have accepted a single TCP connection for two consecutive HTTP"
                  + " requests forwarded through the proxy. If the outgoing request lacks an"
                  + " explicit 'Connection: keep-alive' header, the backend closes the connection"
                  + " after each response, forcing a new TCP handshake per request (~50 ms).")
          .isEqualTo(1);
    }
  }

  /**
   * Regression test for TGR-2201: DNS resolution must be cached. Verifies that calling {@code
   * HttpActionHandler.getRemoteAddress()} twice with the same non-loopback remote socket only
   * triggers one DNS resolution.
   */
  @Test
  void getRemoteAddress_calledTwiceForSameHost_shouldResolveDnsOnlyOnce() {
    HttpActionHandler.clearResolvedHostCache();

    // Mock a Netty ChannelHandlerContext returning an unresolved, non-loopback remote socket
    var ctx = mock(ChannelHandlerContext.class);
    var channel = mock(Channel.class);
    when(ctx.channel()).thenReturn(channel);
    var unresolvedRemote =
        InetSocketAddress.createUnresolved("some.non.loopback.host.invalid", 12345);
    var remoteSocketAttr = mock(Attribute.class);
    when(remoteSocketAttr.get()).thenReturn(unresolvedRemote);
    when(channel.attr(NettyHttpClient.REMOTE_SOCKET)).thenReturn(remoteSocketAttr);
    var outgoingChannelAttr = mock(Attribute.class);
    when(outgoingChannelAttr.get()).thenReturn(null);
    when(channel.attr(BinaryBridgeHandler.OUTGOING_CHANNEL)).thenReturn(outgoingChannelAttr);
    when(channel.localAddress()).thenReturn(new InetSocketAddress("0.0.0.0", 0));

    try (var parserMock = mockStatic(RbelInternetAddressParser.class, CALLS_REAL_METHODS)) {
      HttpActionHandler.getRemoteAddress(ctx);
      HttpActionHandler.getRemoteAddress(ctx);
      HttpActionHandler.getRemoteAddress(ctx);

      // Verify DNS parser was only invoked once thanks to the cache
      parserMock.verify(
          () -> RbelInternetAddressParser.parseInetAddress("some.non.loopback.host.invalid"),
          times(1));
    }
  }

  /**
   * Minimal HTTP/1.1 backend that keeps the TCP connection open only when the client sent an
   * explicit {@code Connection: keep-alive} header. When the header is missing, the backend closes
   * the socket after the response (mimicking a strict backend / the reporter's setup).
   */
  static class KeepAliveAwareBackend implements AutoCloseable {

    private final ServerSocket serverSocket;
    final AtomicInteger acceptedConnections = new AtomicInteger();
    final AtomicInteger requestsHandled = new AtomicInteger();
    private Thread acceptThread;

    KeepAliveAwareBackend() throws Exception {
      this.serverSocket = new ServerSocket(0);
    }

    int getPort() {
      return serverSocket.getLocalPort();
    }

    void start() {
      acceptThread =
          new Thread(
              () -> {
                while (!serverSocket.isClosed()) {
                  try {
                    Socket socket = serverSocket.accept();
                    acceptedConnections.incrementAndGet();
                    new Thread(() -> handleConnection(socket)).start();
                  } catch (Exception e) {
                    return;
                  }
                }
              });
      acceptThread.setDaemon(true);
      acceptThread.start();
    }

    private void handleConnection(Socket socket) {
      try (socket;
          var reader =
              new BufferedReader(
                  new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
          OutputStream out = socket.getOutputStream()) {
        while (!socket.isClosed()) {
          Boolean keepAlive = readRequestAndDetectKeepAlive(reader);
          if (keepAlive == null) {
            return; // client closed the socket
          }
          writeResponse(out, keepAlive);
          requestsHandled.incrementAndGet();
          if (!keepAlive) {
            return;
          }
        }
      } catch (Exception e) {
        log.debug("backend connection ended: {}", e.getMessage());
      }
    }

    /**
     * Reads a single HTTP request from the socket. Returns {@code true} if it contained a {@code
     * Connection: keep-alive} header, {@code false} otherwise, or {@code null} if the peer closed
     * the connection before sending a full request.
     */
    private Boolean readRequestAndDetectKeepAlive(BufferedReader reader) throws Exception {
      boolean keepAlive = false;
      boolean sawAnything = false;
      String line;
      while ((line = reader.readLine()) != null) {
        sawAnything = true;
        if (line.isEmpty()) {
          return keepAlive;
        }
        if (line.toLowerCase().startsWith("connection:")
            && line.toLowerCase().contains("keep-alive")) {
          keepAlive = true;
        }
      }
      return sawAnything ? keepAlive : null;
    }

    private void writeResponse(OutputStream out, boolean keepAlive) {
      var body = "OK";
      var writer = new PrintWriter(out);
      writer.print("HTTP/1.1 200 OK\r\n");
      writer.print("Content-Length: " + body.length() + "\r\n");
      writer.print("Connection: " + (keepAlive ? "keep-alive" : "close") + "\r\n");
      writer.print("\r\n");
      writer.print(body);
      writer.flush();
    }

    @Override
    public void close() throws Exception {
      if (!serverSocket.isClosed()) {
        serverSocket.close();
      }
      if (acceptThread != null) {
        acceptThread.interrupt();
      }
    }
  }
}
