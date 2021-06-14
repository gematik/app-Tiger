/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import de.gematik.rbellogger.data.RbelMessage;
import de.gematik.test.tiger.proxy.data.TigerRoute;
import java.security.Key;
import java.util.List;

public interface ITigerProxy {

    TigerRoute addRoute(String sourceHost, String targetHost);

    void removeRoute(String routeId);

    void addRbelMessageListener(IRbelMessageListener listener);

    void removeRbelMessageListener(IRbelMessageListener listener);

    String getBaseUrl();

    int getPort();

    List<RbelMessage> getRbelMessages();

    void addKey(String keyid, Key key);

    List<TigerRoute> getRoutes();
}
