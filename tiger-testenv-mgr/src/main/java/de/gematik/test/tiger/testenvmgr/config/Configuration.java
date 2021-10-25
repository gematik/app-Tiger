/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.config;

import de.gematik.test.tiger.common.config.tigerProxy.TigerProxyConfiguration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class Configuration {

    private boolean localProxyActive = true;
    private Map<String, CfgServer> servers = new HashMap<>();
    private List<CfgEnvSets> envSets = new ArrayList<>();
    private TigerProxyConfiguration tigerProxy;
}
