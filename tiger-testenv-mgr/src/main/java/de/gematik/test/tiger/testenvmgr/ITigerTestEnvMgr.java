/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr;

import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.config.Configuration;
import java.util.Map;

public interface ITigerTestEnvMgr {

    Configuration getConfiguration();

    Map<String, CfgServer> getTestEnvironment();

    void setUpEnvironment();

    void start(final String serverId, final CfgServer srv, final Configuration cfg);

    void shutDown(final String serverId);

}
