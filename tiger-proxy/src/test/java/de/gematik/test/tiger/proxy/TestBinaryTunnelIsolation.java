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
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.test.tiger.proxy;

import static de.gematik.test.tiger.mockserver.netty.proxy.relay.RelayConnectHandler.PROXIED;
import static de.gematik.test.tiger.mockserver.netty.proxy.relay.RelayConnectHandler.PROXIED_RESPONSE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

/**
 * Binary traffic tunnelled through the forward proxy via the {@code PROXIED_} handshake reaches
 * {@code BinaryHandler} without a pre-established outgoing channel, so it has to create one through
 * the shared channel pool. Such a tunnel is a 1:1 bridge and must never be poolable: it is reusable
 * the instant it is created (no response is awaited) and {@code BinaryBridgeHandler} forwards raw
 * bytes to whatever {@code INCOMING_CHANNEL} currently points at.
 */
@Slf4j
@TestInstance(Lifecycle.PER_CLASS)
@ResetTigerConfiguration
class TestBinaryTunnelIsolation extends AbstractTigerProxyTest {

  private static final int TIMEOUT_MILLIS = (int) Duration.ofSeconds(10).toMillis();

  @Test
  void twoProxiedBinaryTunnelsToSameBackend_shouldNotShareOutgoingChannel() throws IOException {
    try (ServerSocket backendServer = new ServerSocket(0)) {
      backendServer.setSoTimeout(TIMEOUT_MILLIS);
      spawnTigerProxyWith(new TigerProxyConfiguration());

      final byte[] requestOne = "{'msg':'client one'}".getBytes(UTF_8);
      final byte[] requestTwo = "{'msg':'client two'}".getBytes(UTF_8);
      final byte[] responseOne = "{'msg':'response one'}".getBytes(UTF_8);
      final byte[] responseTwo = "{'msg':'response two'}".getBytes(UTF_8);

      try (Socket clientOne = openProxiedTunnel(backendServer.getLocalPort());
          Socket clientTwo = openProxiedTunnel(backendServer.getLocalPort())) {

        // first tunnel, fully established before the second one sends anything
        write(clientOne, requestOne);
        final Socket backendOne = accept(backendServer);
        assertThat(backendOne.getInputStream().readNBytes(requestOne.length)).isEqualTo(requestOne);

        // second tunnel to the very same backend address. While the first tunnel's channel was
        // pooled unbound, this reused it and no second backend connection was ever opened.
        write(clientTwo, requestTwo);
        final Socket backendTwo = accept(backendServer);
        assertThat(backendTwo.getInputStream().readNBytes(requestTwo.length)).isEqualTo(requestTwo);
        assertThat(backendTwo.getPort())
            .as("each tunnel must own a distinct outgoing channel")
            .isNotEqualTo(backendOne.getPort());

        // each answer has to travel back along its own tunnel instead of crossing over
        write(backendOne, responseOne);
        write(backendTwo, responseTwo);

        assertThat(clientOne.getInputStream().readNBytes(responseOne.length))
            .isEqualTo(responseOne);
        assertThat(clientTwo.getInputStream().readNBytes(responseTwo.length))
            .isEqualTo(responseTwo);
      }
    }
  }

  /**
   * Performs the {@code PROXIED_} handshake, which tells the port unification handler the remote
   * address without opening the outgoing connection yet.
   */
  private Socket openProxiedTunnel(int backendPort) throws IOException {
    final Socket client = new Socket("localhost", tigerProxy.getProxyPort());
    client.setSoTimeout(TIMEOUT_MILLIS);
    final String handshake = PROXIED + "localhost:" + backendPort;
    write(client, handshake.getBytes(UTF_8));
    final byte[] expectedAnswer = (PROXIED_RESPONSE + handshake).getBytes(UTF_8);
    assertThat(client.getInputStream().readNBytes(expectedAnswer.length)).isEqualTo(expectedAnswer);
    return client;
  }

  private Socket accept(ServerSocket backendServer) throws IOException {
    final Socket accepted = backendServer.accept();
    accepted.setSoTimeout(TIMEOUT_MILLIS);
    return accepted;
  }

  private void write(Socket socket, byte[] payload) throws IOException {
    socket.getOutputStream().write(payload);
    socket.getOutputStream().flush();
  }
}
