/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr;

import de.gematik.test.tiger.testenvmgr.config.Configuration;

public interface ITigerTestEnvMgr {

    Configuration getConfiguration();

    void setUpEnvironment();

    void shutDown();
}
