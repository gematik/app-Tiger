package de.gematik.test.tiger.testenvmgr;

import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.config.Configuration;
import java.util.List;

public interface ITigerTestEnvMgr {

    Configuration getConfiguration();

    List<CfgServer> getTestEnvironment();

    void setUpEnvironment();

    void start(final CfgServer srv);

    void shutDown(final CfgServer srv);

}
