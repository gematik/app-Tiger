/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.gematik.rbellogger.converter.RbelConverterPlugin;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.facet.RbelFacet;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Slf4j
@ResetTigerConfiguration
class AsynchronousNonHttpCommunicationTest extends AbstractNonHttpTest {
  private static final String MESSAGE = "Client to Server message\n";
  private static final String RESPONSE_MESSAGE = "Server to Client message\n";

  /**
   * Client -> TigerProxy -> listenerServer
   *
   * <p>One clientSocket is created to send messages to the tigerProxy Test sends two messages over
   * this client socket.
   *
   * <p>TigerProxy receives the messages and sends them further to the listenerServer. It uses the
   * NettyHttpClient. Here a new channel is open to the listenerServer (the channel should be reused
   * )
   *
   * <p>The listener server starts a serverSocket and wait for connections.
   *
   * <p>We would expect that the tigerProxy makes only one connection to the listener server.
   */
  @Test
  @DisplayName("Client sends two non-http request, no reply from server, connection stays open")
  void sendNonHttpTrafficWithoutResponseFromServer() throws Exception {
    executeTestRun(
        socket -> {
          writeSingleRequestMessage(socket);
          // seems like requests being neartime concurrently cause havoc sometimes
          // (at least locally when run N times)
          await().pollDelay(200, TimeUnit.MILLISECONDS).until(() -> true);
          writeSingleRequestMessage(socket);
        },
        serverSocket -> {},
        (requestCalls, responseCalls, serverConnectionsOpened) -> {
          assertThat(requestCalls.get()).isEqualTo(2);
          assertThat(responseCalls.get()).isZero();
          assertThat(serverConnectionsOpened.get()).isEqualTo(1);
        });
  }

  @Test
  @DisplayName(
      "Client sends two non-http request, no reply from server, connection closed between each"
          + " client message")
  void sendNonHttpTrafficWithoutResponseAndWithSocketCloseBetweenEachMessage() throws Exception {
    executeTestRun(
        socket -> {
          writeSingleRequestMessage(socket);
          socket.close();
          try (Socket clientSocket = newClientSocketTo(getTigerProxy(), true)) {
            writeSingleRequestMessage(clientSocket);
          }
        },
        serverSocket -> {},
        (requestCalls, responseCalls, serverConnectionsOpened) -> {
          assertThat(serverConnectionsOpened).hasValue(2);
          assertThat(responseCalls).hasValue(0);
          assertThat(requestCalls).hasValue(2);
        },
        (element, converter) -> element.addFacet(new RbelFacetExpectingReplyMessage()));
  }

  @Test
  @DisplayName(
      "Client sends two non-http request, one reply per request from server, connection stays open")
  void sendNonHttpTrafficWithResponseFromServer() throws Exception {
    executeTestRun(
        socket -> {
          writeSingleRequestMessage(socket);
          readSingleResponseMessage(socket);
          writeSingleRequestMessage(socket);
          readSingleResponseMessage(socket);
        },
        serverSocket -> {
          serverSocket.getOutputStream().write(RESPONSE_MESSAGE.getBytes(StandardCharsets.UTF_8));
          serverSocket.getOutputStream().flush();
        },
        (requestCalls, responseCalls, serverConnectionsOpened) -> {
          assertThat(requestCalls).hasValue(2);
          assertThat(responseCalls).hasValue(2);
          assertThat(serverConnectionsOpened).hasValue(1);
        },
        (element, converter) -> element.addFacet(new RbelFacetExpectingReplyMessage()));
  }

