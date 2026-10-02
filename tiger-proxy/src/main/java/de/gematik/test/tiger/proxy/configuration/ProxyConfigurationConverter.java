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
package de.gematik.test.tiger.proxy.configuration;

import static de.gematik.test.tiger.mockserver.proxyconfiguration.ProxyConfiguration.Type.HTTP;
import static de.gematik.test.tiger.mockserver.proxyconfiguration.ProxyConfiguration.proxyConfiguration;

import de.gematik.test.tiger.common.data.config.tigerproxy.ForwardProxyInfo;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyType;
import de.gematik.test.tiger.common.exceptions.TigerProxyToForwardProxyException;
import de.gematik.test.tiger.common.exceptions.TigerUnknownProtocolException;
import de.gematik.test.tiger.mockserver.proxyconfiguration.ProxyConfiguration;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

public class ProxyConfigurationConverter {

  private ProxyConfigurationConverter() {}

  public static Optional<ProxyConfiguration>
      convertForwardProxyConfigurationToMockServerConfiguration(TigerProxyConfiguration tpConfig) {
    return Optional.ofNullable(tpConfig.getForwardToProxy())
        .flatMap(ProxyConfigurationConverter::createMockServerProxyConfiguration);
  }

  public static ProxyConfiguration.Type toMockServerType(TigerProxyType type)
      throws TigerUnknownProtocolException {
    if (type == TigerProxyType.HTTP) {
      return HTTP;
    } else if (type == TigerProxyType.HTTPS) {
      return ProxyConfiguration.Type.HTTPS;
    } else {
      throw new TigerUnknownProtocolException(
          "Protocol of type " + type.toString() + " not specified for proxies");
    }
  }

  public static Optional<ProxyConfiguration> createMockServerProxyConfiguration(
      ForwardProxyInfo forwardProxyInfo) {
    if (StringUtils.isEmpty(forwardProxyInfo.getHostname())) {
      return Optional.empty();
    }
    Optional<ProxyConfiguration> proxyConfiguration;
    proxyConfiguration = convertForwardProxyInfoConfig(forwardProxyInfo);

    return proxyConfiguration.map(
        configuration -> {
          if (forwardProxyInfo.getNoProxyHosts() != null) {
            configuration.getNoProxyHosts().addAll(forwardProxyInfo.getNoProxyHosts());
          }
          return configuration;
        });
  }

  private static Optional<ProxyConfiguration> convertForwardProxyInfoConfig(
      ForwardProxyInfo forwardProxyInfo) {
    if (Strings.CS.equals(forwardProxyInfo.getHostname(), "$SYSTEM")) {
      return convertSystemProxyConfig();
    } else {
      return Optional.of(
          proxyConfiguration(
              Optional.ofNullable(forwardProxyInfo.getType())
                  .map(ProxyConfigurationConverter::toMockServerType)
                  .orElse(HTTP),
              forwardProxyInfo.getHostname() + ":" + forwardProxyInfo.calculateProxyPort(),
              forwardProxyInfo.getUsername(),
              forwardProxyInfo.getPassword()));
    }
  }

  public static Optional<ProxyConfiguration> useProxyWithSystemProperties(String proxyProtocol) {
    TigerProxyType tigerProxyType = TigerProxyType.fromProxyProtocol(proxyProtocol);
    ProxyConfiguration.Type proxyType = toMockServerType(tigerProxyType);
    String proxyHost = System.getProperty(proxyProtocol + ".proxyHost");
    String proxyPort = System.getProperty(proxyProtocol + ".proxyPort");
    String proxyUser = System.getProperty(proxyProtocol + ".proxyUser");
    String proxyPassword = System.getProperty(proxyProtocol + ".proxyPassword");

    if (StringUtils.isEmpty(proxyHost)) {
      return Optional.empty();
    }

    if (proxyUser != null || proxyPassword != null) {
      if (proxyUser == null) {
        throw new TigerProxyToForwardProxyException(
            "Could not convert proxy configuration: proxyUser == null, proxyPassword != null");
      } else if (proxyPassword == null) {
        throw new TigerProxyToForwardProxyException(
            "Could not convert proxy configuration: proxyUser != null, proxyPassword == null");
      }
    }

    String proxyAddress = proxyHost + ":" + normalizeSystemPropertyPort(proxyPort, tigerProxyType);
    return Optional.of(
        proxyUser == null
            ? proxyConfiguration(proxyType, proxyAddress)
            : proxyConfiguration(proxyType, proxyAddress, proxyUser, proxyPassword));
  }

