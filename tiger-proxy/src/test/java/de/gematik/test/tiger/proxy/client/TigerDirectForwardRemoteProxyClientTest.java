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
package de.gematik.test.tiger.proxy.client;

import static de.gematik.rbellogger.data.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.proxy.AbstractNonHttpTest;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.TigerProxyApplication;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

@ResetTigerConfiguration
@Slf4j
class TigerDirectForwardRemoteProxyClientTest extends AbstractNonHttpTest {
  /*
   *  Our Testsetup:
   *
   *
   * ----------------     -----------------------    -----------------------------
   * | clientSocket |  -> | tigerProxy          | -> | remoteServer              |
   * |              |     | (SpringApplication) |    | (GenericRespondingServer) |
   * ----------------     -----------------------    -----------------------------
   *                                |
   *                             Tracing
   *                                |
   *                                V
   *                      tigerRemoteProxyClient
   */

  // the local client, receiving the traced message via mesh setup
  final AtomicReference<TigerRemoteProxyClient> tigerRemoteProxyClient = new AtomicReference<>();

  private static byte[] request = sendAsCetpMessage("{\"this\":\"is a message\"}".getBytes());
  private static byte[] response = sendAsCetpMessage("{\"this\":\"is a response\"}".getBytes());

  @Test
  void sendCetpMessageWithoutResponse() throws Exception {
    executeRemoteProxyTestWithMessagesAndVerification(
        socket -> writeSingleRequestMessage(socket, request),
        serverSocket -> {
          final BufferedReader reader =
              new BufferedReader(new InputStreamReader(serverSocket.getInputStream()));
          reader.readLine();
        },
        (requestCalls, responseCalls, serverCalled) -> {
          assertThat(tigerRemoteProxyClient.get().getRbelMessages()).hasSize(1);
          assertThat(tigerRemoteProxyClient.get().getRbelMessages().getFirst().getRawContent())
              .containsExactly(request);
          assertThat(serverCalled.get()).isEqualTo(1);
        },
        Object::toString);
  }

  @Test
  void sendSplitNonHttpMessageWithoutResponse() throws Exception {
    executeRemoteProxyTestWithMessagesAndVerification(
        socket -> {
          writeSingleRequestMessage(socket, "USER XYZ".getBytes());
          writeSingleRequestMessage(socket, "\r\n".getBytes());
          writeSingleRequestMessage(socket, "QUIT".getBytes());
          writeSingleRequestMessage(socket, "\r\n".getBytes());
        },
        serverSocket -> {
          final BufferedReader reader =
              new BufferedReader(new InputStreamReader(serverSocket.getInputStream()));
          reader.readLine();
        },
        (requestCalls, responseCalls, serverCalled) -> {
          assertThat(serverCalled.get()).isEqualTo(1);
          var messages = new ArrayList<>(tigerRemoteProxyClient.get().getRbelMessages());
          if (messages.size() > 2) {
            messages.forEach(
                message -> System.out.println("content: " + message.getRawStringContent()));
          }
          assertThat(messages).hasSize(2);
          assertThat(messages.get(0).getRawStringContent()).isEqualTo("USER XYZ\r\n");
          assertThat(messages.get(1).getRawStringContent()).isEqualTo("QUIT\r\n");
        },
        Object::toString);
  }

  @Test
  void sendNonHttpMessageWithResponse() throws Exception {
    executeRemoteProxyTestWithMessagesAndVerification(
        socket -> writeSingleRequestMessage(socket, request),
        serverSocket -> {
          serverSocket.getOutputStream().write(response);
          serverSocket.getOutputStream().flush();
        },
        (requestCalls, responseCalls, serverCalled) -> {
          assertThat(tigerRemoteProxyClient.get().getRbelMessages().getFirst())
              .hasStringContentEqualTo(new String(request));
          assertThat(tigerRemoteProxyClient.get().getRbelMessages().getLast())
              .hasStringContentEqualTo(new String(response));
        },
        Object::toString);
  }

  @Test
  void forwardMessagesToNonExistingServer_shouldReceiveException() throws Exception {
    executeRemoteProxyTestWithMessagesAndVerification(
        socket -> writeSingleRequestMessage(socket, request),
        serverSocket -> {},
        (requestCalls, responseCalls, serverCalled) -> {
          assertThat(tigerRemoteProxyClient.get().getRbelMessages()).hasSize(1);
          assertThat(tigerRemoteProxyClient.get().getRbelMessages().getFirst().getRawContent())
              .containsExactly(request);
          assertThat(tigerRemoteProxyClient.get().getReceivedRemoteExceptions()).hasSize(1);
        },
        port -> TigerGlobalConfiguration.readString("free.port.200"));
  }

  private void executeRemoteProxyTestWithMessagesAndVerification(
      ThrowingConsumer<Socket> clientActionCallback,
      ThrowingConsumer<Socket> serverAcceptedConnectionCallback,
      VerifyInteractionsConsumer verifyInteractionsConsumer,
      Function<Integer, String> serverPortMapper)
      throws Exception {
    AtomicReference<ConfigurableApplicationContext> tigerProxyApplication = new AtomicReference<>();

    try {
      executeTestRunWithTls(
          clientActionCallback,
          verifyInteractionsConsumer,
          serverAcceptedConnectionCallback,
          serverPort -> {
            tigerProxyApplication.set(
                new SpringApplicationBuilder()
                    .bannerMode(Mode.OFF)
                    .properties(
                        Map.of(
                            "tigerProxy.name",
                            "tigerProxyApplication",
                            "tigerProxy.activateRbelParsingFor",
                            "pop3",
                            "tigerProxy.directReverseProxy.hostname",
                            "127.0.0.1",
                            "tigerProxy.directReverseProxy.port",
                            serverPortMapper.apply(serverPort),
                            "server.port",
                            TigerGlobalConfiguration.readString("free.port.100")))
                    .sources(TigerProxyApplication.class)
                    .web(WebApplicationType.SERVLET)
                    .initializers()
                    .run());
            final TigerProxy tigerProxy =
                tigerProxyApplication.get().getBean("tigerProxy", TigerProxy.class);
            tigerRemoteProxyClient.set(
                new TigerRemoteProxyClient(
                    "http://localhost:" + tigerProxy.getAdminPort(),
                    TigerProxyConfiguration.builder()
                        .name("tigerRemoteProxyClient")
                        .proxyLogLevel("WARN")
                        .activateRbelParsingFor(List.of("pop3", "smtp", "mime"))
                        .build()));
            tigerRemoteProxyClient.get().connect();
            await().until(tigerRemoteProxyClient.get()::isConnected);
            return tigerProxy;
          });
    } finally {
      Optional.ofNullable(tigerProxyApplication.get())
          .ifPresent(ConfigurableApplicationContext::close);
    }
  }

  private static byte @NotNull [] sendAsCetpMessage(byte[] message) {
    return Bytes.concat("CETP".getBytes(), Ints.toByteArray(message.length), message);
  }
}
