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

import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration;
import de.gematik.test.tiger.mockserver.socket.tls.KeyAlgorithmPreference;
import de.gematik.test.tiger.mockserver.socket.tls.NettySslContextFactory;
import de.gematik.test.tiger.proxy.tls.MockServerTlsConfigurator;
import io.netty.handler.ssl.ApplicationProtocolNames;
import java.util.Optional;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

/**
 * Verifies ALPN defaults for the proxy TLS server configuration.
 */
class AlpnConfigurationTest {

  /**
   * Ensures HTTP/2 is not advertised when ALPN defaults are applied.
   */
  @Test
  void serverAlpnDoesNotAdvertiseHttp2ByDefault() {
    java.security.Security.addProvider(new BouncyCastleProvider());
    var mockServerConfiguration = MockServerConfiguration.configuration();
    var tlsConfigurator =
        MockServerTlsConfigurator.builder()
            .tigerProxyConfiguration(new TigerProxyConfiguration())
            .mockServerConfiguration(mockServerConfiguration)
            .tigerProxyName(Optional.of("alpn-test"))
            .serverRootCa(null)
            .build();
    tlsConfigurator.execute();

    var sslContextFactory = new NettySslContextFactory(mockServerConfiguration, true);
    var protocols = sslContextFactory
        .createServerSslContext("localhost", KeyAlgorithmPreference.MIXED)
        .getLeft()
        .applicationProtocolNegotiator()
        .protocols();

    assertThat(protocols).doesNotContain(ApplicationProtocolNames.HTTP_2);
    if (!protocols.isEmpty()) {
      assertThat(protocols).contains(ApplicationProtocolNames.HTTP_1_1);
    }
  }
}
