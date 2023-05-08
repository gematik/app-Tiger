package de.gematik.test.tiger.zion.config;

import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ZionServerConfiguration extends CfgServer {

    private ZionConfiguration zionConfiguration;
}
