/*
 * Copyright 2024 gematik GmbH
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
 */

package de.gematik.test.tiger.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.gematik.test.tiger.common.data.config.tigerproxy.*;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.proxy.data.TigerConnectionStatus;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import uk.org.webcompere.systemstubs.ThrowingRunnable;

@Slf4j
@TestInstance(Lifecycle.PER_CLASS)
@ResetTigerConfiguration
class TestTigerProxyConnections extends AbstractFastTigerProxyTest {

  @Test
  void testConnectionCount() {
    tigerProxy.addRoute(
        TigerConfigurationRoute.builder()
            .from("http://backend")
            .to("http://localhost:" + fakeBackendServerPort)
            .build());

    assertThat(tigerProxy.getOpenConnections()).isEmpty();
    withOpenTcpConnection(() -> assertThat(tigerProxy.getOpenConnections()).hasSize(1));
    await()
        .catchUncaughtExceptions()
        .untilAsserted(() -> assertThat(tigerProxy.getOpenConnections()).isEmpty());

    assertThat(tigerProxy.getOpenConnections()).isEmpty();
    withOpenTcpConnection(
        () -> {
          assertThat(tigerProxy.getOpenConnections()).hasSize(1);
          withOpenTcpConnection(() -> assertThat(tigerProxy.getOpenConnections()).hasSize(2));
        });
    await()
        .catchUncaughtExceptions()
        .untilAsserted(() -> assertThat(tigerProxy.getOpenConnections()).isEmpty());
  }

  @Test
  void testConnectionCountWithTls() {
    tigerProxy.addRoute(
        TigerConfigurationRoute.builder()
            .from("http://backend")
            .to("http://localhost:" + fakeBackendServerPort)
            .build());

    log.info("TigerProxyPort: {}", tigerProxy.getProxyPort());

    assertThat(tigerProxy.getOpenConnections()).isEmpty();
    withOpenTlsConnection(
        () -> assertThat(tigerProxy.getOpenConnections(TigerConnectionStatus.OPEN_TLS)).hasSize(1));
    await()
        .catchUncaughtExceptions()
        .untilAsserted(() -> assertThat(tigerProxy.getOpenConnections()).isEmpty());

    assertThat(tigerProxy.getOpenConnections()).isEmpty();
    withOpenTcpConnection(
        () -> {
          assertThat(tigerProxy.getOpenConnections()).hasSize(1);
          withOpenTlsConnection(
              () ->
                  withOpenTlsConnection(
                      () ->
                          assertThat(tigerProxy.getOpenConnections(TigerConnectionStatus.OPEN_TLS))
                              .hasSize(2)));
        });
    await()
        .catchUncaughtExceptions()
        .untilAsserted(() -> assertThat(tigerProxy.getOpenConnections()).isEmpty());
  }

  @SneakyThrows
  private void withOpenTcpConnection(ThrowingRunnable runnable) {
    try (SocketChannel socketChannel = SocketChannel.open()) {
      socketChannel.connect(new InetSocketAddress("localhost", tigerProxy.getProxyPort()));
      await()
          .catchUncaughtExceptions()
          .atMost(500, TimeUnit.MILLISECONDS)
          .untilAsserted(runnable::run);
    }
  }

  @SneakyThrows
  private void withOpenTlsConnection(ThrowingRunnable runnable) {
    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(
        null,
        new TrustManager[] {
          new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {}

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {}

            @Override
            public X509Certificate[] getAcceptedIssuers() {
              return new X509Certificate[0];
            }
          }
        },
        null);

    try (SSLSocket socket =
        (SSLSocket)
            sslContext.getSocketFactory().createSocket("localhost", tigerProxy.getProxyPort())) {
      socket.startHandshake();

      await()
          .catchUncaughtExceptions()
          .atMost(500, TimeUnit.MILLISECONDS)
          .untilAsserted(runnable::run);
    }
  }
}
