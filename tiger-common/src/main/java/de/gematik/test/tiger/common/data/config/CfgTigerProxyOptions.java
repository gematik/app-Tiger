package de.gematik.test.tiger.common.data.config;

import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import lombok.Data;

@Data
public class CfgTigerProxyOptions {
    private int serverPort = -1;

    /**
     * used to overwrite proxyCfg with values that allow to reverse proxy the given server node
     */
    private String proxiedServer;

    /**
     * used to overwrite proxyCfg with correct route table, as we cant know whether its http or https, we introduced
     * this field and default to http
     */
    private String proxyProtocol = "http";

    TigerProxyConfiguration proxyCfg;
}