  @Test
  @DisplayName(
      "Client sends two non-http request, two replies per request from server, connection stays"
          + " open")
  @Disabled(
      "NettyHttpClient closes connection immediately after flush from server, fails the test. "
          + "A major rewrite would be necessary in the mockserver to support this")
  void sendNonHttpTrafficWithMultipleResponsesFromServer() throws Exception {
    executeTestRun(
        socket -> {
          writeSingleRequestMessage(socket);
          readSingleResponseMessage(socket);
          readSingleResponseMessage(socket);
          writeSingleRequestMessage(socket);
          readSingleResponseMessage(socket);
          readSingleResponseMessage(socket);
        },
        serverSocket -> {
          serverSocket.getOutputStream().write(RESPONSE_MESSAGE.getBytes(StandardCharsets.UTF_8));
          serverSocket.getOutputStream().flush();
          serverSocket.getOutputStream().write(RESPONSE_MESSAGE.getBytes(StandardCharsets.UTF_8));
          serverSocket.getOutputStream().flush();
        },
        (requestCalls, responseCalls, serverCalled) -> {
          assertThat(requestCalls).hasValue(2);
          assertThat(responseCalls).hasValue(2);
          assertThat(serverCalled).hasValue(2);
        });
  }

  @Test
  @DisplayName(
      "Client sends one non-http request, multiple replies with flush server, connection stays"
          + " open")
  @Disabled("see above")
  void sendNonHttpTrafficWithMultipleResponsesFromServerForOnlyASingleRequest() throws Exception {
    AtomicInteger serverResponsesRead = new AtomicInteger(0);
    executeTestRun(
        socket -> {
          writeSingleRequestMessage(socket);
          readSingleResponseMessage(socket);
          serverResponsesRead.incrementAndGet();
          readSingleResponseMessage(socket);
          serverResponsesRead.incrementAndGet();
          readSingleResponseMessage(socket);
          serverResponsesRead.incrementAndGet();
          readSingleResponseMessage(socket);
          serverResponsesRead.incrementAndGet();
        },
        serverSocket -> {
          serverSocket.setTcpNoDelay(true);
          serverSocket.getOutputStream().write(RESPONSE_MESSAGE.getBytes(StandardCharsets.UTF_8));
          serverSocket.getOutputStream().flush();
          serverSocket.getOutputStream().write(RESPONSE_MESSAGE.getBytes(StandardCharsets.UTF_8));
          serverSocket.getOutputStream().flush();
          serverSocket.getOutputStream().write(RESPONSE_MESSAGE.getBytes(StandardCharsets.UTF_8));
          serverSocket.getOutputStream().flush();
          serverSocket.getOutputStream().write(RESPONSE_MESSAGE.getBytes(StandardCharsets.UTF_8));
          serverSocket.getOutputStream().flush();
        },
        (requestCalls, responseCalls, serverCalled) -> {
          assertThat(serverResponsesRead).hasValueGreaterThanOrEqualTo(4);
          assertThat(requestCalls).hasValue(1);
          assertThat(responseCalls).hasValue(1);
          assertThat(serverCalled).hasValue(1);
        });
  }

  private static void writeSingleRequestMessage(Socket socket) throws IOException {
    writeSingleRequestMessage(socket, MESSAGE.getBytes(StandardCharsets.UTF_8));
  }

  private static void readSingleResponseMessage(Socket socket) throws IOException {
    readSingleResponseMessage(socket, RESPONSE_MESSAGE.getBytes(StandardCharsets.UTF_8));
  }

  public void executeTestRun(
      ThrowingConsumer<Socket> clientActionCallback,
      ThrowingConsumer<Socket> serverActionCallback,
      VerifyInteractionsConsumer interactionsVerificationCallback,
      RbelConverterPlugin... postConversionListener)
      throws Exception {
    executeTestRunWithDirectReverseProxy(
        clientActionCallback,
        interactionsVerificationCallback,
        serverSocket -> {
          BufferedReader reader =
              new BufferedReader(new InputStreamReader(serverSocket.getInputStream()));
          while (!serverSocket.isClosed()) {
            final String serverArrivedMessage = reader.readLine();
            log.debug("listener server: read message ({})", serverArrivedMessage);
            if (serverArrivedMessage != null) {
              serverActionCallback.accept(serverSocket);
              assertThat(serverArrivedMessage + "\n").isEqualTo(MESSAGE);
            }
          }
        },
        postConversionListener);
  }

  private static class RbelFacetExpectingReplyMessage implements RbelFacet {
    @Override
    public RbelMultiMap<RbelElement> getChildElements() {
      return new RbelMultiMap<>();
    }

    @Override
    public boolean shouldExpectReplyMessage() {
      return true;
    }
  }
}
