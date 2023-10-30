/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.configuration;

import static org.mockserver.proxyconfiguration.ProxyConfiguration.Type.HTTP;
import static org.mockserver.proxyconfiguration.ProxyConfiguration.proxyConfiguration;
import de.gematik.test.tiger.common.config.TigerConfigurationException;
import de.gematik.test.tiger.common.data.config.tigerProxy.ForwardProxyInfo;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyType;
import de.gematik.test.tiger.common.exceptions.TigerProxyToForwardProxyException;
import de.gematik.test.tiger.common.exceptions.TigerUnknownProtocolException;
import java.net.URI;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.configuration.Configuration;
import org.mockserver.proxyconfiguration.ProxyConfiguration;

public class ProxyConfigurationConverter {

    private ProxyConfigurationConverter() {}

    public static Optional<ProxyConfiguration> convertForwardProxyConfigurationToMockServerConfiguration(TigerProxyConfiguration tpConfig) {
        return Optional.ofNullable(tpConfig.getForwardToProxy())
            .flatMap(ProxyConfigurationConverter::createMockServerProxyConfiguration);
    }

    public static Configuration convertToMockServerConfiguration(TigerProxyConfiguration tpConfig) {
        Configuration config = Configuration.configuration();
        convertForwardProxyConfigurationToMockServerConfiguration(tpConfig).ifPresent(proxyCfg -> {
            switch(proxyCfg.getType()) {
                case HTTP:
                    config.forwardHttpProxy(proxyCfg.getProxyAddress());
                    break;
                case HTTPS:
                    config.forwardHttpsProxy(proxyCfg.getProxyAddress());
                    break;
                case SOCKS5:
                    throw new TigerConfigurationException("Socks Proxies are not currently supported!");
            }

        });
        return config;
    }

    public static ProxyConfiguration.Type toMockServerType(TigerProxyType type) throws TigerUnknownProtocolException {
        if (type == TigerProxyType.HTTP) {
            return HTTP;
        } else if (type == TigerProxyType.HTTPS) {
            return ProxyConfiguration.Type.HTTPS;
        } else {
            throw new TigerUnknownProtocolException(
                "Protocol of type " + type.toString() + " not specified for proxies");
        }
    }

    public static Optional<ProxyConfiguration> createMockServerProxyConfiguration(ForwardProxyInfo forwardProxyInfo) {
        if (StringUtils.isEmpty(forwardProxyInfo.getHostname())) {
            return Optional.empty();
        }
        if (StringUtils.equals(forwardProxyInfo.getHostname(), "$SYSTEM")) {
            return convertSystemProxyConfig(forwardProxyInfo);
        } else {
            return Optional.of(proxyConfiguration(
                Optional.ofNullable(forwardProxyInfo.getType())
                    .map(ProxyConfigurationConverter::toMockServerType)
                    .orElse(ProxyConfiguration.Type.HTTPS),
                forwardProxyInfo.getHostname() + ":" + forwardProxyInfo.calculateProxyPort(),
                forwardProxyInfo.getUsername(), forwardProxyInfo.getPassword()));
        }
    }

    public static Optional<ProxyConfiguration> useProxyWithSystemProperties(String proxyProtocol, ForwardProxyInfo forwardProxyInfo) {
        ProxyConfiguration.Type proxyType = toMockServerType(forwardProxyInfo.getProxyProtocol(proxyProtocol));
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
                proxyHost + ":" + ForwardProxyInfo.mapProxyPort(proxyPort, forwardProxyInfo.getProxyProtocol(proxyProtocol)),
                proxyUser, proxyPassword));
        } else {
            return Optional.of(proxyConfiguration(proxyType,
                proxyHost + ":" + ForwardProxyInfo.mapProxyPort(proxyPort, forwardProxyInfo.getProxyProtocol(proxyProtocol))));
        }
    }

    public static Optional<ProxyConfiguration> useProxyAsEnvVar(ForwardProxyInfo forwardProxyInfo, String envProxyType) {
        String httpProxyHostFromEnv = System.getenv(envProxyType);

        if (StringUtils.isEmpty(httpProxyHostFromEnv)) {
            return Optional.empty();
        }

        URI proxyAsUri = URI.create(httpProxyHostFromEnv);

        if (proxyAsUri.getHost() == null || proxyAsUri.getScheme() == null) {
            throw new TigerProxyToForwardProxyException("No proxy host or no proxy protocol specified.");
        }

        ProxyConfiguration.Type proxyType = toMockServerType(forwardProxyInfo.getProxyProtocol(proxyAsUri.getScheme()));
        String proxyUsernamePassword = proxyAsUri.getUserInfo();
        String proxyPort = ForwardProxyInfo.mapProxyPort(String.valueOf(proxyAsUri.getPort()),
            forwardProxyInfo.getProxyProtocol(proxyAsUri.getScheme()));

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

    public static Optional<ProxyConfiguration> convertSystemProxyConfig(ForwardProxyInfo forwardProxyInfo) {
        return useProxyWithSystemProperties("http", forwardProxyInfo)
            .or(() -> useProxyWithSystemProperties("https", forwardProxyInfo))
            .or(() -> useProxyAsEnvVar(forwardProxyInfo, "http_proxy"))
            .or(() -> useProxyAsEnvVar(forwardProxyInfo, "https_proxy"));
    }
}
