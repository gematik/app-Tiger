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
package de.gematik.test.tiger.proxy.tls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.gematik.test.tiger.common.data.config.tigerproxy.AlpnProtocol;
import de.gematik.test.tiger.mockserver.proxyconfiguration.ProxyConfiguration;
import de.gematik.test.tiger.proxy.H1TlsServer;
import de.gematik.test.tiger.proxy.H2TestServer;
import de.gematik.test.tiger.proxy.MiniHttpConnectProxy;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.SSLSocket;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for {@link BackendAlpnProber}. */
class BackendAlpnProberTest {

  // --- direct probing ----------------------------------------------------------------------------

  @SneakyThrows
  @Test
  void probeH2Backend_shouldReturnH2() {
    try (H2TestServer h2Backend = H2TestServer.h2Tls(0)) {
      h2Backend.start();

      var result = BackendAlpnProber.probe("localhost", h2Backend.getPort());

      assertThat(result).isPresent().hasValue(AlpnProtocol.H2);
    }
  }

  @SneakyThrows
  @Test
  void probeH1Backend_shouldReturnHttp11() {
    try (H1TlsServer h1Backend = new H1TlsServer(0)) {
      h1Backend.start();

      var result = BackendAlpnProber.probe("localhost", h1Backend.getPort());

      assertThat(result).isPresent().hasValue(AlpnProtocol.HTTP_1_1);
    }
  }

  @Test
  void probeUnreachableBackend_shouldReturnEmpty() {
    var result = BackendAlpnProber.probe("localhost", 1);

    assertThat(result).isEmpty();
  }

  @SneakyThrows
  @Test
  void probeHttpUrl_shouldReturnEmpty() {
    var result = BackendAlpnProber.probe(new URL("http://localhost:12345"));

    assertThat(result).isEmpty();
  }

  @SneakyThrows
  @Test
  void probe_plainTextBackend_failsHandshakeAndReturnsEmpty() {
    try (ServerSocket plainServer = new ServerSocket(0)) {
      ExecutorService executor = Executors.newSingleThreadExecutor();
      executor.submit(
          () -> {
            try {
              Socket accepted = plainServer.accept();
              accepted.close();
            } catch (IOException ignored) {
              // server closed
            }
          });
      try {
        var result = BackendAlpnProber.probe("localhost", plainServer.getLocalPort());

        assertThat(result)
            .describedAs("Probing a non-TLS server must fail-open (empty), not throw")
            .isEmpty();
      } finally {
        executor.shutdownNow();
      }
    }
  }

  // --- deriveServerAlpnProtocols -----------------------------------------------------------------

  @Test
  void deriveServerAlpnProtocols_h2Backend_shouldReturnBoth() {
    var protocols = BackendAlpnProber.deriveServerAlpnProtocols(Optional.of(AlpnProtocol.H2));

    assertThat(protocols).containsExactly(AlpnProtocol.H2, AlpnProtocol.HTTP_1_1);
  }

  @Test
  void deriveServerAlpnProtocols_h1Backend_shouldReturnH1Only() {
    var protocols = BackendAlpnProber.deriveServerAlpnProtocols(Optional.of(AlpnProtocol.HTTP_1_1));

    assertThat(protocols).containsExactly(AlpnProtocol.HTTP_1_1);
  }

  @Test
  void deriveServerAlpnProtocols_probeFailed_shouldReturnBoth() {
    var protocols = BackendAlpnProber.deriveServerAlpnProtocols(Optional.empty());

    assertThat(protocols).containsExactly(AlpnProtocol.H2, AlpnProtocol.HTTP_1_1);
  }

  // --- getAlpnProtocol (negotiated-string parsing) -----------------------------------------------

  @Test
  void getAlpnProtocol_h2_isParsed() {
    assertThat(BackendAlpnProber.getAlpnProtocol("h", 1, "h2")).hasValue(AlpnProtocol.H2);
  }

  @Test
  void getAlpnProtocol_http11_isParsed() {
    assertThat(BackendAlpnProber.getAlpnProtocol("h", 1, "http/1.1"))
        .hasValue(AlpnProtocol.HTTP_1_1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"spdy/3", "h3", "", "garbage"})
  void getAlpnProtocol_unrecognised_returnsEmpty(String negotiated) {
    assertThat(BackendAlpnProber.getAlpnProtocol("h", 1, negotiated)).isEmpty();
  }

