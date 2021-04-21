package de.gematik.test.tiger.proxy;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import com.github.tomakehurst.wiremock.WireMockServer;

public class TigerProxy {

    public TigerProxy() {
        final WireMockServer wireMockServer = new WireMockServer(
            options().port(3129).httpsPort(3130).proxyVia("192.168.230.85", 3128)
                .trustAllProxyTargets(true));
        wireMockServer.start();
        // basic prove of concept
        wireMockServer
            .stubFor(any(urlMatching("/idp.*"))
                .willReturn(aResponse().proxiedFrom("https://orf.at")));
        // probably realized with https://laptrinhx.com/wiremock-with-dynamic-proxies-1687621799/
    }
}
