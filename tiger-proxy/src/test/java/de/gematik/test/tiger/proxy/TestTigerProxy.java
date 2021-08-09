/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import de.gematik.rbellogger.configuration.RbelFileSaveInfo;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.util.CryptoLoader;
import de.gematik.rbellogger.util.RbelPkiIdentity;
import de.gematik.test.tiger.proxy.configuration.TigerProxyConfiguration;
import de.gematik.test.tiger.proxy.data.TigerRoute;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyConfigurationException;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockserver.model.MediaType;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.IOException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

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
                .withStatusMessage("EVIL")
                .withHeader("foo", "bar1", "bar2")
                .withBody("{\"foo\":\"bar\"}")));
    }

    @Test
    public void useAsWebProxyServer_shouldForward() {
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + wireMockRule.port())
                .build()))
            .proxyLogLevel("DEBUG")
            .build());

        Unirest.config().reset();
        Unirest.config().proxy("localhost", tigerProxy.getPort());

        final HttpResponse<JsonNode> response = Unirest.get("http://backend/foobar")
            .asJson();

        assertThat(response.getStatus()).isEqualTo(666);
        assertThat(response.getBody().getObject().get("foo").toString()).isEqualTo("bar");

        assertThat(tigerProxy.getRbelMessages().get(0).getFacetOrFail(RbelTcpIpMessageFacet.class).getReceiverHostname())
            .isEqualTo(new RbelHostname("backend", 80));
        assertThat(tigerProxy.getRbelMessages().get(0).getFacetOrFail(RbelTcpIpMessageFacet.class).getSender().seekValue())
            .isEmpty();
        assertThat(tigerProxy.getRbelMessages().get(1).getFacetOrFail(RbelTcpIpMessageFacet.class).getSenderHostname())
            .isEqualTo(new RbelHostname("backend", 80));
        assertThat(tigerProxy.getRbelMessages().get(1).getFacetOrFail(RbelTcpIpMessageFacet.class).getReceiver().seekValue())
            .isEmpty();

        new RbelHtmlRenderer().doRender(tigerProxy.getRbelMessages());
    }

    @Test
    public void routeLessTraffic_shouldLogInRbel() {
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://foo")
                .to("http://bar")
                .build()))
            .proxyLogLevel("DEBUG")
            .build());

        Unirest.config().reset();
        Unirest.config().proxy("localhost", tigerProxy.getPort());

        final HttpResponse<JsonNode> response = Unirest.get("http://localhost:" + wireMockRule.port() + "/foobar")
            .asJson();

        assertThat(response.getStatus()).isEqualTo(666);

        assertThat(tigerProxy.getRbelMessages().get(1).getFacetOrFail(RbelHttpResponseFacet.class)
            .getResponseCode().getRawStringContent())
            .isEqualTo("666");
    }

    @Test
    public void routeLessTrafficHttps_shouldLogInRbel() {
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://foo")
                .to("http://bar")
                .build()))
            .proxyLogLevel("DEBUG")
            .build());

        Unirest.config().reset();
        Unirest.config().proxy("localhost", tigerProxy.getPort());
        Unirest.config().verifySsl(false);

        final HttpResponse<JsonNode> response = Unirest.get("https://localhost:" + wireMockRule.httpsPort() + "/foobar")
            .asJson();

        assertThat(response.getStatus()).isEqualTo(666);

        assertThat(tigerProxy.getRbelMessages().get(1).getFacetOrFail(RbelHttpResponseFacet.class)
            .getResponseCode().getRawStringContent())
            .isEqualTo("666");
    }

    @Test
    public void addAlreadyExistingRoute_shouldThrowException() {
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + wireMockRule.port())
                .build()))
            .build());

        assertThatThrownBy(() ->
            tigerProxy.addRoute(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + wireMockRule.port())
                .build()))
            .isInstanceOf(TigerProxyConfigurationException.class);
    }

    @Test
    public void binaryMessage_shouldGiveBinaryResult() {
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + wireMockRule.port())
                .build()))
            .build());

        Unirest.config().reset();
        Unirest.config().proxy("localhost", tigerProxy.getPort());
        wireMockRule.stubFor(get(urlEqualTo("/binary"))
            .willReturn(aResponse()
                .withHeader("content-type", MediaType.APPLICATION_OCTET_STREAM.toString())
                .withBody("Hallo".getBytes())));

        Unirest.get("http://backend/binary").asBytes();

        assertThat(tigerProxy.getRbelMessages().get(tigerProxy.getRbelMessages().size() - 1)
            .findRbelPathMembers("$.body").get(0)
            .getRawContent())
            .containsExactly("Hallo".getBytes());
    }

    @Test
    public void useTslBetweenClientAndProxy_shouldForward() throws UnirestException {
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("https://backend")
                .to("http://localhost:" + wireMockRule.port())
                .build()))
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
    public void customRsaCaFileInTruststore_shouldVerifyConnection() throws UnirestException, IOException {
        final RbelPkiIdentity ca = CryptoLoader.getIdentityFromP12(
            FileUtils.readFileToByteArray(new File("src/test/resources/selfSignedCa/rootCa.p12")), "00");

        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("https://backend")
                .to("http://localhost:" + wireMockRule.port())
                .build()))
            .serverRootCa(ca)
            .build());

        Unirest.config().reset();
        Unirest.config().proxy("localhost", tigerProxy.getPort());
        Unirest.config().verifySsl(true);
        Unirest.config().sslContext(buildSslContextTrustingCaFile(ca.getCertificate()));

        final kong.unirest.HttpResponse<JsonNode> response = Unirest.get("https://backend/foobar")
            .asJson();

        assertThat(response.getStatus()).isEqualTo(666);
        assertThat(response.getBody().getObject().get("foo").toString()).isEqualTo("bar");
    }

    // TODO really fix this and reactivate @Test
    public void customEccCaFileInTruststore_shouldVerifyConnection() throws UnirestException, IOException {
        final RbelPkiIdentity ca = CryptoLoader.getIdentityFromP12(
            FileUtils.readFileToByteArray(new File("src/test/resources/customCa.p12")), "00");

        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("https://backend")
                .to("http://localhost:" + wireMockRule.port())
                .build()))
            .serverRootCa(ca)
            .build());

        Unirest.config().reset();
        Unirest.config().proxy("localhost", tigerProxy.getPort());
        Unirest.config().verifySsl(true);
        Unirest.config().sslContext(buildSslContextTrustingCaFile(ca.getCertificate()));

        final kong.unirest.HttpResponse<JsonNode> response = Unirest.get("https://backend/foobar")
            .asJson();

        assertThat(response.getStatus()).isEqualTo(666);
        assertThat(response.getBody().getObject().get("foo").toString()).isEqualTo("bar");
    }

    @SneakyThrows
    private SSLContext buildSslContextTrustingCaFile(X509Certificate certificate) {
        TrustManagerFactory tmf = TrustManagerFactory
            .getInstance(TrustManagerFactory.getDefaultAlgorithm());
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null);
        ks.setCertificateEntry("caCert", certificate);

        tmf.init(ks);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, tmf.getTrustManagers(), null);

        return sslContext;
    }

    @Test
    public void useTslBetweenProxyAndServer_shouldForward() throws UnirestException {
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("https://localhost:" + wireMockRule.httpsPort())
                .build()))
            .build());

        Unirest.config().reset();
        Unirest.config().proxy("localhost", tigerProxy.getPort());

        final kong.unirest.HttpResponse<JsonNode> response = Unirest.get("http://backend/foobar")
            .asJson();

        assertThat(response.getStatus()).isEqualTo(666);
        assertThat(response.getBody().getObject().get("foo").toString()).isEqualTo("bar");
    }

    @Test
    public void testTigerWebEndpoing() throws UnirestException {
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .activateRbelEndpoint(true)
            .build());

        Unirest.config().reset();

        assertThat(Unirest.get("http://localhost:" + tigerProxy.getPort() + "/rbel").asString()
            .getBody())
            .contains("<html");

        Unirest.config().reset();
        Unirest.config().proxy("localhost", tigerProxy.getPort());

        assertThat(Unirest.get("http://rbel").asString()
            .getBody())
            .contains("<html");
    }

    @Test
    public void requestAndResponseThroughWebProxy_shouldGiveRbelObjects() throws UnirestException {
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + wireMockRule.port())
                .build()))
            .build());

        Unirest.config().reset();
        Unirest.config().proxy("localhost", tigerProxy.getPort());

        Unirest.get("http://backend/foobar").asString().getBody();

        assertThat(tigerProxy.getRbelMessages().get(1)
            .findRbelPathMembers("$.body.foo.content")
            .get(0).getRawStringContent()
        ).isEqualTo("bar");
    }

    @Test
    public void registerListenerThenSentRequest_shouldTriggerListener() throws UnirestException {
        AtomicInteger callCounter = new AtomicInteger(0);

        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + wireMockRule.port())
                .build()))
            .build());

        tigerProxy.addRbelMessageListener(message -> callCounter.incrementAndGet());

        Unirest.config().reset();
        Unirest.config().proxy("localhost", tigerProxy.getPort());

        Unirest.get("http://backend/foobar").asString().getBody();

        assertThat(callCounter.get()).isEqualTo(2);
    }

    @Test
    public void implicitReverseProxy_shouldForwardReqeust() {
        AtomicInteger callCounter = new AtomicInteger(0);

        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/notAServer")
                .to("http://localhost:" + wireMockRule.port())
                .build()))
            .build());

        tigerProxy.addRbelMessageListener(message -> callCounter.incrementAndGet());

        Unirest.config().reset();
        // no (forward)-proxy! we use the tiger-proxy as a reverse-proxy

        Unirest.get("http://localhost:" + tigerProxy.getPort() + "/notAServer/foobar").asString();

        assertThat(callCounter.get()).isEqualTo(2);
    }

    @Test
    public void blanketRerverseProxy_shouldForwardReqeust() {
        AtomicInteger callCounter = new AtomicInteger(0);

        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("http://localhost:" + wireMockRule.port())
                .build()))
            .build());

        tigerProxy.addRbelMessageListener(message -> callCounter.incrementAndGet());

        Unirest.config().reset();
        // no (forward)-proxy! we use the tiger-proxy as a reverse-proxy
        Unirest.get("http://localhost:" + tigerProxy.getPort() + "/foobar").asString();

        assertThat(callCounter.get()).isEqualTo(2);
    }

    @Test
    public void activateFileSaving_shouldAddRouteTrafficToFile() {
        final String filename = "target/test-log.tgr";
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + wireMockRule.port())
                .build()))
            .fileSaveInfo(RbelFileSaveInfo.builder()
                .writeToFile(true)
                .clearFileOnBoot(true)
                .filename(filename)
                .build())
            .build());

        Unirest.config().reset();
        Unirest.config().proxy("localhost", tigerProxy.getPort());
        Unirest.get("http://backend/foobar").asString();

        await()
            .atMost(2, TimeUnit.SECONDS)
            .until(() -> new File(filename).exists());
    }

    @Test
    public void activateFileSaving_shouldAddRoutelessTrafficToFile() {
        final String filename = "target/test-log.tgr";
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .activateForwardAllLogging(true)
            .fileSaveInfo(RbelFileSaveInfo.builder()
                .writeToFile(true)
                .clearFileOnBoot(true)
                .filename(filename)
                .build())
            .build());

        Unirest.config().reset();
        Unirest.config().proxy("localhost", tigerProxy.getPort());
        Unirest.get("http://localhost:" + wireMockRule.port() + "/foobar").asString();

        await()
            .atMost(2, TimeUnit.SECONDS)
            .until(() -> new File(filename).exists());
    }
}
