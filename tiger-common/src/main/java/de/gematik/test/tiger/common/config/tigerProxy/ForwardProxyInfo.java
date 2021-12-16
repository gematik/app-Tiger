/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.config.tigerProxy;

import static org.mockserver.proxyconfiguration.ProxyConfiguration.Type.HTTP;
import static org.mockserver.proxyconfiguration.ProxyConfiguration.proxyConfiguration;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import de.gematik.test.tiger.common.exceptions.TigerProxyToForwardProxyException;
import de.gematik.test.tiger.common.exceptions.TigerUnknownProtocolException;
import java.net.URI;
import java.util.Objects;
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
            if (System.getProperty("http.proxyHost") != null) {
                return useProxyWithSystemProperties();
            } else if (System.getenv("http_proxy") != null) {
                return useProxySetAsEnvVar();
            }
        } else {
            return Optional.of(proxyConfiguration(
                Optional.ofNullable(type)
                    .map(this::toMockServerType)
                    .orElse(ProxyConfiguration.Type.HTTPS),
                hostname + ":" + Objects.requireNonNullElse(port, 80),
                username, password));
        }
        return Optional.empty();
    }

    private Optional<ProxyConfiguration> useProxyWithSystemProperties() {
        String proxyHost = System.getProperty("http.proxyHost");
        String proxyPort = System.getProperty("http.proxyPort");
        String proxyUser = System.getProperty("http.proxyUser");
        String proxyPassword = System.getProperty("http.proxyPassword");

        if (proxyUser != null || proxyPassword != null) {
            if (proxyUser == null) {
                throw new TigerProxyToForwardProxyException(
                    "Could not convert proxy configuration: proxyUser == null, proxyPassword != null");
            } else if (proxyPassword == null) {
                throw new TigerProxyToForwardProxyException(
                    "Could not convert proxy configuration: proxyUser != null, proxyPassword == null");
            }
            return Optional.of(proxyConfiguration(HTTP, proxyHost + ":" + Objects.requireNonNullElse(proxyPort, 80),
                proxyUser, proxyPassword));
        } else {
            return Optional.of(proxyConfiguration(HTTP, proxyHost + ":" + Objects.requireNonNullElse(proxyPort, 80)));
        }
    }

    private Optional<ProxyConfiguration> useProxySetAsEnvVar() {
        String httpProxyAsEnv = System.getenv("http_proxy");
        URI proxyAsUri = URI.create(httpProxyAsEnv);
        String proxyUsernamePassword = proxyAsUri.getUserInfo();

        if (proxyAsUri.getHost() == null || proxyAsUri.getScheme() == null) {
            throw new TigerProxyToForwardProxyException("No proxy host or no proxy protocol specified.");
        }
        if (proxyUsernamePassword == null) {
            return Optional.of(proxyConfiguration(HTTP,
                proxyAsUri.getHost() + ":" + ((proxyAsUri.getPort() == -1) ? 80 : proxyAsUri.getPort())));
        } else if (!proxyUsernamePassword.contains(":")) {
            throw new TigerProxyToForwardProxyException(
                "Could not convert proxy configuration: either username or password are not present in the env variable");
        } else {
            return Optional.of(proxyConfiguration(HTTP,
                proxyAsUri.getHost() + ":" + ((proxyAsUri.getPort() == -1) ? 80 : proxyAsUri.getPort()),
                proxyUsernamePassword.split(":")[0],
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
}