  // --- forward-proxy-aware probing ---------------------------------------------------------------

  @SneakyThrows
  @Test
  void probe_throughForwardProxy_returnsBackendAlpn() {
    try (H2TestServer h2Backend = H2TestServer.h2Tls(0);
        MiniHttpConnectProxy upstreamProxy = new MiniHttpConnectProxy()) {
      h2Backend.start();
      upstreamProxy.start();

      var proxyConfig =
          ProxyConfiguration.proxyConfiguration(
              ProxyConfiguration.Type.HTTP, "localhost:" + upstreamProxy.getPort());

      var result = BackendAlpnProber.probe("localhost", h2Backend.getPort(), proxyConfig);

      assertThat(result).isPresent().hasValue(AlpnProtocol.H2);
      assertThat(upstreamProxy.getConnectRequests()).hasValue(1);
      assertThat(upstreamProxy.getAcceptedTunnels()).hasValue(1);
    }
  }

  @SneakyThrows
  @Test
  void probe_throughForwardProxy_h1Backend_returnsHttp11() {
    try (H1TlsServer h1Backend = new H1TlsServer(0);
        MiniHttpConnectProxy upstreamProxy = new MiniHttpConnectProxy()) {
      h1Backend.start();
      upstreamProxy.start();

      var proxyConfig =
          ProxyConfiguration.proxyConfiguration(
              ProxyConfiguration.Type.HTTP, "localhost:" + upstreamProxy.getPort());

      var result = BackendAlpnProber.probe("localhost", h1Backend.getPort(), proxyConfig);

      assertThat(result).isPresent().hasValue(AlpnProtocol.HTTP_1_1);
      assertThat(upstreamProxy.getConnectRequests()).hasValue(1);
    }
  }

  @SneakyThrows
  @Test
  void probe_throughForwardProxy_withBasicAuth_succeeds() {
    try (H2TestServer h2Backend = H2TestServer.h2Tls(0);
        MiniHttpConnectProxy upstreamProxy =
            MiniHttpConnectProxy.withBasicAuth("alice", "s3cret")) {
      h2Backend.start();
      upstreamProxy.start();

      var proxyConfig =
          ProxyConfiguration.proxyConfiguration(
              ProxyConfiguration.Type.HTTP,
              "localhost:" + upstreamProxy.getPort(),
              "alice",
              "s3cret");

      var result = BackendAlpnProber.probe("localhost", h2Backend.getPort(), proxyConfig);

      assertThat(result).isPresent().hasValue(AlpnProtocol.H2);
      assertThat(upstreamProxy.getAcceptedTunnels()).hasValue(1);
    }
  }

  @SneakyThrows
  @Test
  void probe_throughForwardProxy_withWrongCredentials_returnsEmpty() {
    try (H2TestServer h2Backend = H2TestServer.h2Tls(0);
        MiniHttpConnectProxy upstreamProxy =
            MiniHttpConnectProxy.withBasicAuth("alice", "s3cret")) {
      h2Backend.start();
      upstreamProxy.start();

      var proxyConfig =
          ProxyConfiguration.proxyConfiguration(
              ProxyConfiguration.Type.HTTP,
              "localhost:" + upstreamProxy.getPort(),
              "alice",
              "wrong-password");

      var result = BackendAlpnProber.probe("localhost", h2Backend.getPort(), proxyConfig);

      assertThat(result).isEmpty();
      assertThat(upstreamProxy.getAcceptedTunnels()).hasValue(0);
    }
  }

  @SneakyThrows
  @Test
  void probe_throughForwardProxy_withoutCredentials_sendsNoAuthorizationHeader() {
    try (MiniHttpConnectProxy upstreamProxy = MiniHttpConnectProxy.withBasicAuth("u", "p")) {
      upstreamProxy.start();

      var proxyConfig =
          ProxyConfiguration.proxyConfiguration(
              ProxyConfiguration.Type.HTTP, "localhost:" + upstreamProxy.getPort());

      var result = BackendAlpnProber.probe("localhost", 1, proxyConfig);

      assertThat(result).isEmpty();
      assertThat(upstreamProxy.getConnectRequests()).hasValueGreaterThanOrEqualTo(1);
      assertThat(upstreamProxy.getAcceptedTunnels())
          .describedAs("Without credentials the proxy must reject auth")
          .hasValue(0);
    }
  }

