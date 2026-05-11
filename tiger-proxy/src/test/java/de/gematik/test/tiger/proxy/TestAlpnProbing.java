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

import de.gematik.test.tiger.common.data.config.tigerproxy.AlpnProtocol;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerConfigurationRoute;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

/** Integration tests for per-connection ALPN resolution through the Tiger Proxy. */
@Slf4j
class TestAlpnProbing extends AbstractTigerProxyTest {

  @SneakyThrows
  @Test
  void h1OnlyBackend_clientShouldNegotiateH1_notH2() {
    try (H1TlsServer h1Backend = new H1TlsServer(0)) {
      h1Backend.start();

      TigerProxyConfiguration config = new TigerProxyConfiguration();
      spawnTigerProxyWith(config);
      tigerProxy.addRoute(
          TigerConfigurationRoute.builder()
              .from("/")
              .to("https://localhost:" + h1Backend.getPort())
              .build());

      final HttpResponse<String> response =
          HttpClient.newBuilder()
              .sslContext(tigerProxy.buildSslContext())
              .version(Version.HTTP_2)
              .build()
              .send(
                  HttpRequest.newBuilder()
                      .uri(new URI("https://localhost:" + tigerProxy.getProxyPort() + "/test"))
                      .version(Version.HTTP_2)
                      .GET()
                      .build(),
                  BodyHandlers.ofString());

      assertThat(response.version())
          .describedAs(
              "Client should negotiate HTTP/1.1 because the backend only supports h1 "
                  + "and the proxy's ALPN probe restricts advertised protocols")
          .isEqualTo(Version.HTTP_1_1);
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body()).contains("h1");

      assertThat(h1Backend.getRequestsReceived()).hasValue(1);
    }
  }

  @SneakyThrows
  @Test
  void h2Backend_clientShouldStillNegotiateH2() {
    try (H2TestServer h2Backend = H2TestServer.h2Tls(0)) {
      h2Backend.start();

      TigerProxyConfiguration config = new TigerProxyConfiguration();
      spawnTigerProxyWith(config);
      tigerProxy.addRoute(
          TigerConfigurationRoute.builder()
              .from("/")
              .to("https://localhost:" + h2Backend.getPort())
              .build());

      final HttpResponse<String> response =
          HttpClient.newBuilder()
              .sslContext(tigerProxy.buildSslContext())
              .version(Version.HTTP_2)
              .build()
              .send(
                  HttpRequest.newBuilder()
                      .uri(new URI("https://localhost:" + tigerProxy.getProxyPort() + "/test"))
                      .version(Version.HTTP_2)
                      .GET()
                      .build(),
                  BodyHandlers.ofString());

      assertThat(response.version())
          .describedAs("Client should negotiate HTTP/2 because the backend supports it")
          .isEqualTo(Version.HTTP_2);
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body()).contains("h2");

      assertThat(h2Backend.getRequestsReceived()).hasValue(1);
    }
  }

  @SneakyThrows
  @Test
  void httpBackend_noProbe_clientCanStillUseH2() {
    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());

    final HttpResponse<String> response =
        HttpClient.newBuilder()
            .sslContext(tigerProxy.buildSslContext())
            .version(Version.HTTP_2)
            .build()
            .send(
                HttpRequest.newBuilder()
                    .uri(new URI("https://localhost:" + tigerProxy.getProxyPort() + "/foobar"))
                    .version(Version.HTTP_2)
                    .GET()
                    .build(),
                BodyHandlers.ofString());

    assertThat(response.version())
        .describedAs("HTTP backend: proxy should still offer h2 (fail-open)")
        .isEqualTo(Version.HTTP_2);
    assertThat(response.statusCode()).isEqualTo(666);
  }

  @SneakyThrows
  @Test
  void mixedBackends_conservativeFallback_shouldOfferH1Only() {
    try (H2TestServer h2Backend = H2TestServer.h2Tls(0);
        H1TlsServer h1Backend = new H1TlsServer(0)) {
      h2Backend.start();
      h1Backend.start();

      TigerProxyConfiguration config = new TigerProxyConfiguration();
      spawnTigerProxyWith(config);
      tigerProxy.addRoute(
          TigerConfigurationRoute.builder()
              .from("/h2path")
              .to("https://localhost:" + h2Backend.getPort())
              .build());
      tigerProxy.addRoute(
          TigerConfigurationRoute.builder()
              .from("/h1path")
              .to("https://localhost:" + h1Backend.getPort())
              .build());

      final HttpResponse<String> response =
          HttpClient.newBuilder()
              .sslContext(tigerProxy.buildSslContext())
              .version(Version.HTTP_2)
              .build()
              .send(
                  HttpRequest.newBuilder()
                      .uri(
                          new URI(
                              "https://localhost:" + tigerProxy.getProxyPort() + "/h1path/test"))
                      .version(Version.HTTP_2)
                      .GET()
                      .build(),
                  BodyHandlers.ofString());

      assertThat(response.version())
          .describedAs(
              "Mixed backends: conservative fallback should restrict to HTTP/1.1 "
                  + "because one backend only supports h1")
          .isEqualTo(Version.HTTP_1_1);
      assertThat(response.statusCode()).isEqualTo(200);
    }
  }

  @SneakyThrows
  @Test
  void explicitHostRouting_shouldResolvePerHostname() {
    try (H2TestServer h2Backend = H2TestServer.h2Tls(0);
        H1TlsServer h1Backend = new H1TlsServer(0)) {
      h2Backend.start();
      h1Backend.start();

      TigerProxyConfiguration config = new TigerProxyConfiguration();
      spawnTigerProxyWith(config);
      tigerProxy.addRoute(
          TigerConfigurationRoute.builder()
              .from("/")
              .to("https://localhost:" + h1Backend.getPort())
              .hosts(List.of("localhost"))
              .build());

      final HttpResponse<String> response =
          HttpClient.newBuilder()
              .sslContext(tigerProxy.buildSslContext())
              .version(Version.HTTP_2)
              .build()
              .send(
                  HttpRequest.newBuilder()
                      .uri(new URI("https://localhost:" + tigerProxy.getProxyPort() + "/test"))
                      .version(Version.HTTP_2)
                      .GET()
                      .build(),
                  BodyHandlers.ofString());

      assertThat(response.version())
          .describedAs("Route with explicit host 'localhost' → h1 backend → client gets h1")
          .isEqualTo(Version.HTTP_1_1);
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body()).contains("h1");
    }
  }

  @SneakyThrows
  @Test
  void allBackendsH2_shouldOfferH2() {
    try (H2TestServer h2Backend1 = H2TestServer.h2Tls(0);
        H2TestServer h2Backend2 = H2TestServer.h2Tls(0)) {
      h2Backend1.start();
      h2Backend2.start();

      TigerProxyConfiguration config = new TigerProxyConfiguration();
      spawnTigerProxyWith(config);
      tigerProxy.addRoute(
          TigerConfigurationRoute.builder()
              .from("/api")
              .to("https://localhost:" + h2Backend1.getPort())
              .build());
      tigerProxy.addRoute(
          TigerConfigurationRoute.builder()
              .from("/other")
              .to("https://localhost:" + h2Backend2.getPort())
              .build());

      final HttpResponse<String> response =
          HttpClient.newBuilder()
              .sslContext(tigerProxy.buildSslContext())
              .version(Version.HTTP_2)
              .build()
              .send(
                  HttpRequest.newBuilder()
                      .uri(new URI("https://localhost:" + tigerProxy.getProxyPort() + "/api/test"))
                      .version(Version.HTTP_2)
                      .GET()
                      .build(),
                  BodyHandlers.ofString());

      assertThat(response.version())
          .describedAs("All backends support h2 → client should get h2")
          .isEqualTo(Version.HTTP_2);
      assertThat(response.statusCode()).isEqualTo(200);
    }
  }

  @SneakyThrows
  @Test
  void explicitAlpnDeclaration_overridesProbeResult() {
    try (H2TestServer h2Backend = H2TestServer.h2Tls(0);
        H1TlsServer h1Backend = new H1TlsServer(0)) {
      h2Backend.start();
      h1Backend.start();

      TigerProxyConfiguration config = new TigerProxyConfiguration();
      spawnTigerProxyWith(config);

      tigerProxy.addRoute(
          TigerConfigurationRoute.builder()
              .from("/h2path")
              .to("https://localhost:" + h2Backend.getPort())
              .hosts(List.of("localhost"))
              .alpnProtocols(List.of(AlpnProtocol.H2, AlpnProtocol.HTTP_1_1))
              .build());
      tigerProxy.addRoute(
          TigerConfigurationRoute.builder()
              .from("/h1path")
              .to("https://localhost:" + h1Backend.getPort())
              .hosts(List.of("localhost"))
              .alpnProtocols(List.of(AlpnProtocol.HTTP_1_1))
              .build());

      final HttpResponse<String> response =
          HttpClient.newBuilder()
              .sslContext(tigerProxy.buildSslContext())
              .version(Version.HTTP_2)
              .build()
              .send(
                  HttpRequest.newBuilder()
                      .uri(
                          new URI(
                              "https://localhost:" + tigerProxy.getProxyPort() + "/h1path/test"))
                      .version(Version.HTTP_2)
                      .GET()
                      .build(),
                  BodyHandlers.ofString());

      assertThat(response.version())
          .describedAs(
              "Explicit ALPN declarations should override probe results. "
                  + "Intersection of declared protocols = [http/1.1]")
          .isEqualTo(Version.HTTP_1_1);
      assertThat(response.statusCode()).isEqualTo(200);
    }
  }

  @SneakyThrows
  @Test
  void explicitAlpnDeclaration_overridesWhatProbeWouldFind() {
    try (H1TlsServer h1Backend = new H1TlsServer(0)) {
      h1Backend.start();

      TigerProxyConfiguration config = new TigerProxyConfiguration();
      spawnTigerProxyWith(config);
      tigerProxy.addRoute(
          TigerConfigurationRoute.builder()
              .from("/")
              .to("https://localhost:" + h1Backend.getPort())
              .alpnProtocols(List.of(AlpnProtocol.H2, AlpnProtocol.HTTP_1_1))
              .build());

      final HttpResponse<String> response =
          HttpClient.newBuilder()
              .sslContext(tigerProxy.buildSslContext())
              .version(Version.HTTP_2)
              .build()
              .send(
                  HttpRequest.newBuilder()
                      .uri(new URI("https://localhost:" + tigerProxy.getProxyPort() + "/test"))
                      .version(Version.HTTP_2)
                      .GET()
                      .build(),
                  BodyHandlers.ofString());

      assertThat(response.version())
          .describedAs(
              "Explicit alpnProtocols=[h2, http/1.1] should override the h1-only probe result. "
                  + "Client gets h2, proxy translates to h1 for backend.")
          .isEqualTo(Version.HTTP_2);
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body()).contains("h1");
      assertThat(h1Backend.getRequestsReceived()).hasValue(1);
    }
  }
}
