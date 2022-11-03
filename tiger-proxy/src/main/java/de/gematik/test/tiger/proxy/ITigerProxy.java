/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;


import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.modifier.RbelModificationDescription;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
import java.security.Key;
import java.util.Deque;
import java.util.List;

public interface ITigerProxy {

    TigerRoute addRoute(TigerRoute tigerRoute);

    void removeRoute(String routeId);

    void addRbelMessageListener(IRbelMessageListener listener);

    void removeRbelMessageListener(IRbelMessageListener listener);

    String getBaseUrl();

    int getProxyPort();

    Deque<RbelElement> getRbelMessages();

    void addKey(String keyid, Key key);

    List<TigerRoute> getRoutes();

    void clearAllRoutes();

    RbelModificationDescription addModificaton(RbelModificationDescription modification);

    List<RbelModificationDescription> getModifications();

    void removeModification(String modificationId);
}
