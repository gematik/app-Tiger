/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr;

import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.config.Configuration;
import java.util.List;

public interface ITigerTestEnvMgr {

    Configuration getConfiguration();

    List<CfgServer> getTestEnvironment();

    void setUpEnvironment();

    void start(final CfgServer srv, final Configuration cfg);

    void shutDown(final CfgServer srv);

}
