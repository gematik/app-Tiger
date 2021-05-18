/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import de.gematik.rbellogger.data.RbelElement;
import java.security.Key;
import java.util.List;

public interface ITigerProxy {

    void addRoute(String sourceHost, String targetHost);

    void removeRoute(String sourceHost);

    void addRbelMessageListener(IRbelMessageListener listener);

    void removeRbelMessageListener(IRbelMessageListener listener);

    String getBaseUrl();

    int getPort();

    List<RbelElement> getRbelMessages();

    void addKey(String keyid, Key key);
}
