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
package de.gematik.test.tiger.proxy.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static uk.org.webcompere.systemstubs.SystemStubs.restoreSystemProperties;

import de.gematik.test.tiger.common.data.config.tigerproxy.ForwardProxyInfo;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.exceptions.TigerProxyToForwardProxyException;
import de.gematik.test.tiger.mockserver.proxyconfiguration.ProxyConfiguration;
import de.gematik.test.tiger.util.NoProxyUtils;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;

class ProxyConfigurationConverterTest {

  @SneakyThrows
  @Test
  void convertSystemProxyConfig_shouldIncludeNonProxyHostsAndResolvedIpv6Loopback() {
    restoreSystemProperties(
        () -> {
          System.setProperty("http.proxyHost", "corporate-proxy.example.com");
          System.setProperty("http.proxyPort", "3128");
          System.setProperty("http.nonProxyHosts", " localhost | *.internal.example.com | 10.* ");

          var proxyConfiguration = convertSystemProxyConfig();

          assertThat(proxyConfiguration.getNoProxyHosts())
              .contains(
                  "localhost",
                  "*.internal.example.com",
                  "10.*",
                  "127.0.0.1",
                  "::1",
                  "0:0:0:0:0:0:0:1");
        });
  }

  @SneakyThrows
  @Test
  void convertSystemProxyConfig_shouldApplyNoProxyToProxyFromSystemProperties() {
    new EnvironmentVariables("no_proxy", " localhost, *.internal.example.com ")
        .execute(
            () ->
                restoreSystemProperties(
                    () -> {
                      System.setProperty("http.proxyHost", "corporate-proxy.example.com");
                      System.setProperty("http.proxyPort", "3128");

                      var proxyConfiguration = convertSystemProxyConfig();

                      assertThat(proxyConfiguration.getNoProxyHosts())
                          .contains("localhost", "*.internal.example.com");
                    }));
  }

  @SneakyThrows
  @Test
  void blockWithoutHostname_shouldUseSystemProxyAndKeepNoProxyHosts() {
    restoreSystemProperties(
        () -> {
          System.setProperty("http.proxyHost", "corporate-proxy.example.com");
          System.setProperty("http.proxyPort", "3128");

          var proxyConfiguration =
              ProxyConfigurationConverter.convertForwardProxyConfigurationToMockServerConfiguration(
                      TigerProxyConfiguration.builder()
                          .forwardToProxy(
                              ForwardProxyInfo.builder()
                                  .noProxyHosts(List.of(".ti-dienste.de"))
                                  .build())
                          .build())
                  .orElseThrow();

          assertThat(proxyConfiguration.getProxyAddress().getHostString())
              .isEqualTo("corporate-proxy.example.com");
          assertThat(proxyConfiguration.getNoProxyHosts()).contains(".ti-dienste.de");
        });
  }

  @Test
  void invalidHostname_shouldFailAtStartupAndNameTheValue() {
    final TigerProxyConfiguration configuration =
        TigerProxyConfiguration.builder()
            .forwardToProxy(ForwardProxyInfo.builder().hostname("bad host!").build())
            .build();

    assertThatThrownBy(
            () ->
                ProxyConfigurationConverter
                    .convertForwardProxyConfigurationToMockServerConfiguration(configuration))
        .isInstanceOf(TigerProxyToForwardProxyException.class)
        .hasMessageContaining("'bad host!'");
  }

  @Test
  void emptyHostname_shouldFailWithAClearMessage() {
    final TigerProxyConfiguration configuration =
        TigerProxyConfiguration.builder()
            .forwardToProxy(ForwardProxyInfo.builder().hostname("").build())
            .build();

    assertThatThrownBy(
            () ->
                ProxyConfigurationConverter
                    .convertForwardProxyConfigurationToMockServerConfiguration(configuration))
        .isInstanceOf(TigerProxyToForwardProxyException.class)
        .hasMessageContaining("forwardToProxy.hostname is empty");
  }

  @SneakyThrows
  @Test
  void bareDomainInNoProxyShouldAlsoCoverSubdomains() {
    new EnvironmentVariables("no_proxy", "corp.de")
        .execute(
            () ->
                restoreSystemProperties(
                    () -> {
                      System.setProperty("http.proxyHost", "corporate-proxy.example.com");
                      System.setProperty("http.proxyPort", "3128");

                      assertThat(
                              NoProxyUtils.shouldUseProxyForHost(
                                  "x.corp.de", null, convertSystemProxyConfig().getNoProxyHosts()))
                          .isFalse();
                    }));
  }

  private static ProxyConfiguration convertSystemProxyConfig() {
    return ProxyConfigurationConverter.convertForwardProxyConfigurationToMockServerConfiguration(
            TigerProxyConfiguration.builder()
                .forwardToProxy(ForwardProxyInfo.builder().hostname("$SYSTEM").build())
                .build())
        .orElseThrow();
  }
}
