/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Slf4j
@ResetTigerConfiguration
class TestCetpCommunication extends AbstractNonHttpTest{

    private byte[] message;

    @BeforeEach
    public void setupFixture() throws IOException {
        String xml = Files.readString(Path.of("pom.xml"));
        message = Arrays.concatenate(
            Arrays.concatenate("CETP".getBytes(), ByteBuffer.allocate(4).putInt(xml.length()).array()),
            xml.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Client sends two non-http request, no reply from server, connection stays open")
    void sendNonHttpTrafficWithoutResponseFromServer() throws Exception {
        executeTestRun(
            socket -> {
                writeSingleRequestMessage(socket);
                writeSingleRequestMessage(socket);
            },
            (requestCalls, responseCalls, serverCalled) -> {
                waitForCondition(() -> getTigerProxy().getRbelMessages().size() == 2,
                    () -> "Wait timed out. No tiger-messages found!");
                assertThat(getTigerProxy().getRbelMessages().getFirst().getRawContent())
                    .isEqualTo(message);
                assertThat(getTigerProxy().getRbelMessages().getLast().getRawContent())
                    .isEqualTo(message);
            }
        );
    }

    @Test
    @DisplayName("Client sends two non-http request, no reply from server, connection closed between each client message")
    void sendNonHttpTrafficWithoutResponseAndWithSocketCloseBetweenEachMessage() throws Exception {
        executeTestRun(
            socket -> {
                socket.close();
                try (Socket clientSocket = newClientSocketTo(getTigerProxy())) {
                    writeSingleRequestMessage(clientSocket);
                }
                try (Socket clientSocket = newClientSocketTo(getTigerProxy())) {
                    writeSingleRequestMessage(clientSocket);
                }
            },
            (requestCalls, responseCalls, serverCalled) -> {
                waitForCondition(() -> getTigerProxy().getRbelMessages().size() == 2,
                    () -> "Wait timed out. No tiger-messages found!");
                assertThat(getTigerProxy().getRbelMessages().getFirst().getRawContent())
                    .isEqualTo(message);
                assertThat(getTigerProxy().getRbelMessages().getLast().getRawContent())
                    .isEqualTo(message);
            }
        );
    }

    private void writeSingleRequestMessage(Socket socket) throws IOException {
        writeSingleRequestMessage(socket, message);
    }

    public void executeTestRun(ThrowingConsumer<Socket> clientActionCallback,
        VerifyInteractionsConsumer interactionsVerificationCallback) throws Exception {
        executeTestRun(clientActionCallback, interactionsVerificationCallback,
            serverSocket -> {
                final BufferedReader reader = new BufferedReader(
                    new InputStreamReader(serverSocket.getInputStream()));
                reader.readLine();
                log.info("listener server: read message");
            });
    }
}
