/*
 * Copyright (c) 2021 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.common.config.tigerProxy;

import static org.mockserver.proxyconfiguration.ProxyConfiguration.Type.HTTP;
import static org.mockserver.proxyconfiguration.ProxyConfiguration.proxyConfiguration;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import de.gematik.test.tiger.common.exceptions.TigerProxyToForwardProxyException;
import de.gematik.test.tiger.common.exceptions.TigerUnknownProtocolException;
import java.net.URI;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.proxyconfiguration.ProxyConfiguration;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
@JsonInclude(Include.NON_NULL)
public class ForwardProxyInfo {
    private String hostname;
    private Integer port;
    @Builder.Default
    private TigerProxyType type = TigerProxyType.HTTP;
    private String username;
    private String password;

    public Optional<ProxyConfiguration> createMockServerProxyConfiguration() {
        if (StringUtils.isEmpty(hostname)) {
            return Optional.empty();
        }
        if (StringUtils.equals(hostname, "$SYSTEM")) {
            return convertSystemProxyConfig();
        } else {
            return Optional.of(proxyConfiguration(
                Optional.ofNullable(type)
                    .map(this::toMockServerType)
                    .orElse(ProxyConfiguration.Type.HTTPS),
                hostname + ":" + getProxyPort(String.valueOf(port), type),
                username, password));
        }
    }

    private Optional<ProxyConfiguration> useProxyWithSystemProperties(String proxyProtocol) {
        ProxyConfiguration.Type proxyType = toMockServerType(getProxyProtocol(proxyProtocol));
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
            return Optional.of(proxyConfiguration(proxyType,
                proxyHost + ":" + getProxyPort(proxyPort, getProxyProtocol(proxyProtocol)),
                proxyUser, proxyPassword));
        } else {
            return Optional.of(proxyConfiguration(proxyType,
                proxyHost + ":" + getProxyPort(proxyPort, getProxyProtocol(proxyProtocol))));
        }
    }

    private Optional<ProxyConfiguration> useProxyAsEnvVar(String envProxyType) {
        String httpProxyAsEnv = System.getenv(envProxyType);

        if (StringUtils.isEmpty(httpProxyAsEnv)) {
            return Optional.empty();
        }

        URI proxyAsUri = URI.create(httpProxyAsEnv);

        if (proxyAsUri.getHost() == null || proxyAsUri.getScheme() == null) {
            throw new TigerProxyToForwardProxyException("No proxy host or no proxy protocol specified.");
        }

        ProxyConfiguration.Type proxyType = toMockServerType(getProxyProtocol(proxyAsUri.getScheme()));
        String proxyUsernamePassword = proxyAsUri.getUserInfo();
        String proxyPort = getProxyPort(String.valueOf(proxyAsUri.getPort()),
            getProxyProtocol(proxyAsUri.getScheme()));

        if (proxyUsernamePassword == null) {
            return Optional.of(proxyConfiguration(proxyType,
                proxyAsUri.getHost() + ":" + proxyPort));
        } else if (!proxyUsernamePassword.contains(":")) {
            throw new TigerProxyToForwardProxyException(
                "Could not convert proxy configuration: either username or password are not present in the env variable");
        } else {
            return Optional.of(proxyConfiguration(proxyType,
                proxyAsUri.getHost() + ":" + proxyPort, proxyUsernamePassword.split(":")[0],
                proxyUsernamePassword.split(":")[1]));
        }
    }

    private ProxyConfiguration.Type toMockServerType(TigerProxyType type) throws TigerUnknownProtocolException {
        if (type == TigerProxyType.HTTP) {
            return HTTP;
        } else if (type == TigerProxyType.HTTPS) {
            return ProxyConfiguration.Type.HTTPS;
        } else {
            throw new TigerUnknownProtocolException(
                "Protocol of type " + type.toString() + " not specified for proxies");
        }
    }

    private Optional<ProxyConfiguration> convertSystemProxyConfig() {
        return useProxyWithSystemProperties("http")
            .or(() -> useProxyWithSystemProperties("https"))
            .or(() -> useProxyAsEnvVar("http_proxy"))
            .or(() -> useProxyAsEnvVar("https_proxy"));
    }

    private TigerProxyType getProxyProtocol(String proxyProtocol) {
        if (proxyProtocol.equalsIgnoreCase("http")) {
            return TigerProxyType.HTTP;
        } else if (proxyProtocol.equalsIgnoreCase("https")) {
            return TigerProxyType.HTTPS;
        } else {
            throw new TigerUnknownProtocolException(
                "Protocol of type " + proxyProtocol + " not specified for proxies");
        }
    }

    private String getProxyPort(String proxyPort, TigerProxyType type) {
        if (proxyPort == null || proxyPort.equals("null") || proxyPort.equals("-1")) {
            if (type == TigerProxyType.HTTP) {
                return "80";
            } else if (type == TigerProxyType.HTTPS) {
                return "443";
            }
        }
        return proxyPort;
    }
}