  @SneakyThrows
  @Test
  void probe_throughForwardProxy_proxyRejects_returnsEmpty() {
    try (H2TestServer h2Backend = H2TestServer.h2Tls(0);
        MiniHttpConnectProxy upstreamProxy = MiniHttpConnectProxy.alwaysReject()) {
      h2Backend.start();
      upstreamProxy.start();

      var proxyConfig =
          ProxyConfiguration.proxyConfiguration(
              ProxyConfiguration.Type.HTTP, "localhost:" + upstreamProxy.getPort());

      var result = BackendAlpnProber.probe("localhost", h2Backend.getPort(), proxyConfig);

      assertThat(result).isEmpty();
      assertThat(upstreamProxy.getAcceptedTunnels()).hasValue(0);
    }
  }

  @SneakyThrows
  @Test
  void probe_throughForwardProxy_noProxyHostMatches_bypassesProxy() {
    try (H2TestServer h2Backend = H2TestServer.h2Tls(0);
        MiniHttpConnectProxy upstreamProxy = MiniHttpConnectProxy.alwaysReject()) {
      h2Backend.start();
      upstreamProxy.start();

      var proxyConfig =
          ProxyConfiguration.proxyConfiguration(
              ProxyConfiguration.Type.HTTP, "localhost:" + upstreamProxy.getPort());
      proxyConfig.getNoProxyHosts().add("localhost");

      var result = BackendAlpnProber.probe("localhost", h2Backend.getPort(), proxyConfig);

      assertThat(result).isPresent().hasValue(AlpnProtocol.H2);
      assertThat(upstreamProxy.getConnectRequests()).hasValue(0);
    }
  }

  @SneakyThrows
  @Test
  void probe_noProxyHosts_suffixMatch_bypassesProxy() {
    try (MiniHttpConnectProxy upstreamProxy = MiniHttpConnectProxy.alwaysReject()) {
      upstreamProxy.start();

      var proxyConfig =
          ProxyConfiguration.proxyConfiguration(
              ProxyConfiguration.Type.HTTP, "localhost:" + upstreamProxy.getPort());
      proxyConfig.getNoProxyHosts().add("localhost");

      BackendAlpnProber.probe("sub.localhost", 1, proxyConfig);

      assertThat(upstreamProxy.getConnectRequests())
          .describedAs("Suffix match in noProxyHosts must bypass the proxy")
          .hasValue(0);
    }
  }

  @SneakyThrows
  @Test
  void probe_noProxyHosts_caseInsensitive_bypassesProxy() {
    try (MiniHttpConnectProxy upstreamProxy = MiniHttpConnectProxy.alwaysReject()) {
      upstreamProxy.start();

      var proxyConfig =
          ProxyConfiguration.proxyConfiguration(
              ProxyConfiguration.Type.HTTP, "localhost:" + upstreamProxy.getPort());
      proxyConfig.getNoProxyHosts().add("LOCALHOST");

      BackendAlpnProber.probe("LocalHost", 1, proxyConfig);

      assertThat(upstreamProxy.getConnectRequests())
          .describedAs("noProxyHosts entries must be matched case-insensitively")
          .hasValue(0);
    }
  }

  @SneakyThrows
  @Test
  void probe_socks5ForwardProxy_isSkipped() {
    try (H2TestServer h2Backend = H2TestServer.h2Tls(0)) {
      h2Backend.start();

      var proxyConfig =
          ProxyConfiguration.proxyConfiguration(ProxyConfiguration.Type.SOCKS5, "localhost:9999");

      var result = BackendAlpnProber.probe("localhost", h2Backend.getPort(), proxyConfig);

      assertThat(result).isEmpty();
    }
  }

  // --- port-derivation invariant -----------------------------------------------------------------

