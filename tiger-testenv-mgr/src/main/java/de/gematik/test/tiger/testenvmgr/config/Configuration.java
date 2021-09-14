/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.config;

import de.gematik.test.tiger.common.config.tigerProxy.TigerProxyConfiguration;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class Configuration {

    private boolean localProxyActive = true;
    private List<CfgServer> servers = new ArrayList<>();
    private List<CfgEnvSets> envSets = new ArrayList<>();
    private TigerProxyConfiguration tigerProxy;
}
