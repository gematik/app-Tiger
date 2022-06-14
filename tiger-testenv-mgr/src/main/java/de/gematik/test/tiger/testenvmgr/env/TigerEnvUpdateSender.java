/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.env;

import de.gematik.test.tiger.testenvmgr.servers.TigerServerLogListener;

public interface TigerEnvUpdateSender {

    void registerNewListener(TigerUpdateListener listener);
    void registerLogListener(TigerServerLogListener listener);
}