  @SneakyThrows
  @Test
  void probe_urlWithoutExplicitPort_targetsDefaultHttpsPort443() {
    try (MiniHttpConnectProxy upstreamProxy = MiniHttpConnectProxy.alwaysReject()) {
      upstreamProxy.start();

      var proxyConfig =
          ProxyConfiguration.proxyConfiguration(
              ProxyConfiguration.Type.HTTP, "localhost:" + upstreamProxy.getPort());

      var result = BackendAlpnProber.probe(new URL("https://localhost"), proxyConfig);

      assertThat(result).isEmpty();
      assertThat(upstreamProxy.getLastConnectTarget())
          .describedAs("URL without explicit port must resolve to the scheme default (443)")
          .isEqualTo("localhost:443");
    }
  }

  @SneakyThrows
  @Test
  void probe_urlWithExplicitPort_targetsThatPort() {
    try (MiniHttpConnectProxy upstreamProxy = MiniHttpConnectProxy.alwaysReject()) {
      upstreamProxy.start();

      var proxyConfig =
          ProxyConfiguration.proxyConfiguration(
              ProxyConfiguration.Type.HTTP, "localhost:" + upstreamProxy.getPort());

      var result = BackendAlpnProber.probe(new URL("https://localhost:8443"), proxyConfig);

      assertThat(result).isEmpty();
      assertThat(upstreamProxy.getLastConnectTarget())
          .describedAs("Explicit URL port must override the scheme default")
          .isEqualTo("localhost:8443");
    }
  }

  @SneakyThrows
  @Test
  void probe_httpUrl_isSkippedAndDoesNotContactProxy() {
    try (MiniHttpConnectProxy upstreamProxy = MiniHttpConnectProxy.alwaysReject()) {
      upstreamProxy.start();

      var proxyConfig =
          ProxyConfiguration.proxyConfiguration(
              ProxyConfiguration.Type.HTTP, "localhost:" + upstreamProxy.getPort());

      var result = BackendAlpnProber.probe(new URL("http://localhost"), proxyConfig);

      assertThat(result).isEmpty();
      assertThat(upstreamProxy.getConnectRequests())
          .describedAs("Non-https URLs must short-circuit before touching the network")
          .hasValue(0);
    }
  }

  // --- wrapWithTls (TLS hop to HTTPS forward proxy) ----------------------------------------------

  @SneakyThrows
  @Test
  void wrapWithTls_handshakeSucceeds_returnsOpenSslSocket() {
    try (H1TlsServer tlsBackend = new H1TlsServer(0)) {
      tlsBackend.start();

      Socket raw = new Socket();
      raw.connect(new InetSocketAddress("localhost", tlsBackend.getPort()), 3_000);

      SSLSocket wrapped =
          BackendAlpnProber.wrapWithTls(
              raw, new InetSocketAddress("localhost", tlsBackend.getPort()));

      try {
        assertThat(wrapped.isConnected()).isTrue();
        assertThat(wrapped.isClosed()).isFalse();
        assertThat(wrapped.getSession().getProtocol()).startsWith("TLS");
      } finally {
        wrapped.close();
      }
    }
  }

  @SneakyThrows
  @Test
  void wrapWithTls_handshakeFails_closesRawSocket() {
    // Plain-text TCP server: accepts and immediately closes -> SSL handshake reads EOF and fails.
    try (ServerSocket plainServer = new ServerSocket(0)) {
      ExecutorService executor = Executors.newSingleThreadExecutor();
      executor.submit(
          () -> {
            try {
              Socket accepted = plainServer.accept();
              accepted.close();
            } catch (IOException ignored) {
              // server closed
            }
          });
      try {
        Socket raw = new Socket();
        raw.connect(new InetSocketAddress("localhost", plainServer.getLocalPort()), 3_000);

        assertThatThrownBy(
                () ->
                    BackendAlpnProber.wrapWithTls(
                        raw, new InetSocketAddress("localhost", plainServer.getLocalPort())))
            .isInstanceOfAny(IOException.class, RuntimeException.class);

        assertThat(raw.isClosed())
            .describedAs("raw socket must be closed when the TLS wrap fails")
            .isTrue();
      } finally {
        executor.shutdownNow();
      }
    }
  }
}
