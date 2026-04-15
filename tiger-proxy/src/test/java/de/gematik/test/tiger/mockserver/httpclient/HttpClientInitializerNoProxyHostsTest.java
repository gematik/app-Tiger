/*
 *  Copyright 2021-2025 gematik GmbH
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
 * ******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.test.tiger.mockserver.httpclient;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration;
import de.gematik.test.tiger.mockserver.netty.proxy.BinaryModifierApplier;
import de.gematik.test.tiger.mockserver.proxyconfiguration.ProxyConfiguration;
import de.gematik.test.tiger.mockserver.proxyconfiguration.ProxyConfiguration.Type;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class HttpClientInitializerNoProxyHostsTest {

  @ParameterizedTest(name = "host={0}, patterns={1} -> shouldUseProxy={2}")
  @MethodSource("isHostNotOnNoProxyHostListCases")
  void isHostNotOnNoProxyHostList_shouldHandleWildcardsAndEdgeCases(
      InetSocketAddress remoteAddress, List<String> noProxyHosts, boolean shouldUseProxy) {
    HttpClientInitializer initializer = initializerWithNoProxyHosts(noProxyHosts);

    assertThat(initializer.isHostNotOnNoProxyHostList(remoteAddress)).isEqualTo(shouldUseProxy);
  }

  @ParameterizedTest(name = "host={0}, pattern={1} -> matches={2}")
  @MethodSource("matchesCases")
  void matches_shouldHandleExactAndWildcardPatterns(String host, String pattern, boolean expected) {
    assertThat(HttpClientInitializer.hostPatternMatches(host, pattern)).isEqualTo(expected);
  }

  private static Stream<Arguments> isHostNotOnNoProxyHostListCases() {
    return Stream.of(
        Arguments.of(null, List.of("example.com"), true),
        Arguments.of(new InetSocketAddress("api.internal.example.com", 443), null, true),
        Arguments.of(new InetSocketAddress("api.internal.example.com", 443), List.of(), true),
        Arguments.of(new InetSocketAddress("example.com", 443), List.of("example.com"), false),
        Arguments.of(new InetSocketAddress("EXAMPLE.COM", 443), List.of("example.com"), false),
        Arguments.of(
            new InetSocketAddress("api.internal.example.com", 443),
            List.of("*.internal.example.com"),
            false),
        Arguments.of(
            new InetSocketAddress("internal.example.com", 443),
            List.of("*.internal.example.com"),
            true),
        Arguments.of(
            new InetSocketAddress("service-a.internal.example.com", 443),
            List.of("service-*.internal.example.com"),
            false),
        Arguments.of(new InetSocketAddress("foo.bar", 443), List.of("*"), false),
        Arguments.of(
            new InetSocketAddress("service.internal.example.com", 443),
            Arrays.asList("   ", null, " *.internal.example.com "),
            false),
        Arguments.of(
            new InetSocketAddress("service.external.example.com", 443),
            List.of("*.internal.example.com", "localhost"),
            true));
  }

  private static Stream<Arguments> matchesCases() {
    return Stream.of(
        Arguments.of("example.com", "example.com", true),
        Arguments.of("example.com", "EXAMPLE.COM", false),
        Arguments.of("service.internal.example.com", "*.internal.example.com", true),
        Arguments.of("internal.example.com", "*.internal.example.com", false),
        Arguments.of("service-a.internal.example.com", "service-*.internal.example.com", true),
        Arguments.of("foo.bar", "*", true),
        Arguments.of("foo.bar", "f*", true),
        Arguments.of("foo.bar", "*.baz", false));
  }

  private static HttpClientInitializer initializerWithNoProxyHosts(List<String> noProxyHosts) {
    MockServerConfiguration configuration = MockServerConfiguration.configuration();

    if (noProxyHosts != null) {
      ProxyConfiguration proxyConfiguration =
          ProxyConfiguration.proxyConfiguration(
              Type.HTTP, new InetSocketAddress("proxy.local", 1080));
      proxyConfiguration.getNoProxyHosts().addAll(noProxyHosts);
      configuration.proxyConfiguration(proxyConfiguration);
    }

    return new HttpClientInitializer(
        configuration, null, null, new BinaryModifierApplier(configuration));
  }
}
