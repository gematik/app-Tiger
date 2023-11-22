/*
 * Copyright (c) 2023 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.gematik.test.tiger.config.ResetTigerConfiguration;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Slf4j
@ResetTigerConfiguration
class TestCetpCommunication extends AbstractNonHttpTest {

  private byte[] message;

  @BeforeEach
  public void setupFixture() throws IOException {
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
    executeTestRun(
        socket -> {
          writeSingleRequestMessage(socket);
          await()
              .atMost(5, TimeUnit.SECONDS)
              .pollInterval(100, TimeUnit.MILLISECONDS)
              .until(() -> !getTigerProxy().getRbelMessages().isEmpty());
          writeSingleRequestMessage(socket);
        },
        (requestCalls, responseCalls, serverCalled) -> {
          assertThat(getTigerProxy().getRbelMessages()).hasSize(2);
          assertThat(getTigerProxy().getRbelMessages().getFirst().getRawContent())
              .isEqualTo(message);
          assertThat(getTigerProxy().getRbelMessages().getLast().getRawContent())
              .isEqualTo(message);
        });
  }

  @Test
  @DisplayName(
      "Client sends two non-http request, no reply from server, connection closed between each"
          + " client message")
  void sendNonHttpTrafficWithoutResponseAndWithSocketCloseBetweenEachMessage() throws Exception {
    executeTestRun(
        socket -> {
          socket.close();
          try (Socket clientSocket = newClientSocketTo(getTigerProxy())) {
            writeSingleRequestMessage(clientSocket);
          }
          await()
              .atMost(5, TimeUnit.SECONDS)
              .pollInterval(100, TimeUnit.MILLISECONDS)
              .until(() -> getTigerProxy().getRbelMessages().size() >= 1);
          try (Socket clientSocket = newClientSocketTo(getTigerProxy())) {
            writeSingleRequestMessage(clientSocket);
          }
        },
        (requestCalls, responseCalls, serverCalled) -> {
          assertThat(getTigerProxy().getRbelMessages()).hasSize(2);
          assertThat(getTigerProxy().getRbelMessages().getFirst().getRawContent())
              .isEqualTo(message);
          assertThat(getTigerProxy().getRbelMessages().getLast().getRawContent())
              .isEqualTo(message);
        });
  }

  private void writeSingleRequestMessage(Socket socket) throws IOException {
    writeSingleRequestMessage(socket, message);
  }

  public void executeTestRun(
      ThrowingConsumer<Socket> clientActionCallback,
      VerifyInteractionsConsumer interactionsVerificationCallback)
      throws Exception {
    executeTestRun(
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
