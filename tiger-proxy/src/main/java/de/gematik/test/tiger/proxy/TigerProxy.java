package de.gematik.test.tiger.proxy;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.captures.WiremockCapture;
import de.gematik.rbellogger.converter.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHttpMessage;
import de.gematik.test.tiger.proxy.configuration.TigerProxyConfiguration;
import de.gematik.test.tiger.proxy.wiremockUtils.WiremockProxyUrlTransformer;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class TigerProxy implements ITigerProxy {

    private final WiremockCapture wiremockCapture;
    private final RbelLogger rbelLogger;
    private final WiremockProxyUrlTransformer urlTransformer;
    private final List<IRbelMessageListener> rbelMessageListeners = new ArrayList<>();

    public TigerProxy(TigerProxyConfiguration configuration) {
        urlTransformer = new WiremockProxyUrlTransformer();

        final WireMockConfiguration wireMockConfiguration = wireMockConfig()
            .dynamicPort()
            .dynamicHttpsPort()
            .trustAllProxyTargets(true)
            .enableBrowserProxying(true)
            .extensions(urlTransformer);

        if (configuration.getForwardToProxy() != null
            && !StringUtils.isEmpty(configuration.getForwardToProxy().getHostname())
            && configuration.getForwardToProxy().getPort() != null) {
            wireMockConfiguration
                .proxyVia(configuration.getForwardToProxy().getHostname(),
                    configuration.getForwardToProxy().getPort());
        }

        wiremockCapture = WiremockCapture.builder()
            .wireMockConfiguration(wireMockConfiguration)
            .build();

        rbelLogger = RbelLogger.build(new RbelConfiguration()
            .addCapturer(wiremockCapture));

        rbelLogger.getRbelConverter().registerListener(RbelHttpMessage.class, (message, context) ->
            rbelMessageListeners.forEach(listener -> listener.triggerNewReceivedMessage(message)));

        urlTransformer.getUrlMap().putAll(configuration.getProxyRoutes());
    }

    @Override
    public String getBaseUrl() {
        return "http://localhost:" + wiremockCapture.getWireMockServer().port();
    }

    @Override
    public int getPort() {
        return wiremockCapture.getWireMockServer().port();
    }

    public int getTslPort() {
        return wiremockCapture.getWireMockServer().httpsPort();
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
