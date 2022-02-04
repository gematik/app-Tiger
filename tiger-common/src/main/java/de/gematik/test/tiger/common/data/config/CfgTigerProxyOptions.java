package de.gematik.test.tiger.common.data.config;

import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import lombok.Data;

@Data
public class CfgTigerProxyOptions {

    /**
     * Management-port of the Tiger Proxy.
     */
    private int serverPort = -1;

    /**
     * used to overwrite proxyCfg with values that allow to reverse proxy the given server node.
     */
    private String proxiedServer;

    /**
     * Used to add a route to the tiger-proxy. By default, the healthcheck-url-protocol is used here, or http if none is
     * present. If you want to override this you can do it using this field.
     */
    private String proxiedServerProtocol;

    TigerProxyConfiguration proxyCfg;
}
