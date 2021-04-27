package de.gematik.test.tiger.proxy;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.http.HttpHost;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class TestTigerProxy {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(options().dynamicPort());

    @Before
    public void setupBackendServer() {
        wireMockRule.stubFor(get(urlEqualTo("/foobar"))
            .willReturn(aResponse()
                .withStatus(666)
                .withHeader("foo", "bar1", "bar2")
                .withBody("{\"foo\":\"bar\"}")));
    }

    @Test
    public void useAsWebProxyServer_shouldForward() throws UnirestException {
        final TigerProxy tigerProxy = new TigerProxy(Map.of("backend", "http://localhost:" + wireMockRule.port()));

        Unirest.setProxy(new HttpHost("localhost", tigerProxy.getPort()));

        final HttpResponse<JsonNode> response = Unirest.get("http://backend/foobar")
            .asJson();

        assertThat(response.getStatus()).isEqualTo(666);
        assertThat(response.getBody().getObject().get("foo").toString()).isEqualTo("bar");
    }

    @Test
    public void requestAndResponseThroughWebProxy_shouldGiveRbelObjects() throws UnirestException {
        final TigerProxy tigerProxy = new TigerProxy(Map.of("backend", "http://localhost:" + wireMockRule.port()));

        Unirest.setProxy(new HttpHost("localhost", tigerProxy.getPort()));
        Unirest.get("http://backend/foobar").asString().getBody();

        assertThat(tigerProxy.getRbelMessages().get(1)
            .getFirst("body").get()
            .getFirst("foo").get().getContent()
        ).isEqualTo("bar");
    }

    @Test
    public void registerListenerThenSentRequest_shouldTriggerListener() throws UnirestException {
        AtomicInteger callCounter = new AtomicInteger(0);

        final TigerProxy tigerProxy = new TigerProxy(Map.of("backend", "http://localhost:" + wireMockRule.port()));
        tigerProxy.addRbelMessageListener(message -> callCounter.incrementAndGet());

        Unirest.setProxy(new HttpHost("localhost", tigerProxy.getPort()));
        Unirest.get("http://backend/foobar").asString().getBody();

        assertThat(callCounter.get()).isEqualTo(2);
    }
}
