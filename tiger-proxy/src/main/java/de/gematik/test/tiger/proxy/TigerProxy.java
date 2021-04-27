package de.gematik.test.tiger.proxy;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.captures.WiremockCapture;
import de.gematik.rbellogger.converter.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import java.util.List;
import java.util.Map;

public class TigerProxy {

    private final WiremockCapture wiremockCapture;
    private final RbelLogger rbelLogger;

    public TigerProxy(Map<String, String> mappings) {

        wiremockCapture = WiremockCapture.builder()
            .wireMockConfiguration(wireMockConfig()
                .dynamicPort()
                .extensions(WiremockProxyUrlTransformer.class))
            .build();

        rbelLogger = RbelLogger.build(new RbelConfiguration()
            .addCapturer(wiremockCapture));

        WiremockProxyUrlTransformer.URL_MAP.putAll(mappings);
    }

    public String getBaseUrl() {
        return "http://localhost:" + wiremockCapture.getWireMockServer().port();
    }

    public int getPort() {
        return wiremockCapture.getWireMockServer().port();
    }

    public List<RbelElement> getRbelMessages() {
        return rbelLogger.getMessageHistory();
    }
}
