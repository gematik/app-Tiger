/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import de.gematik.rbellogger.configuration.RbelFileSaveInfo;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.util.CryptoLoader;
import de.gematik.rbellogger.util.RbelPkiIdentity;
import de.gematik.test.tiger.common.config.tigerProxy.*;
import de.gematik.test.tiger.common.pki.TigerPkiIdentity;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyConfigurationException;
import kong.unirest.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockserver.junit.MockServerRule;
import org.mockserver.model.MediaType;
import org.mockserver.model.SocketAddress;

import javax.net.ssl.*;
import java.io.File;
import java.io.IOException;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;
import static org.mockserver.model.HttpRequest.request;

@Slf4j
public class TestTigerProxy {

    @Rule
    public MockServerRule forwardProxy = new MockServerRule(this);
    @Rule
    public WireMockRule fakeBackendServer = new WireMockRule(options()
        .dynamicPort()
        .dynamicHttpsPort());
    private UnirestInstance unirestInstance;

    @Before
    public void setupBackendServer() {
        log.info("Started Backend-Server on ports {} and {} (https)", fakeBackendServer.port(), fakeBackendServer.httpsPort());
        log.info("Started Forward-Proxy-Server on port {}", forwardProxy.getPort());

        fakeBackendServer.stubFor(get(urlEqualTo("/foobar"))
            .willReturn(aResponse()
                .withStatus(666)
                .withStatusMessage("EVIL")
                .withHeader("foo", "bar1", "bar2")
                .withBody("{\"foo\":\"bar\"}")));
        fakeBackendServer.stubFor(get(urlEqualTo("/deep/foobar"))
            .willReturn(aResponse()
                .withStatus(777)
                .withStatusMessage("DEEPEREVIL")
                .withHeader("foo", "bar1", "bar2")
                .withBody("{\"foo\":\"bar\"}")));

        forwardProxy.getClient().when(request())
            .forward(
                req -> forwardOverriddenRequest(
                    req.withSocketAddress(
                        "localhost", fakeBackendServer.port(), SocketAddress.Scheme.HTTP
                    ))
                    .getHttpRequest());
    }

