package de.gematik.test.tiger.testenvmgr.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import de.gematik.test.tiger.proxy.configuration.TigerProxyConfiguration;
import lombok.Data;

@Data
@JsonInclude(Include.NON_NULL)
public class CfgStandaloneProxy {
    private CfgStandaloneServer server;

    private TigerProxyConfiguration tigerProxy;
}

