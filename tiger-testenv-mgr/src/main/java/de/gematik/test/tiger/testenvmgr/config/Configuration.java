/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.config;

import de.gematik.test.tiger.common.config.tigerProxy.TigerProxyConfiguration;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Slf4j
@Builder
@AllArgsConstructor
public class Configuration {

    private final Map<String, CfgServer> servers = new HashMap<>();
    private final List<CfgEnvSets> envSets = new ArrayList<>();
    @Builder.Default
    private boolean localProxyActive = true;
    private TigerProxyConfiguration tigerProxy;

}