    @Test
    public void useAsWebProxyServer_shouldForward() {
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
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
    public void forwardProxy_headersShouldBeUntouchedExceptForHost() {
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        Unirest.config().reset();
        Unirest.config().proxy("localhost", tigerProxy.getPort());

        Unirest.get("http://backend/foobar")
            .header("foo", "bar")
            .header("x-forwarded-for", "someStuff")
            .asString();

        assertThat(tigerProxy.getRbelMessages().get(0)
            .findElement("$.header.foo")
            .get().getRawStringContent())
            .isEqualTo("bar");
        assertThat(tigerProxy.getRbelMessages().get(0)
            .findElement("$.header.x-forwarded-for")
            .get().getRawStringContent())
            .isEqualTo("someStuff");
        assertThat(tigerProxy.getRbelMessages().get(0)
            .findElement("$.header.Host")
            .get().getRawStringContent())
            .isEqualTo("localhost:" + fakeBackendServer.port());
    }

    @Test
    public void reverseProxy_headersShouldBeUntouched() {
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        Unirest.config().reset();

        Unirest.get("http://localhost:" + tigerProxy.getPort() + "/foobar")
            .header("foo", "bar")
            .header("x-forwarded-for", "someStuff")
            .asString();

        assertThat(tigerProxy.getRbelMessages().get(0)
            .findElement("$.header.foo")
            .get().getRawStringContent())
            .isEqualTo("bar");
        assertThat(tigerProxy.getRbelMessages().get(0)
            .findElement("$.header.x-forwarded-for")
            .get().getRawStringContent())
            .isEqualTo("someStuff");
        assertThat(tigerProxy.getRbelMessages().get(0)
            .findElement("$.header.Host")
            .get().getRawStringContent())
            .isEqualTo("localhost:" + fakeBackendServer.port());
    }

    @Test
    public void reverseProxy_shouldGiveReceiverAndSenderInRbelMessage() {
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        new UnirestInstance(new Config())
            .get("http://localhost:" + tigerProxy.getPort() + "/foobar").asString();

        assertThat(tigerProxy.getRbelMessages().get(0)
            .findElement("$.recipient")
            .flatMap(RbelElement::seekValue))
            .get()
            .isEqualTo(new RbelHostname("localhost", fakeBackendServer.port()));
        assertThat(tigerProxy.getRbelMessages().get(1)
            .findElement("$.sender")
            .flatMap(RbelElement::seekValue))
            .get()
            .isEqualTo(new RbelHostname("localhost", fakeBackendServer.port()));
    }

    @Test
    public void reverseProxy_shouldUseConfiguredAlternativeNameInTlsCertificate() throws NoSuchAlgorithmException, KeyManagementException {
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .tls(TigerTlsConfiguration.builder()
                .domainName("muahaha")
                .build())
            .build());

        AtomicBoolean verifyWasCalledSuccesfully = new AtomicBoolean(false);
        SSLContext ctx = SSLContext.getInstance("TLSv1.2");
        ctx.init(null, new TrustManager[]{
                new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }

                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        assertThat(chain[0].getSubjectDN().getName())
                            .contains("muahaha");
                        verifyWasCalledSuccesfully.set(true);
                    }

                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
            }, new SecureRandom()
        );
        new UnirestInstance(new Config()
            .sslContext(ctx))
            .get("https://localhost:" + tigerProxy.getPort() + "/foobar").asString();

        assertThat(verifyWasCalledSuccesfully).isTrue();
    }

    @Test
    public void forwardProxy_shouldGiveReceiverAndSenderInRbelMessage() {
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://foo.bar")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        new UnirestInstance(new Config().proxy("localhost", tigerProxy.getPort()))
            .get("http://foo.bar/foobar").asString();

        assertThat(tigerProxy.getRbelMessages().get(0)
            .findElement("$.recipient")
            .flatMap(RbelElement::seekValue))
            .get()
            .isEqualTo(new RbelHostname("foo.bar", 80));
        assertThat(tigerProxy.getRbelMessages().get(1)
            .findElement("$.sender")
            .flatMap(RbelElement::seekValue))
            .get()
            .isEqualTo(new RbelHostname("foo.bar", 80));
    }

    @Test
    public void routeLessTraffic_shouldLogInRbel() {
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://foo")
                .to("http://bar")
                .build()))
            .build());

        Unirest.config().reset();
        Unirest.config().proxy("localhost", tigerProxy.getPort());

        final HttpResponse<JsonNode> response = Unirest.get("http://localhost:" + fakeBackendServer.port() + "/foobar")
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
            .build());

        Unirest.config().reset();
        Unirest.config().proxy("localhost", tigerProxy.getPort());
        Unirest.config().verifySsl(false);

        final HttpResponse<JsonNode> response = Unirest.get("https://localhost:" + fakeBackendServer.httpsPort() + "/foobar")
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
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        assertThatThrownBy(() ->
            tigerProxy.addRoute(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .isInstanceOf(TigerProxyConfigurationException.class);
    }

    @Test
    public void binaryMessage_shouldGiveBinaryResult() {
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        Unirest.config().reset();
        Unirest.config().proxy("localhost", tigerProxy.getPort());
        fakeBackendServer.stubFor(get(urlEqualTo("/binary"))
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
                .to("http://localhost:" + fakeBackendServer.port())
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

    @SneakyThrows
    @Test
    public void serverCertificateChainShouldContainMultipleCertificatesIfGiven() throws UnirestException {
        Security.insertProviderAt(new BouncyCastleJsseProvider(), 1);
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("https://authn.aktor.epa.telematik-test")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .tls(TigerTlsConfiguration.builder()
                .serverIdentity(new TigerPkiIdentity("src/test/resources/rsaStoreWithChain.jks;gematik"))
                .build())
            .build());

        AtomicInteger callCounter = new AtomicInteger(0);
        Unirest.config().reset();
        Unirest.config().verifySsl(true);
        Unirest.config().proxy("localhost", tigerProxy.getPort());
        SSLContext ctx = SSLContext.getInstance("TLSv1.2");
        ctx.init(null, new TrustManager[]{
                new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }

                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        assertThat(chain).hasSize(3);
                        callCounter.incrementAndGet();
                    }

                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
            }, new SecureRandom()
        );
        Unirest.config().sslContext(ctx);

        Unirest.get("https://authn.aktor.epa.telematik-test/foobar").asString();

        await().atMost(2, TimeUnit.SECONDS)
            .until(() -> callCounter.get() > 0);
    }

    @Test
    public void rsaCaFileInP12File_shouldVerifyConnection() throws UnirestException {
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("https://backend")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .tls(TigerTlsConfiguration.builder()
                .serverRootCa(new TigerPkiIdentity("src/test/resources/selfSignedCa/rootCa.p12;00"))
                .build())
            .build());

        final UnirestInstance unirestInstance = new UnirestInstance(
            new Config().proxy("localhost", tigerProxy.getPort())
                .verifySsl(true)
                .sslContext(tigerProxy.buildSslContext()));

        final kong.unirest.HttpResponse<JsonNode> response = unirestInstance.get("https://backend/foobar")
            .asJson();

        assertThat(response.getStatus()).isEqualTo(666);
        assertThat(response.getBody().getObject().get("foo").toString()).isEqualTo("bar");
    }

    @Test
    public void defunctCertificate_expectException() throws UnirestException {
        assertThatThrownBy(() -> new TigerProxy(TigerProxyConfiguration.builder()
            .tls(TigerTlsConfiguration.builder()
                .serverRootCa(new TigerPkiIdentity("src/test/resources/selfSignedCa/rootCa.p12;wrongPassword"))
                .build())
            .build()))
            .isInstanceOf(RuntimeException.class);
    }

    // TODO really fix this and reactivate @Test
    public void customEccCaFileInTruststore_shouldVerifyConnection() throws UnirestException, IOException {
        final RbelPkiIdentity ca = CryptoLoader.getIdentityFromP12(
            FileUtils.readFileToByteArray(new File("src/test/resources/customCa.p12")), "00");

        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("https://backend")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .tls(TigerTlsConfiguration.builder()
                .serverRootCa(new TigerPkiIdentity("src/test/resources/customCa.p12;00"))
                .build())
            .build());

        Unirest.config().reset();
        Unirest.config().proxy("localhost", tigerProxy.getPort());
        Unirest.config().verifySsl(true);
        Unirest.config().sslContext(tigerProxy.buildSslContext());

        final kong.unirest.HttpResponse<JsonNode> response = Unirest.get("https://backend/foobar")
            .asJson();

        assertThat(response.getStatus()).isEqualTo(666);
        assertThat(response.getBody().getObject().get("foo").toString()).isEqualTo("bar");
    }

    @Test
    public void useTslBetweenProxyAndServer_shouldForward() throws UnirestException {
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("https://localhost:" + fakeBackendServer.httpsPort())
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
    public void testTigerWebEndpoint() throws UnirestException {
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
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        new UnirestInstance(
            new Config().proxy("localhost", tigerProxy.getPort()))
            .get("http://backend/foobar").asString().getBody();

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
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        tigerProxy.addRbelMessageListener(message -> callCounter.incrementAndGet());

        new UnirestInstance(
            new Config().proxy("localhost", tigerProxy.getPort()))
            .get("http://backend/foobar").asString().getBody();

        assertThat(callCounter.get()).isEqualTo(2);
    }

    @Test
    public void implicitReverseProxy_shouldForwardReqeust() {
        AtomicInteger callCounter = new AtomicInteger(0);

        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/notAServer")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        tigerProxy.addRbelMessageListener(message -> callCounter.incrementAndGet());

        Unirest.config().reset();
        // no (forward)-proxy! we use the tiger-proxy as a reverse-proxy
        Unirest.get("http://localhost:" + tigerProxy.getPort() + "/notAServer/foobar").asString();

        assertThat(callCounter.get()).isEqualTo(2);
        assertThat(tigerProxy.getRbelMessages().get(1)
            .findRbelPathMembers("$.body.foo.content")
            .get(0).getRawStringContent()
        ).isEqualTo("bar");
    }

    @Test
    public void blanketRerverseProxy_shouldForwardReqeust() {
        AtomicInteger callCounter = new AtomicInteger(0);

        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        tigerProxy.addRbelMessageListener(message -> callCounter.incrementAndGet());

        Unirest.config().reset();
        // no (forward)-proxy! we use the tiger-proxy as a reverse-proxy
        Unirest.get("http://localhost:" + tigerProxy.getPort() + "/foobar").asString();

        assertThat(callCounter.get()).isEqualTo(2);
    }

    @Test
    public void blanketRerverseProxy_shouldForwardHttpsRequest() {
        AtomicInteger callCounter = new AtomicInteger(0);

        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .tls(TigerTlsConfiguration.builder()
                .serverRootCa(new TigerPkiIdentity(
                    "src/test/resources/selfSignedCa/rootCa.p12;00"))
                .build())
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        tigerProxy.addRbelMessageListener(message -> callCounter.incrementAndGet());

        final UnirestInstance unirestWithTruststoreAndSslVerification = new UnirestInstance(
            new Config().proxy("localhost", tigerProxy.getPort())
                .verifySsl(true)
                .sslContext(tigerProxy.buildSslContext()));

        unirestWithTruststoreAndSslVerification
            .get("https://localhost:" + tigerProxy.getPort() + "/foobar").asString();

        assertThat(callCounter.get()).isEqualTo(2);
    }

    @Test
    public void activateFileSaving_shouldAddRouteTrafficToFile() {
        final String filename = "target/test-log.tgr";
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .fileSaveInfo(RbelFileSaveInfo.builder()
                .writeToFile(true)
                .clearFileOnBoot(true)
                .filename(filename)
                .build())
            .build());

        new UnirestInstance(
            new Config().proxy("localhost", tigerProxy.getPort()))
            .get("http://backend/foobar").asString();

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

        new UnirestInstance(
            new Config().proxy("localhost", tigerProxy.getPort()))
            .get("http://localhost:" + fakeBackendServer.port() + "/foobar").asString();

        await()
            .atMost(2, TimeUnit.SECONDS)
            .until(() -> new File(filename).exists());
    }

    @Test
    public void forwardMutualTlsAndTerminatingTls_shouldUseCorrectTerminatingCa() throws UnirestException, IOException {
        final TigerPkiIdentity ca = new TigerPkiIdentity(
            "src/test/resources/selfSignedCa/rootCa.p12;00");

        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("https://backend")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .tls(TigerTlsConfiguration.builder()
                .serverRootCa(ca)
                .forwardMutualTlsIdentity(new TigerPkiIdentity("src/test/resources/rsa.p12;00"))
                .build())
            .build());

        final UnirestInstance unirestInstance = new UnirestInstance(
            new Config().proxy("localhost", tigerProxy.getPort())
                .verifySsl(true)
                .sslContext(tigerProxy.buildSslContext()));

        assertThat(unirestInstance.get("https://backend/foobar").asString()
            .getStatus())
            .isEqualTo(666);
    }

    @Test
    public void basicAuthenticationRequiredAndConfigured_ShouldWork() {
        fakeBackendServer.stubFor(get(urlEqualTo("/authenticatedPath"))
            .withBasicAuth("user", "password")
            .willReturn(aResponse()
                .withStatus(777)
                .withBody("{\"foo\":\"bar\"}")));

        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backendWithBasicAuth")
                .to("http://localhost:" + fakeBackendServer.port())
                .basicAuth(new TigerBasicAuthConfiguration("user", "password"))
                .build()))
            .build());

        unirestInstance = Unirest.spawnInstance();
        unirestInstance.config().proxy("localhost", tigerProxy.getPort());

        assertThat(unirestInstance.get("http://backend/authenticatedPath").asJson().getStatus())
            .isEqualTo(404);
        assertThat(unirestInstance.get("http://backendWithBasicAuth/authenticatedPath").asJson().getStatus())
            .isEqualTo(777);
    }

    @Test
    public void perRouteCertificate_shouldBePresentedOnlyForThisRoute() throws UnirestException {
        final TigerPkiIdentity serverIdentity
            = new TigerPkiIdentity("src/test/resources/rsaStoreWithChain.jks;gematik");

        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .tls(TigerTlsConfiguration.builder()
                .serverIdentity(serverIdentity)
                .build())
            .proxyRoutes(List.of(TigerRoute.builder()
                    // aktor-gateway.gematik.de ist der DN des obigen zertifikats
                    .from("https://authn.aktor.epa.telematik-test")
                    .to("http://localhost:" + fakeBackendServer.port())
                    .build(),
                TigerRoute.builder()
                    .from("https://falsche-url")
                    .to("http://localhost:" + fakeBackendServer.port())
                    .build()))
            .build());

        final UnirestInstance unirestInstance = new UnirestInstance(
            new Config().proxy("localhost", tigerProxy.getPort())
                .verifySsl(true)
                .sslContext(buildSslContextTrustingOnly(serverIdentity)));

        assertThat(unirestInstance.get("https://authn.aktor.epa.telematik-test/foobar").asString()
            .getStatus())
            .isEqualTo(666);
        assertThatThrownBy(() -> unirestInstance.get("https://falsche-url/foobar").asString())
            .hasCauseInstanceOf(SSLPeerUnverifiedException.class);
    }

    @SneakyThrows
    private SSLContext buildSslContextTrustingOnly(TigerPkiIdentity serverIdentity) {
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null);
        ks.setCertificateEntry("caCert", serverIdentity.getCertificate());
        int chainCertCtr = 0;
        for (X509Certificate chainCert : serverIdentity.getCertificateChain()) {
            ks.setCertificateEntry("chainCert" + chainCertCtr++, chainCert);
        }
        TrustManagerFactory tmf = TrustManagerFactory
            .getInstance(TrustManagerFactory.getDefaultAlgorithm());

        tmf.init(ks);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, tmf.getTrustManagers(), null);

        return sslContext;
    }

    public void forwardProxyRouteViaAnotherForwardProxy() {
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://notARealServer")
                .build()))
            .forwardToProxy(ForwardProxyInfo.builder()
                .port(forwardProxy.getPort())
                .hostname("localhost")
                .type(TigerProxyType.HTTP)
                .build())
            .build());

        final UnirestInstance unirestInstance = Unirest.spawnInstance();
        unirestInstance.config().proxy("localhost", tigerProxy.getPort());

        final HttpResponse<JsonNode> response = unirestInstance.get("http://backend/foobar")
            .asJson();

        assertThat(response.getStatus()).isEqualTo(666);
    }

    @Test
    public void reverseProxyRouteViaAnotherForwardProxy() {
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("http://notARealServer")
                .build()))
            .forwardToProxy(ForwardProxyInfo.builder()
                .port(forwardProxy.getPort())
                .hostname("localhost")
                .type(TigerProxyType.HTTP)
                .build())
            .build());

        final HttpResponse<JsonNode> response = Unirest.spawnInstance()
            .get("http://localhost:" + tigerProxy.getPort() + "/foobar")
            .asJson();

        assertThat(response.getStatus()).isEqualTo(666);
    }

    @Test
    public void forwardProxyToNestedTarget_ShouldAdressCorrectly() {
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + fakeBackendServer.port() + "/deep")
                .build()))
            .build());

        Unirest.config().reset();
        Unirest.config().proxy("localhost", tigerProxy.getPort());

        assertThat(Unirest.get("http://backend/foobar").asString()
            .getStatus())
            .isEqualTo(777);

        assertThat(tigerProxy.getRbelMessages().get(0)
            .findElement("$.header.Host")
            .get().getRawStringContent())
            .isEqualTo("localhost:" + fakeBackendServer.port());
    }

    @Test
    public void forwardProxyToNestedTargetWithPlainPath_ShouldAdressCorrectly() {
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + fakeBackendServer.port() + "/foobar")
                .build()))
            .build());

        Unirest.config().reset();
        Unirest.config().proxy("localhost", tigerProxy.getPort());

        assertThat(Unirest.get("http://backend").asString()
            .getStatus())
            .isEqualTo(666);

        assertThat(tigerProxy.getRbelMessages().get(0)
            .findElement("$.header.Host")
            .get().getRawStringContent())
            .isEqualTo("localhost:" + fakeBackendServer.port());
    }

    @Test
    public void reverseProxyToNestedTarget_ShouldAdressCorrectly() {
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("http://localhost:" + fakeBackendServer.port() + "/deep")
                .build()))
            .build());

        Unirest.config().reset();

        assertThat(Unirest.get("http://localhost:" + tigerProxy.getPort() + "/foobar").asString()
            .getStatus())
            .isEqualTo(777);

        assertThat(tigerProxy.getRbelMessages().get(0)
            .findElement("$.header.Host")
            .get().getRawStringContent())
            .isEqualTo("localhost:" + fakeBackendServer.port());
    }
}
