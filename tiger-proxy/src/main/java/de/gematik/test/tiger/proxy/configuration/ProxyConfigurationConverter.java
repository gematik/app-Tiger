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

package de.gematik.test.tiger.proxy.configuration;

import static de.gematik.test.tiger.mockserver.proxyconfiguration.ProxyConfiguration.Type.HTTP;
import static de.gematik.test.tiger.mockserver.proxyconfiguration.ProxyConfiguration.proxyConfiguration;

import de.gematik.test.tiger.common.data.config.tigerproxy.ForwardProxyInfo;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyType;
import de.gematik.test.tiger.common.exceptions.TigerProxyToForwardProxyException;
import de.gematik.test.tiger.common.exceptions.TigerUnknownProtocolException;
import de.gematik.test.tiger.mockserver.proxyconfiguration.ProxyConfiguration;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

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
    if (StringUtils.equals(forwardProxyInfo.getHostname(), "$SYSTEM")) {
      return convertSystemProxyConfig(forwardProxyInfo);
    } else {
      final ProxyConfiguration result = proxyConfiguration(
        Optional.ofNullable(forwardProxyInfo.getType())
          .map(ProxyConfigurationConverter::toMockServerType)
          .orElse(HTTP),
        forwardProxyInfo.getHostname() + ":" + forwardProxyInfo.calculateProxyPort(),
        forwardProxyInfo.getUsername(),
        forwardProxyInfo.getPassword());
      if (forwardProxyInfo.getNoProxyHosts() != null) {
        result.getNoProxyHosts().addAll(forwardProxyInfo.getNoProxyHosts());
      }
      return Optional.of(result);
    }
  }

  public static Optional<ProxyConfiguration> useProxyWithSystemProperties(
      String proxyProtocol, ForwardProxyInfo forwardProxyInfo) {
    ProxyConfiguration.Type proxyType =
        toMockServerType(forwardProxyInfo.getProxyProtocol(proxyProtocol));
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
      return Optional.of(
          proxyConfiguration(
              proxyType,
              proxyHost
                  + ":"
                  + ForwardProxyInfo.mapProxyPort(
                      proxyPort, forwardProxyInfo.getProxyProtocol(proxyProtocol)),
              proxyUser,
              proxyPassword));
    } else {
      return Optional.of(
          proxyConfiguration(
              proxyType,
              proxyHost
                  + ":"
                  + ForwardProxyInfo.mapProxyPort(
                      proxyPort, forwardProxyInfo.getProxyProtocol(proxyProtocol))));
    }
  }

  public static Optional<ProxyConfiguration> useProxyAsEnvVar(
      ForwardProxyInfo forwardProxyInfo, String envProxyType) {
    String httpProxyHostFromEnv = System.getenv(envProxyType);

    if (StringUtils.isEmpty(httpProxyHostFromEnv)) {
      return Optional.empty();
    }

    URI proxyAsUri = URI.create(httpProxyHostFromEnv);

    if (proxyAsUri.getHost() == null) {
      throw new TigerProxyToForwardProxyException("No proxy host specified.");
    }

    ProxyConfiguration.Type proxyType =
        toMockServerType(forwardProxyInfo.getProxyProtocol(proxyAsUri.getScheme()));
    String proxyUsernamePassword = proxyAsUri.getUserInfo();
    String proxyPort =
        ForwardProxyInfo.mapProxyPort(
            String.valueOf(proxyAsUri.getPort()),
            forwardProxyInfo.getProxyProtocol(proxyAsUri.getScheme()));

    if (proxyUsernamePassword == null) {
      return Optional.of(proxyConfiguration(proxyType, proxyAsUri.getHost() + ":" + proxyPort))
        .map(ProxyConfigurationConverter::addNoProxyHostsFromEnv);
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
              proxyUsernamePassword.split(":")[1]))
        .map(ProxyConfigurationConverter::addNoProxyHostsFromEnv);
    }
  }

  private static ProxyConfiguration addNoProxyHostsFromEnv(ProxyConfiguration proxyConfiguration) {
    String noProxyHosts = System.getenv("no_proxy");
    if (noProxyHosts != null) {
      proxyConfiguration.getNoProxyHosts().addAll(List.of(noProxyHosts.split(",")));
    }
    return proxyConfiguration;
  }

  public static Optional<ProxyConfiguration> convertSystemProxyConfig(
      ForwardProxyInfo forwardProxyInfo) {
    return useProxyWithSystemProperties("http", forwardProxyInfo)
        .or(() -> useProxyWithSystemProperties("https", forwardProxyInfo))
        .or(() -> useProxyAsEnvVar(forwardProxyInfo, "http_proxy"))
        .or(() -> useProxyAsEnvVar(forwardProxyInfo, "https_proxy"));
  }
}
