package de.gematik.test.tiger.proxy;

import de.gematik.rbellogger.data.RbelElement;
import java.util.List;

public interface ITigerProxy {

    void addRoute(String urlRegexPattern, String targetUrl, boolean rbelEnabled);

    void addRbelMessageListener(IRbelMessageListener listener);

    void removeRbelMessageListener(IRbelMessageListener listener);

    String getBaseUrl();

    int getPort();

    List<RbelElement> getRbelMessages();
}
