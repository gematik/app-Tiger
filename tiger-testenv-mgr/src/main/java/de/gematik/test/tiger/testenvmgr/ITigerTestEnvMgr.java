package de.gematik.test.tiger.testenvmgr;

import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.config.Configuration;

public interface ITigerTestEnvMgr {

    Configuration getConfiguration();

    void setUpEnvironment();

    void start(final CfgServer srv);

    void shutDown(final CfgServer srv);

}
