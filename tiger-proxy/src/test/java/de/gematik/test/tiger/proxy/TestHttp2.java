/*
 *
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

import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

class TestHttp2 extends AbstractTigerProxyTest {

  @SneakyThrows
  @Test
  void testHttp2ViaTls() {
    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());

    final HttpResponse<String> response = HttpClient.newBuilder()
      .sslContext(tigerProxy.buildSslContext())
      .version(Version.HTTP_2)
      .build()
      .send(HttpRequest.newBuilder()
        .uri(new URI("https://localhost:" + tigerProxy.getProxyPort() + "/foobar"))
        .version(Version.HTTP_2)
        .GET()
        .build(), BodyHandlers.ofString());

    //TODO TGR-1699: Check, that the response is really HTTP/2
    assertThat(response.statusCode()).isEqualTo(666);
  }

  @SneakyThrows
  @Test
  void testHttp2Plain() {
    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());

    final HttpResponse<String> response = HttpClient.newBuilder()
      .sslContext(tigerProxy.buildSslContext())
      .version(Version.HTTP_2)
      .build()
      .send(HttpRequest.newBuilder()
        .uri(new URI("http://localhost:" + tigerProxy.getProxyPort() + "/foobar"))
        .version(Version.HTTP_2)
        .GET()
        .build(), BodyHandlers.ofString());

    //TODO TGR-1699: Check, that the response is really HTTP/2
    assertThat(response.statusCode()).isEqualTo(666);
  }
}
