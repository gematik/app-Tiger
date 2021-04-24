package de.gematik.test.tiger.proxy;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.util.Map;

public class TigerProxy {

    private final WireMockServer wireMockServer;

    public TigerProxy(Map<String, String> mappings) {
        wireMockServer = new WireMockServer(wireMockConfig()
            .dynamicPort()
            .extensions(WiremockProxyUrlTransformer.class));
        wireMockServer.start();

        WiremockProxyUrlTransformer.URL_MAP.putAll(mappings);
//        wireMockServer
//            .stubFor(any(urlMatching(".*"))
//                .willReturn(aResponse()
//                    .withTransformers(WiremockProxyUrlTransformer.EXTENSION_NAME)
//                ));
    }

    public String getBaseUrl() {
        return "http://localhost:" + wireMockServer.port();
    }

    public int getPort() {
        return wireMockServer.port();
    }
}
