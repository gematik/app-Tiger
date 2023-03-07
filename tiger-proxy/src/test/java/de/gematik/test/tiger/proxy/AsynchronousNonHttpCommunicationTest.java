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
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Slf4j
@ResetTigerConfiguration
class AsynchronousNonHttpCommunicationTest extends AbstractNonHttpTest{

    private static final String MESSAGE = "Client to Server message\n";
    private static final String RESPONSE_MESSAGE = "Server to Client message\n";

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
            serverSocket -> {
            },
            (requestCalls, responseCalls, serverCalled) -> {
                assertThat(requestCalls.get()).isEqualTo(2);
                assertThat(responseCalls.get()).isEqualTo(0);
                assertThat(serverCalled.get()).isEqualTo(2);
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
            serverSocket -> {
            },
            (requestCalls, responseCalls, serverCalled) -> {
                assertThat(serverCalled).hasValue(2);
                assertThat(responseCalls).hasValue(0);
                assertThat(requestCalls).hasValue(2);
            }
        );
    }

    @Test
    @DisplayName("Client sends two non-http request, one reply per request from server, connection stays open")
    void sendNonHttpTrafficWithResponseFromServer() throws Exception {
        executeTestRun(
            socket -> {
                writeSingleRequestMessage(socket);
                readSingleResponseMessage(socket);
                writeSingleRequestMessage(socket);
                readSingleResponseMessage(socket);
            },
            serverSocket -> {
                serverSocket.getOutputStream().write(RESPONSE_MESSAGE.getBytes());
                serverSocket.getOutputStream().flush();
            },
            (requestCalls, responseCalls, serverCalled) -> {
                assertThat(requestCalls).hasValue(2);
                assertThat(responseCalls).hasValue(2);
                assertThat(serverCalled).hasValue(2);
            }
        );
    }

    @Test
    @DisplayName("Client sends two non-http request, two replies per request from server, connection stays open")
    @Disabled("NettyHttpClient closes connection immediately after flush from server, fails the test. "
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
                serverSocket.getOutputStream().write(RESPONSE_MESSAGE.getBytes());
                serverSocket.getOutputStream().flush();
                serverSocket.getOutputStream().write(RESPONSE_MESSAGE.getBytes());
                serverSocket.getOutputStream().flush();
            },
            (requestCalls, responseCalls, serverCalled) -> {
                assertThat(requestCalls).hasValue(2);
                assertThat(responseCalls).hasValue(2);
                assertThat(serverCalled).hasValue(2);
            }
        );
    }

    @Test
    @DisplayName("Client sends one non-http request, multiple replies with flush server, connection stays open")
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
                serverSocket.getOutputStream().write(RESPONSE_MESSAGE.getBytes());
                serverSocket.getOutputStream().flush();
                serverSocket.getOutputStream().write(RESPONSE_MESSAGE.getBytes());
                serverSocket.getOutputStream().flush();
                serverSocket.getOutputStream().write(RESPONSE_MESSAGE.getBytes());
                serverSocket.getOutputStream().flush();
                serverSocket.getOutputStream().write(RESPONSE_MESSAGE.getBytes());
                serverSocket.getOutputStream().flush();

            },
            (requestCalls, responseCalls, serverCalled) -> {
                assertThat(serverResponsesRead).hasValueGreaterThanOrEqualTo(4);
                assertThat(requestCalls).hasValue(1);
                assertThat(responseCalls).hasValue(1);
                assertThat(serverCalled).hasValue(1);
            }
        );
    }

    private static void writeSingleRequestMessage(Socket socket) throws IOException {
        writeSingleRequestMessage(socket, MESSAGE.getBytes(StandardCharsets.UTF_8));
    }

    private static void readSingleResponseMessage(Socket socket) throws IOException {
        readSingleResponseMessage(socket, RESPONSE_MESSAGE.getBytes(StandardCharsets.UTF_8));
    }

    public void executeTestRun(ThrowingConsumer<Socket> clientActionCallback,
        ThrowingConsumer<Socket> serverActionCallback,
        VerifyInteractionsConsumer interactionsVerificationCallback) throws Exception {
        executeTestRun(clientActionCallback, interactionsVerificationCallback,
            serverSocket -> {
                final BufferedReader reader = new BufferedReader(
                    new InputStreamReader(serverSocket.getInputStream()));
                final String serverArrivedMessage = reader.readLine();
                log.info("listener server: read message");
                serverActionCallback.accept(serverSocket);
                assertThat(serverArrivedMessage + "\n").isEqualTo(MESSAGE);
            });
    }
}
