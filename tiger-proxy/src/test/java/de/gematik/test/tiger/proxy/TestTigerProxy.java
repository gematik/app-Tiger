/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import de.gematik.rbellogger.data.RbelBinaryElement;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.test.tiger.proxy.configuration.ForwardProxyInfo;
import de.gematik.test.tiger.proxy.configuration.TigerProxyConfiguration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockserver.model.MediaType;

public class TestTigerProxy {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(options()
        .dynamicPort()
        .dynamicHttpsPort());

    @Before
    public void setupBackendServer() {
        wireMockRule.stubFor(get(urlEqualTo("/foobar"))
            .willReturn(aResponse()
                .withStatus(666)
                .withHeader("foo", "bar1", "bar2")
                .withBody("{\"foo\":\"bar\"}")));
    }

    @Test
    public void useAsWebProxyServer_shouldForward() {
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(Map.of("http://backend", "http://localhost:" + wireMockRule.port()))
            .build());

        Unirest.config().reset();
        Unirest.config().proxy("localhost", tigerProxy.getPort());

        final HttpResponse<JsonNode> response = Unirest.get("http://backend/foobar")
            .asJson();

        assertThat(response.getStatus()).isEqualTo(666);
        assertThat(response.getBody().getObject().get("foo").toString()).isEqualTo("bar");

        new RbelHtmlRenderer().doRender(tigerProxy.getRbelMessages());
    }

    @Test
    public void binaryMessage_shouldGiveBinaryResult() {
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(Map.of("http://backend", "http://localhost:" + wireMockRule.port()))
            .build());

        Unirest.config().reset();
        Unirest.config().proxy("localhost", tigerProxy.getPort());
        wireMockRule.stubFor(get(urlEqualTo("/binary"))
            .willReturn(aResponse()
                .withHeader("content-type", MediaType.APPLICATION_OCTET_STREAM.toString())
                .withBody(Base64.getEncoder().encode("Hallo".getBytes()))));

        Unirest.get("http://backend/binary").asBytes();

        assertThat(tigerProxy.getRbelMessages().get(tigerProxy.getRbelMessages().size() - 1)
        .findRbelPathMembers("$.body").get(0))
            .isInstanceOf(RbelBinaryElement.class);
    }

    @Test
    public void useTslBetweenClientAndProxy_shouldForward() throws UnirestException {
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(Map.of("https://backend", "http://localhost:" + wireMockRule.port()))
            .build());

        Unirest.config().reset();
        Unirest.config().proxy("localhost", tigerProxy.getPort());
        Unirest.config().verifySsl(false);

        final kong.unirest.HttpResponse<JsonNode> response = Unirest.get("https://backend/foobar")
            .asJson();

        assertThat(response.getStatus()).isEqualTo(666);
        assertThat(response.getBody().getObject().get("foo").toString()).isEqualTo("bar");
    }

    @Test
    public void useTslBetweenProxyAndServer_shouldForward() throws UnirestException {
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(Map.of("http://backend", "https://localhost:" + wireMockRule.httpsPort()))
            .build());

        Unirest.config().reset();
        Unirest.config().proxy("localhost", tigerProxy.getPort());

        final kong.unirest.HttpResponse<JsonNode> response = Unirest.get("http://backend/foobar")
            .asJson();

        assertThat(response.getStatus()).isEqualTo(666);
        assertThat(response.getBody().getObject().get("foo").toString()).isEqualTo("bar");
    }

    @Test
    public void requestAndResponseThroughWebProxy_shouldGiveRbelObjects()
        throws UnirestException {
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(Map.of(
                "http://backend", "http://localhost:" + wireMockRule.port()
            ))
            .build());

        Unirest.config().reset();
        Unirest.config().proxy("localhost", tigerProxy.getPort());

        Unirest.get("http://backend/foobar").asString().getBody();

        assertThat(tigerProxy.getRbelMessages().get(1)
            .getFirst("body").get()
            .getFirst("foo").get()
            .getContent()
        ).isEqualTo("bar");
    }

    @Test
    public void registerListenerThenSentRequest_shouldTriggerListener() throws UnirestException {
        AtomicInteger callCounter = new AtomicInteger(0);

        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(Map.of("http://backend", "http://localhost:" + wireMockRule.port()))
            .build());
        tigerProxy.addRbelMessageListener(message -> callCounter.incrementAndGet());

        Unirest.config().reset();
        Unirest.config().proxy("localhost", tigerProxy.getPort());

        Unirest.get("http://backend/foobar").asString().getBody();

        assertThat(callCounter.get()).isEqualTo(2);
    }

    //    @Test
    public void startProxyFor30s() {
        TigerProxy tp = new TigerProxy(TigerProxyConfiguration.builder()
            .forwardToProxy(new ForwardProxyInfo("192.168.230.85", 3128))
//            .forwardToProxy(new ForwardProxyInfo("192.168.110.10", 3128))
            .proxyRoutes(Map.of(
                "https://magog", "https://google.com",
                "http://magog", "http://google.com",
                "http://tsl", "http://download-ref.tsl.ti-dienste.de",
                "https://tsl", "https://download-ref.tsl.ti-dienste.de"
            )).proxyLogLevel("DEBUG").serverRootCaCertPem("src/main/resources/CertificateAuthorityCertificate.pem")
            .serverRootCaKeyPem("src/main/resources/PKCS8CertificateAuthorityPrivateKey.pem").build());
        System.out.println(tp.getBaseUrl() + " with " + tp.getPort());
        try {
            Thread.sleep(30 * 1_000 * 1_000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
