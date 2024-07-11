/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.test.tiger.ByteArrayToStringRepresentation;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Slf4j
@ResetTigerConfiguration
class TestCetpCommunication extends AbstractNonHttpTest {

  private static byte[] message;

  @BeforeAll
  public static void setupFixture() throws IOException {
    String xml = Files.readString(Path.of("pom.xml"));
    message =
        Arrays.concatenate(
            Arrays.concatenate(
                "CETP".getBytes(), ByteBuffer.allocate(4).putInt(xml.length()).array()),
            xml.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("Client sends two non-http request, no reply from server, connection stays open")
  void sendNonHttpTrafficWithoutResponseFromServer() throws Exception {
    executeTestRunWithDirectReverseProxy(
        socket -> {
          writeSingleRequestMessage(socket);
          TigerProxyTestHelper.waitUntilMessageListInProxyContainsCountMessages(getTigerProxy(), 1);
          writeSingleRequestMessage(socket);
          TigerProxyTestHelper.waitUntilMessageListInProxyContainsCountMessages(getTigerProxy(), 2);
        },
        (requestCalls, responseCalls, serverCalled) -> {
          assertThat(getTigerProxy().getRbelMessages().getFirst().getRawContent())
              .withRepresentation(new ByteArrayToStringRepresentation())
              .isEqualTo(message);
          assertThat(getTigerProxy().getRbelMessages().getLast().getRawContent())
              .withRepresentation(new ByteArrayToStringRepresentation())
              .isEqualTo(message);
        });
  }

  @Test
  @DisplayName(
      "Client sends two non-http request, no reply from server, connection closed between each"
          + " client message")
  void sendNonHttpTrafficWithoutResponseAndWithSocketCloseBetweenEachMessage() throws Exception {
    log.info("RUNNING sendNonHttpTrafficWithoutResponseAndWithSocketCloseBetweenEachMessage");
    executeTestRunWithDirectReverseProxy(
        socket -> {
          socket.close();
          try (Socket clientSocket = newClientSocketTo(getTigerProxy(), true)) {
            writeSingleRequestMessage(clientSocket);
          }
          TigerProxyTestHelper.waitUntilMessageListInProxyContainsCountMessagesWithTimeout(
              getTigerProxy(), 1, 10);
          try (Socket clientSocket = newClientSocketTo(getTigerProxy(), true)) {
            writeSingleRequestMessage(clientSocket);
            TigerProxyTestHelper.waitUntilMessageListInProxyContainsCountMessagesWithTimeout(
                getTigerProxy(), 2, 10);
          }
        },
        (requestCalls, responseCalls, serverCalled) -> {
          assertThat(getTigerProxy().getRbelMessages().getFirst().getRawContent())
              .withRepresentation(new ByteArrayToStringRepresentation())
              .isEqualTo(message);
          assertThat(getTigerProxy().getRbelMessages().getLast().getRawContent())
              .withRepresentation(new ByteArrayToStringRepresentation())
              .isEqualTo(message);
        });
  }

  private void writeSingleRequestMessage(Socket socket) throws IOException {
    writeSingleRequestMessage(socket, message);
  }

  public void executeTestRunWithDirectReverseProxy(
      ThrowingConsumer<Socket> clientActionCallback,
      VerifyInteractionsConsumer interactionsVerificationCallback)
      throws Exception {
    executeTestRunWithDirectReverseProxy(
        clientActionCallback,
        interactionsVerificationCallback,
        serverSocket -> {
          final BufferedReader reader =
              new BufferedReader(new InputStreamReader(serverSocket.getInputStream()));
          reader.readLine();
          log.info("listener server: read message");
        });
  }
}
