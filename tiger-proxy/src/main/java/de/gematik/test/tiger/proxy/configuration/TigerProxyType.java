package de.gematik.test.tiger.proxy.configuration;

import org.mockserver.proxyconfiguration.ProxyConfiguration;

public enum TigerProxyType {
    HTTP, HTTPS;

    public ProxyConfiguration.Type toMockServerType() {
        if (this == HTTP) {
            return ProxyConfiguration.Type.HTTP;
        } else {
            return ProxyConfiguration.Type.HTTPS;
        }
    }
}
