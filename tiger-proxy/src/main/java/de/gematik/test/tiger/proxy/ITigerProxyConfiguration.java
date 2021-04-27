package de.gematik.test.tiger.proxy;

public interface ITigerProxyConfiguration {
    void addRoute(String urlRegexPattern, String targetUrl, boolean rbelEnabled);
    void addRbelMessageListener(IRbelMessageListener listener);
    void removeRbelMessageListener(IRbelMessageListener listener);
}