  private static String normalizeSystemPropertyPort(String proxyPort, TigerProxyType type) {
    if (proxyPort == null || proxyPort.equals("null") || proxyPort.equals("-1")) {
      return String.valueOf(type.getDefaultPort());
    }
    return proxyPort;
  }

  public static Optional<ProxyConfiguration> useProxyAsEnvVar(String envProxyType) {
    String httpProxyHostFromEnv = System.getenv(envProxyType);

    if (StringUtils.isEmpty(httpProxyHostFromEnv)) {
      return Optional.empty();
    }

    URI proxyAsUri = URI.create(httpProxyHostFromEnv);

    if (proxyAsUri.getHost() == null) {
      throw new TigerProxyToForwardProxyException("No proxy host specified.");
    }

    TigerProxyType tigerProxyType = TigerProxyType.fromProxyProtocol(proxyAsUri.getScheme());
    ProxyConfiguration.Type proxyType = toMockServerType(tigerProxyType);
    String proxyUsernamePassword = proxyAsUri.getUserInfo();
    String proxyPort =
        normalizeSystemPropertyPort(String.valueOf(proxyAsUri.getPort()), tigerProxyType);

    if (proxyUsernamePassword == null) {
      return Optional.of(proxyConfiguration(proxyType, proxyAsUri.getHost() + ":" + proxyPort));
    } else if (!proxyUsernamePassword.contains(":")) {
      throw new TigerProxyToForwardProxyException(
          "Could not convert proxy configuration: either username or password are not present in"
              + " the env variable");
    } else {
      return Optional.of(
          proxyConfiguration(
              proxyType,
              proxyAsUri.getHost() + ":" + proxyPort,
              proxyUsernamePassword.split(":")[0],
              proxyUsernamePassword.split(":")[1]));
    }
  }

  private static Optional<ProxyConfiguration> useProxyWithProxySelector(String proxyProtocol) {
    ProxySelector proxySelector = ProxySelector.getDefault();
    if (proxySelector == null) {
      return Optional.empty();
    }

    return proxySelector.select(URI.create(proxyProtocol + "://example.com")).stream()
        .filter(proxy -> proxy.type() == Proxy.Type.HTTP)
        .map(Proxy::address)
        .filter(InetSocketAddress.class::isInstance)
        .map(InetSocketAddress.class::cast)
        .findFirst()
        .map(address -> proxyConfiguration(HTTP, address));
  }

  private static Optional<ProxyConfiguration> convertSystemProxyConfig() {
    return useProxyWithSystemProperties("http")
        .or(() -> useProxyWithSystemProperties("https"))
        .or(() -> useProxyAsEnvVar("http_proxy"))
        .or(() -> useProxyAsEnvVar("https_proxy"))
        .or(() -> useProxyWithProxySelector("http"))
        .or(() -> useProxyWithProxySelector("https"))
        .map(ProxyConfigurationConverter::addSystemNoProxyHosts)
        .map(ProxyConfigurationConverter::addEnvironmentNoProxyHosts)
        .map(ProxyConfigurationConverter::addDefaultNoProxyHosts);
  }

  private static ProxyConfiguration addSystemNoProxyHosts(ProxyConfiguration proxyConfiguration) {
    addNoProxyHosts(proxyConfiguration, System.getProperty("http.nonProxyHosts"), "\\|");
    return proxyConfiguration;
  }

  private static ProxyConfiguration addEnvironmentNoProxyHosts(
      ProxyConfiguration proxyConfiguration) {
    addNoProxyHosts(proxyConfiguration, System.getenv("no_proxy"), ",");
    addNoProxyHosts(proxyConfiguration, System.getenv("NO_PROXY"), ",");
    return proxyConfiguration;
  }

  private static void addNoProxyHosts(
      ProxyConfiguration proxyConfiguration, String noProxyHosts, String separator) {
    if (StringUtils.isNotBlank(noProxyHosts)) {
      proxyConfiguration
          .getNoProxyHosts()
          .addAll(
              Stream.of(noProxyHosts.split(separator))
                  .map(String::trim)
                  .filter(StringUtils::isNotEmpty)
                  .toList());
    }
  }

  private static ProxyConfiguration addDefaultNoProxyHosts(ProxyConfiguration proxyConfiguration) {
    proxyConfiguration
        .getNoProxyHosts()
        .addAll(List.of("localhost", "127.0.0.1", "::1", "0:0:0:0:0:0:0:1"));
    return proxyConfiguration;
  }
}
