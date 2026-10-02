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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.proxy.data.TigerProxyRoute;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Lifecycle of a WebSocket tunnelled through the proxy.
 *
 * <p>Once a connection has been upgraded it is a tunnel, not a reusable HTTP connection: its bytes
 * only mean anything on the socket the handshake happened on, and the two halves live and die
 * together. Getting this wrong left clients holding a socket whose far side was gone, and made the
 * proxy replay WebSocket frames onto freshly dialled connections that had never seen an {@code
 * Upgrade}.
 */
@Slf4j
@SpringBootTest(
    classes = TigerProxyApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ResetTigerConfiguration
class WebSocketTunnelLifecycleTest {

  private static final byte[] MASKED_PING_FRAME = {
    (byte) 0x81,
    (byte) 0x84,
    0x12,
    0x34,
    0x56,
    0x78,
    (byte) ('p' ^ 0x12),
    (byte) ('i' ^ 0x34),
    (byte) ('n' ^ 0x56),
    (byte) ('g' ^ 0x78)
  };
  private static final byte[] HELLO_FRAME = {(byte) 0x81, 0x05, 'h', 'e', 'l', 'l', 'o'};

  /** A text block normalises its line terminators to \n, so HTTP's CRLF is restored afterwards. */
  private static final String HANDSHAKE_RESPONSE =
      """
      HTTP/1.1 101 Switching Protocols
      Upgrade: websocket
      Connection: Upgrade
      Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=

      """
          .replace("\n", "\r\n");

  @Autowired private TigerProxy tigerProxy;

  private FakeWebSocketBackend backend;

  @BeforeEach
  void setUpProxyAndBackend() {
    tigerProxy.clearAllRoutes();
    tigerProxy.clearAllMessages();
    backend = new FakeWebSocketBackend();
    tigerProxy.addRoute(
        TigerProxyRoute.builder()
            .from("/")
            .to("http://localhost:" + backend.getPort())
            .matchForProxyType(false)
            .build());
  }

  @AfterEach
  void tearDownBackend() {
    backend.close();
  }

  /**
   * A backend closing an idle tunnel (nginx does this after 60s by default) has to reach the
   * client. Leaving the client half ESTABLISHED means it keeps writing into a tunnel with no far
   * side and only notices when its own request times out.
   */
  @Test
  @SneakyThrows
  void backendClosingTheTunnelMustReachTheClient() {
    try (val client = openTunnel()) {
      backend.closeCurrentConnection();

      assertThat(readUntilEofOrTimeout(client.getInputStream(), 5000))
          .withFailMessage("Client was left holding a half-open tunnel")
          .isEqualTo(EOF);
    }
  }

  /**
   * The bytes of an established tunnel cannot be replayed on a new connection - there is no
   * handshake to repeat them after. Dialling a fresh connection writes a raw WebSocket frame onto a
   * socket the backend is still reading as HTTP, which it answers with "400 Bad Request", and that
   * HTML error page then arrives inside the client's WebSocket.
   */
  @Test
  @SneakyThrows
  void clientFrameAfterTheBackendClosedMustNotOpenANewConnection() {
    try (val client = openTunnel()) {
      backend.closeCurrentConnection();
      await().atMost(5, TimeUnit.SECONDS).until(() -> backend.getLastConnectionClosed() != null);

      client.getOutputStream().write(MASKED_PING_FRAME);
      client.getOutputStream().flush();

      // a negative assertion: the backend must stay untouched for a while, not merely right now
      await()
          .during(Duration.ofSeconds(1))
          .atMost(Duration.ofSeconds(5))
          .untilAsserted(
              () -> {
                assertThat(backend.getConnectionCount())
                    .withFailMessage(
                        "Proxy re-dialled the backend after the tunnel was gone; it received %s",
                        backend.getBytesOnLaterConnections())
                    .isEqualTo(1);
                assertThat(backend.getBytesOnLaterConnections()).isEmpty();
              });
    }
  }

  /** The other direction: a departing client must not leave its backend connection behind. */
  @Test
  @SneakyThrows
  void clientClosingTheTunnelMustReachTheBackend() {
    val client = openTunnel();
    client.close();

    await()
        .alias("Backend connection was left behind after the client went away")
        .atMost(5, TimeUnit.SECONDS)
        .until(() -> backend.getLastConnectionClosed() != null);
  }

  /**
   * The pool's reclamation must not reach a tunnel. {@code lastUsedAt} only advances when the pool
   * hands a channel out, which tunnelled traffic never does, so a busy tunnel looks permanently
   * idle - without detaching it on upgrade, the TTL sweep closes live connections.
   */
  @Test
  @SneakyThrows
  void connectionPoolCleanupMustNotReachATunnel() {
    try (val client = openTunnel()) {
      val channelMap =
          tigerProxy
              .getMockServer()
              .getActionHandler()
              .getHttpClient()
              .getClientBootstrapFactory()
              .getChannelMap();
      channelMap.setChannelPoolTtlMillis(0);
      channelMap.cleanupExpiredChannels();

      assertThat(readUntilEofOrTimeout(client.getInputStream(), 2000))
          .withFailMessage("The connection pool closed a live tunnel")
          .isEqualTo(TIMED_OUT);
      assertThat(backend.getLastConnectionClosed()).isNull();
    }
  }

  private static final String EOF = "EOF";
  private static final String TIMED_OUT = "TIMED_OUT";

  /**
   * Opens a client socket, upgrades it through the proxy and returns it once the tunnel carries.
   */
  @SneakyThrows
  private Socket openTunnel() {
    val client = new Socket("localhost", tigerProxy.getProxyPort());
    client.setSoTimeout(10_000);
    client
        .getOutputStream()
        .write(
            ("GET /ws HTTP/1.1\r\nHost: localhost:"
                    + tigerProxy.getProxyPort()
                    + "\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                    + "Sec-WebSocket-Version: 13\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
    client.getOutputStream().flush();

    assertThat(readHttpHeader(client.getInputStream())).contains("101");
    assertThat(client.getInputStream().readNBytes(HELLO_FRAME.length)).isEqualTo(HELLO_FRAME);
    return client;
  }

  /**
   * Reads exactly up to the end of the HTTP header, leaving whatever follows on the socket. Reading
   * into a buffer instead would swallow a frame that shares the header's segment.
   */
  @SneakyThrows
  private static String readHttpHeader(InputStream in) {
    val header = new StringBuilder();
    while (!header.toString().endsWith("\r\n\r\n")) {
      val read = in.read();
      if (read < 0) {
        break;
      }
      header.append((char) read);
    }
    return header.toString();
  }

  @SneakyThrows
  private static String readUntilEofOrTimeout(InputStream in, int timeoutMillis) {
    // the caller owns the socket, so restoring the timeout is its business
    try {
      val read = in.read(new byte[4096]);
      return read < 0 ? EOF : "DATA";
    } catch (SocketTimeoutException e) {
      return TIMED_OUT;
    } catch (java.net.SocketException e) {
      // a reset is a close as far as the client is concerned
      return EOF;
    }
  }

  /** A backend that completes one WebSocket handshake and then records what else it is sent. */
  @Slf4j
  private static class FakeWebSocketBackend implements AutoCloseable {
    private final ServerSocket serverSocket;
    private final AtomicInteger connectionCount = new AtomicInteger();
    private final List<String> bytesOnLaterConnections = new CopyOnWriteArrayList<>();
    private volatile Socket currentConnection;
    private volatile CountDownLatch connectionClosed = new CountDownLatch(1);

    @SneakyThrows
    FakeWebSocketBackend() {
      serverSocket = new ServerSocket(0);
      val acceptor = new Thread(this::acceptLoop, "fake-ws-backend");
      acceptor.setDaemon(true);
      acceptor.start();
    }

    int getPort() {
      return serverSocket.getLocalPort();
    }

    int getConnectionCount() {
      return connectionCount.get();
    }

    List<String> getBytesOnLaterConnections() {
      return bytesOnLaterConnections;
    }

    /** Non-null once the first connection has ended, however it ended. */
    Boolean getLastConnectionClosed() {
      return connectionClosed.getCount() == 0 ? Boolean.TRUE : null;
    }

    @SneakyThrows
    void closeCurrentConnection() {
      currentConnection.close();
    }

    private void acceptLoop() {
      try {
        while (!serverSocket.isClosed()) {
          val socket = serverSocket.accept();
          val nr = connectionCount.incrementAndGet();
          val handler = new Thread(() -> serve(socket, nr), "fake-ws-backend-" + nr);
          handler.setDaemon(true);
          handler.start();
        }
      } catch (Exception e) {
        log.debug("backend acceptor stopped", e);
      }
    }

    private void serve(Socket socket, int connectionNr) {
      try (socket) {
        val in = socket.getInputStream();
        val out = socket.getOutputStream();
        val buffer = new byte[8192];
        val read = in.read(buffer);
        if (read < 0) {
          return;
        }
        if (connectionNr == 1) {
          currentConnection = socket;
          completeHandshake(out);
          // hold the connection open until somebody closes it
          while (in.read(buffer) >= 0) {
            // tunnel traffic, ignored
          }
        } else {
          bytesOnLaterConnections.add(new String(buffer, 0, read, StandardCharsets.UTF_8));
          write(out, "HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\n\r\n");
        }
      } catch (Exception e) {
        log.debug("backend connection {} ended", connectionNr, e);
      } finally {
        if (connectionNr == 1) {
          connectionClosed.countDown();
        }
      }
    }

    private static void completeHandshake(OutputStream out) {
      // the client reads the header and the frame separately, so they may share a segment
      write(out, HANDSHAKE_RESPONSE);
      write(out, HELLO_FRAME);
    }

    @SneakyThrows
    private static void write(OutputStream out, String text) {
      write(out, text.getBytes(StandardCharsets.UTF_8));
    }

    @SneakyThrows
    private static void write(OutputStream out, byte[] bytes) {
      out.write(bytes);
      out.flush();
    }

    @Override
    @SneakyThrows
    public void close() {
      serverSocket.close();
    }
  }
}
