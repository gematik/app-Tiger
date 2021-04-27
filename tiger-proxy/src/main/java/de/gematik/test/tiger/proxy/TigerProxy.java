package de.gematik.test.tiger.proxy;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.captures.WiremockCapture;
import de.gematik.rbellogger.converter.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHttpMessage;
import de.gematik.test.tiger.proxy.wiremockUtils.WiremockProxyUrlTransformer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

// TODO allow to configure an upstream proxy for internet connections
@Slf4j
public class TigerProxy implements ITigerProxy {

    private final WiremockCapture wiremockCapture;
    private final RbelLogger rbelLogger;
    private final WiremockProxyUrlTransformer urlTransformer;
    private final List<IRbelMessageListener> rbelMessageListeners = new ArrayList<>();

    public TigerProxy(Map<String, String> mappings) {
        urlTransformer = new WiremockProxyUrlTransformer();

        wiremockCapture = WiremockCapture.builder()
            .wireMockConfiguration(wireMockConfig()
                .dynamicPort()
                .extensions(urlTransformer))
            .build();

        rbelLogger = RbelLogger.build(new RbelConfiguration()
            .addCapturer(wiremockCapture));

        rbelLogger.getRbelConverter().registerListener(RbelHttpMessage.class, (message, context) ->
            rbelMessageListeners.forEach(listener -> listener.triggerNewReceivedMessage(message)));

        urlTransformer.getUrlMap().putAll(mappings);
    }

    @Override
    public String getBaseUrl() {
        return "http://localhost:" + wiremockCapture.getWireMockServer().port();
    }

    @Override
    public int getPort() {
        return wiremockCapture.getWireMockServer().port();
    }

    @Override
    public List<RbelElement> getRbelMessages() {
        return rbelLogger.getMessageHistory();
    }

    @Override
    public void addRoute(String urlRegexPattern, String targetUrl, boolean rbelEnabled) {
        //TODO urlRegexPattern wird momentan einfach fix ausgewertet. Da müssen wir bei Gelegenheit mal drüber reden
        //TODO rbelEnabled wird ignoriert.
        log.info("adding route " + urlRegexPattern + " -> " + targetUrl);
        urlTransformer.getUrlMap().put(urlRegexPattern, targetUrl);
    }

    @Override
    public void addRbelMessageListener(IRbelMessageListener listener) {
        rbelMessageListeners.add(listener);
    }

    @Override
    public void removeRbelMessageListener(IRbelMessageListener listener) {
        rbelMessageListeners.remove(listener);
    }
}
