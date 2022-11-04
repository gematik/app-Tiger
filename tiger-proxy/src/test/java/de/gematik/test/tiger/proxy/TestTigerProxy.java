/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;
import static org.mockserver.model.HttpRequest.request;
import de.gematik.rbellogger.converter.brainpool.BrainpoolCurves;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.facet.RbelHostnameFacet;
import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import de.gematik.rbellogger.data.facet.RbelMessageTimingFacet;
import de.gematik.rbellogger.data.facet.RbelTcpIpMessageFacet;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.data.config.tigerProxy.*;
import de.gematik.test.tiger.common.pki.KeyMgr;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyConfigurationException;
import java.io.File;
import java.security.Key;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import kong.unirest.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.assertj.core.data.TemporalUnitWithinOffset;
import org.jetbrains.annotations.NotNull;
import org.jose4j.jws.JsonWebSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.MediaType;
import org.mockserver.model.SocketAddress;
import org.mockserver.netty.MockServer;
import org.springframework.util.SocketUtils;

@Slf4j
@TestInstance(Lifecycle.PER_CLASS)
public class TestTigerProxy extends AbstractTigerProxyTest {

    public MockServerClient forwardProxy;

    @BeforeEach
    public void setupForwardProxy() {
        if (forwardProxy != null) {
            return;
        }

        final MockServer forwardProxyServer = new MockServer();

        forwardProxy = new MockServerClient("localhost", forwardProxyServer.getLocalPort());
        log.info("Started Forward-Proxy-Server on port {}", forwardProxy.getPort());

        forwardProxy.when(request())
            .forward(
                req -> forwardOverriddenRequest(
                    req.withSocketAddress(
                        "localhost", fakeBackendServer.port(), SocketAddress.Scheme.HTTP
                    ))
                    .getRequestOverride());
    }

    @Test
    void useAsWebProxyServer_shouldForward() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        final HttpResponse<JsonNode> response = proxyRest.get("http://backend/foobar")
            .asJson();

        assertThat(response.getStatus()).isEqualTo(666);
        assertThat(response.getBody().getObject().get("foo").toString()).isEqualTo("bar");

        assertThat(
            tigerProxy.getRbelMessagesList().get(0).getFacetOrFail(RbelTcpIpMessageFacet.class).getReceiverHostname())
            .isEqualTo(new RbelHostname("backend", 80));
        assertThat(
            tigerProxy.getRbelMessagesList().get(1).getFacetOrFail(RbelTcpIpMessageFacet.class).getSenderHostname())
            .isEqualTo(new RbelHostname("backend", 80));
    }

    @Test
    void forwardProxy_headersShouldBeUntouchedExceptForHost() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        proxyRest.get("http://backend/foobar")
            .header("foo", "bar")
            .header("x-forwarded-for", "someStuff")
            .asString();

        assertThat(tigerProxy.getRbelMessagesList().get(0)
            .findElement("$.header.foo")
            .get().getRawStringContent())
            .isEqualTo("bar");
        assertThat(tigerProxy.getRbelMessagesList().get(0)
            .findElement("$.header.x-forwarded-for")
            .get().getRawStringContent())
            .isEqualTo("someStuff");
        assertThat(tigerProxy.getRbelMessagesList().get(0)
            .findElement("$.header.Host")
            .get().getRawStringContent())
            .isEqualTo("localhost:" + fakeBackendServer.port());
    }

    @Test
    void reverseProxy_headersShouldBeUntouched() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar")
            .header("foo", "bar")
            .header("x-forwarded-for", "someStuff")
            .header("Host", "RandomStuffShouldBePreserved")
            .asString();

        assertThat(tigerProxy.getRbelMessagesList().get(0)
            .findElement("$.header.foo")
            .get().getRawStringContent())
            .isEqualTo("bar");
        assertThat(tigerProxy.getRbelMessagesList().get(0)
            .findElement("$.header.x-forwarded-for")
            .get().getRawStringContent())
            .isEqualTo("someStuff");
        assertThat(tigerProxy.getRbelMessagesList().get(0)
            .findElement("$.header.Host")
            .get().getRawStringContent())
            .isEqualTo("RandomStuffShouldBePreserved");
    }

    @Test
    void reverseProxy_shouldRewriteHostHeaderIfConfigured() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .rewriteHostHeader(true)
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar")
            .header("Host", "RandomStuffShouldBeOverwritten")
            .asString();

        assertThat(tigerProxy.getRbelMessagesList().get(0)
            .findElement("$.header.Host")
            .get().getRawStringContent())
            .contains("localhost:");
    }

    @Test
    void forwardProxy_shouldRewriteHostHeaderIfConfigured() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .rewriteHostHeader(true)
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://foo.bar")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        proxyRest.get("http://foo.bar/foobar")
            .asString();

        assertThat(tigerProxy.getRbelMessagesList().get(0)
            .findElement("$.header.Host")
            .get().getRawStringContent())
            .contains("localhost:");
    }

    @Test
    void reverseProxy_shouldGiveReceiverAndSenderInRbelMessage() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar").asString();

        assertThat(tigerProxy.getRbelMessagesList().get(0)
            .findElement("$.receiver")
            .flatMap(el -> el.getFacet(RbelHostnameFacet.class))
            .map(Object::toString)).get()
            .isEqualTo("localhost:" + fakeBackendServer.port());
        assertThat(tigerProxy.getRbelMessagesList().get(1)
            .findElement("$.sender")
            .flatMap(el -> el.getFacet(RbelHostnameFacet.class))
            .map(Object::toString)).get()
            .isEqualTo("localhost:" + fakeBackendServer.port());
    }

    @Test
    void reverseProxy_shouldUseConfiguredAlternativeNameInTlsCertificate()
        throws NoSuchAlgorithmException, KeyManagementException {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .tls(TigerTlsConfiguration.builder()
                .domainName("muahaha")
                .build())
            .build());

        final AtomicBoolean verifyWasCalledSuccesfully = new AtomicBoolean(false);
        final SSLContext ctx = SSLContext.getInstance("TLSv1.2");
        ctx.init(null, new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(final X509Certificate[] chain, final String authType) {
                    }

                    @Override
                    public void checkServerTrusted(final X509Certificate[] chain, final String authType) {
                        assertThat(chain[0].getSubjectDN().getName())
                            .contains("muahaha");
                        verifyWasCalledSuccesfully.set(true);
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
            }, new SecureRandom()
        );
        new UnirestInstance(new Config()
            .sslContext(ctx))
            .get("https://localhost:" + tigerProxy.getProxyPort() + "/foobar").asString();

        assertThat(verifyWasCalledSuccesfully).isTrue();
    }

    @Test
    void forwardProxy_shouldGiveReceiverAndSenderInRbelMessage() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://foo.bar")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        proxyRest.get("http://foo.bar/foobar").asString();

        assertThat(tigerProxy.getRbelMessagesList().get(0)
            .findElement("$.receiver")
            .flatMap(el -> el.getFacet(RbelHostnameFacet.class))
            .map(Object::toString)).get()
            .isEqualTo("foo.bar:80");
        assertThat(tigerProxy.getRbelMessagesList().get(1)
            .findElement("$.sender")
            .flatMap(el -> el.getFacet(RbelHostnameFacet.class))
            .map(Object::toString)).get()
            .isEqualTo("foo.bar:80");
    }

    @Test
    void routeLessTraffic_shouldLogInRbel() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://foo")
                .to("http://bar")
                .build()))
            .build());

        final HttpResponse<JsonNode> response = proxyRest.get(
                "http://localhost:" + fakeBackendServer.port() + "/foobar")
            .asJson();

        assertThat(response.getStatus()).isEqualTo(666);

        assertThat(tigerProxy.getRbelMessagesList().get(1).getFacetOrFail(RbelHttpResponseFacet.class)
            .getResponseCode().getRawStringContent())
            .isEqualTo("666");
    }

    @Test
    void addAlreadyExistingRoute_shouldThrowException() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
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
    void binaryMessage_shouldGiveBinaryResult() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        fakeBackendServer.stubFor(get(urlEqualTo("/binary"))
            .willReturn(aResponse()
                .withHeader("content-type", MediaType.APPLICATION_OCTET_STREAM.toString())
                .withBody("Hallo".getBytes())));

        proxyRest.get("http://backend/binary").asBytes();

        assertThat(tigerProxy.getRbelMessagesList().get(tigerProxy.getRbelMessagesList().size() - 1)
            .findRbelPathMembers("$.body").get(0)
            .getRawContent())
            .containsExactly("Hallo".getBytes());
    }

    @Test
    void requestAndResponseThroughWebProxy_shouldGiveRbelObjects() throws UnirestException {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        proxyRest.get("http://backend/foobar").asString().getBody();

        assertThat(tigerProxy.getRbelMessagesList().get(1)
            .findRbelPathMembers("$.body.foo.content")
            .get(0).getRawStringContent()
        ).isEqualTo("bar");
    }

    @Test
    void registerListenerThenSentRequest_shouldTriggerListener() throws UnirestException {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        final AtomicInteger callCounter = new AtomicInteger(0);
        tigerProxy.addRbelMessageListener(message -> callCounter.incrementAndGet());

        proxyRest.get("http://backend/foobar").asString().getBody();

        assertThat(callCounter.get()).isEqualTo(2);
    }

    @Test
    void implicitReverseProxy_shouldForwardReqeust() {
        final AtomicInteger callCounter = new AtomicInteger(0);

        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/notAServer")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        tigerProxy.addRbelMessageListener(message -> callCounter.incrementAndGet());

        // no (forward)-proxy! we use the tiger-proxy as a reverse-proxy
        Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/notAServer/foobar").asString();

        assertThat(callCounter.get()).isEqualTo(2);
        assertThat(tigerProxy.getRbelMessagesList().get(1)
            .findRbelPathMembers("$.body.foo.content")
            .get(0).getRawStringContent()
        ).isEqualTo("bar");
    }

    @Test
    void blanketReverseProxy_shouldForwardReqeust() {
        final AtomicInteger callCounter = new AtomicInteger(0);

        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        tigerProxy.addRbelMessageListener(message -> callCounter.incrementAndGet());

        // no (forward)-proxy! we use the tiger-proxy as a reverse-proxy
        Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar").asString();

        assertThat(callCounter.get()).isEqualTo(2);
    }

    @Test
    void activateFileSaving_shouldAddRouteTrafficToFile() {
        final String filename = "target/test-log.tgr";
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .fileSaveInfo(TigerFileSaveInfo.builder()
                .writeToFile(true)
                .clearFileOnBoot(true)
                .filename(filename)
                .build())
            .build());

        proxyRest.get("http://backend/foobar").asString();

        await()
            .atMost(2, TimeUnit.SECONDS)
            .until(() -> new File(filename).exists());
    }

    @Test
    void activateFileSaving_shouldAddRoutelessTrafficToFile() {
        final String filename = "target/test-log.tgr";
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .activateForwardAllLogging(true)
            .fileSaveInfo(TigerFileSaveInfo.builder()
                .writeToFile(true)
                .clearFileOnBoot(true)
                .filename(filename)
                .build())
            .build());

        proxyRest.get("http://localhost:" + fakeBackendServer.port() + "/foobar").asString();

        await()
            .atMost(2, TimeUnit.SECONDS)
            .until(() -> new File(filename).exists());
    }

    @Test
    void basicAuthenticationRequiredAndConfigured_ShouldWork() {
        fakeBackendServer.stubFor(get(urlEqualTo("/authenticatedPath"))
            .withBasicAuth("user", "password")
            .willReturn(aResponse()
                .withStatus(777)
                .withBody("{\"foo\":\"bar\"}")));

        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backendWithBasicAuth")
                .to("http://localhost:" + fakeBackendServer.port())
                .basicAuth(new TigerBasicAuthConfiguration("user", "password"))
                .build()))
            .build());

        assertThat(proxyRest.get("http://backend/authenticatedPath").asJson().getStatus())
            .isEqualTo(404);
        assertThat(proxyRest.get("http://backendWithBasicAuth/authenticatedPath").asJson().getStatus())
            .isEqualTo(777);
    }

    @Test
    void forwardProxyRouteViaAnotherForwardProxy() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
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

        final HttpResponse<JsonNode> response = proxyRest.get("http://backend/foobar")
            .asJson();

        assertThat(response.getStatus()).isEqualTo(666);
    }

    @Test
    void reverseProxyRouteViaAnotherForwardProxy() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
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

        final HttpResponse<JsonNode> response = Unirest
            .get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar")
            .asJson();

        assertThat(response.getStatus()).isEqualTo(666);
    }

    @Test
    void forwardProxyToNestedTarget_ShouldAdressCorrectly() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + fakeBackendServer.port() + "/deep")
                .build()))
            .build());

        assertThat(proxyRest.get("http://backend/foobar").asString()
            .getStatus())
            .isEqualTo(777);

        assertThat(tigerProxy.getRbelMessagesList().get(0)
            .findElement("$.header.Host")
            .get().getRawStringContent())
            .isEqualTo("localhost:" + fakeBackendServer.port());
    }

    @Test
    void forwardProxyToNestedTargetWithPlainPath_ShouldAdressCorrectly() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + fakeBackendServer.port() + "/foobar")
                .build()))
            .build());

        assertThat(proxyRest.get("http://backend").asString()
            .getStatus())
            .isEqualTo(666);

        assertThat(tigerProxy.getRbelMessagesList().get(0)
            .findElement("$.header.Host")
            .get().getRawStringContent())
            .isEqualTo("localhost:" + fakeBackendServer.port());
    }

    @SneakyThrows
    @Test
    //gemSpec_Krypt, A_21888
    public void tigerProxyShouldHaveFixedVauKeyLoaded() {
        BrainpoolCurves.init();
        final Key key = KeyMgr.readKeyFromPem(FileUtils.readFileToString(new File("src/test/resources/fixVauKey.pem")));

        final JsonWebSignature jws = new JsonWebSignature();
        jws.setKey(key);
        jws.setPayload("foobar");
        jws.setAlgorithmHeaderValue("BP256R1");
        final String jwsSerialized = jws.getCompactSerialization();

        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + fakeBackendServer.port() + "/foobar")
                .build()))
            .build());

        proxyRest.get("http://backend?jws=" + jwsSerialized)
            .asString();

        assertThat(tigerProxy.getRbelMessagesList().get(0)
            .findElement("$.path.jws.value.signature.isValid")
            .get().seekValue(Boolean.class).get())
            .isTrue();
    }

    @Test
    void reverseProxyToNestedTarget_ShouldAdressCorrectly() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("http://localhost:" + fakeBackendServer.port() + "/deep")
                .build()))
            .build());

        assertThat(Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar").asString()
            .getStatus())
            .isEqualTo(777);

        assertThat(tigerProxy.getRbelMessagesList().get(0)
            .findElement("$.header.Host")
            .get().getRawStringContent())
            .isEqualTo("localhost:" + tigerProxy.getProxyPort());
    }

    @Test
    void forwardProxyWithQueryParameters() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        proxyRest.get("http://backend/foobar?foo=bar1&foo=bar2&schmoo").asString();

        assertThat(getLastRequest().getQueryParams())
            .containsOnlyKeys("foo", "schmoo");
        assertThat(getLastRequest().getQueryParams().get("foo").values())
            .containsExactly("bar1", "bar2");
        assertThat(getLastRequest().getQueryParams().get("schmoo").values())
            .containsExactly("");
    }

    @Test
    void reverseProxyWithQueryParameters() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar?foo=bar1&foo=bar2&schmoo").asString();

        assertThat(getLastRequest().getQueryParams())
            .containsOnlyKeys("foo", "schmoo");
        assertThat(getLastRequest().getQueryParams().get("foo").values())
            .containsExactly("bar1", "bar2");
        assertThat(getLastRequest().getQueryParams().get("schmoo").values())
            .containsExactly("");
    }

    @Test
    void forwardProxy_checkClientAddresses() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        final UnirestInstance unirestInstance = Unirest.spawnInstance();
        unirestInstance.config().proxy("localhost", tigerProxy.getProxyPort());
        unirestInstance.get("http://backend/foobar").asString();

        final UnirestInstance secondInstance = Unirest.spawnInstance();
        secondInstance.config().proxy("localhost", tigerProxy.getProxyPort());
        secondInstance.get("http://backend/foobar").asString();

        final List<String> hostnameSenderList = new ArrayList<>();
        hostnameSenderList.addAll(
            Arrays.asList("localhost|view-localhost", "backend", "localhost|view-localhost", "backend"));

        final List<String> hostnameReceiverList = new ArrayList<>();
        hostnameReceiverList.addAll(
            Arrays.asList("backend", "localhost|view-localhost", "backend", "localhost|view-localhost"));

        checkClientAddresses(hostnameSenderList, hostnameReceiverList);
        checkPortsAreCorrect();
    }

    @Test
    void reverseProxy_checkClientAddresses() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        final UnirestInstance unirestInstance = Unirest.spawnInstance();
        unirestInstance.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar").asString();

        final UnirestInstance secondInstance = Unirest.spawnInstance();
        secondInstance.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar").asString();

        final List<String> hostnameList = new ArrayList<>();
        hostnameList.addAll(
            Arrays.asList("localhost|view-localhost", "localhost|view-localhost", "localhost|view-localhost",
                "localhost|view-localhost"));

        checkClientAddresses(hostnameList, hostnameList);

        checkPortsAreCorrect();
    }

    @Test
    void forwardAllRoute_checkClientAddresses() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder().build());

        final UnirestInstance unirestInstance = Unirest.spawnInstance();
        unirestInstance.config().proxy("localhost", tigerProxy.getProxyPort());
        unirestInstance.get("http://localhost:" + fakeBackendServer.port() + "/foobar").asString();

        final UnirestInstance secondInstance = Unirest.spawnInstance();
        secondInstance.config().proxy("localhost", tigerProxy.getProxyPort());
        secondInstance.get("http://localhost:" + fakeBackendServer.port() + "/foobar").asString();

        final List<String> hostnameList = new ArrayList<>();
        hostnameList.addAll(
            Arrays.asList("localhost|view-localhost", "localhost|view-localhost", "localhost|view-localhost",
                "localhost|view-localhost"));

        checkClientAddresses(hostnameList, hostnameList);

        checkPortsAreCorrect();
    }

    @Test
    void checkNotSetTigerProxyPort_ShouldImplicitBeSetToTheChosenFreePort() {
        TigerGlobalConfiguration.reset();
        spawnTigerProxyWith(TigerProxyConfiguration.builder().build());
        assertThat(tigerProxy.getProxyPort()).isBetween(10000, 100000);
    }

    @Test
    void checkGetNotSetTigerProxyPort_ShouldThrowTigerConfigurationException() {
        TigerGlobalConfiguration.reset();
        int availableTcpPort = SocketUtils.findAvailableTcpPort();
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyPort(availableTcpPort)
            .build());

        assertThat(tigerProxy.getProxyPort()).isEqualTo(availableTcpPort);
    }


    @Test
    void forwardProxy_shouldAddTimingInformation() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        proxyRest.get("http://backend/foobar").asString();

        assertThat(tigerProxy.getRbelMessagesList().get(0)
            .getFacetOrFail(RbelMessageTimingFacet.class).getTransmissionTime())
            .isCloseTo(ZonedDateTime.now(), new TemporalUnitWithinOffset(1, ChronoUnit.SECONDS));
        assertThat(tigerProxy.getRbelMessagesList().get(1)
            .getFacetOrFail(RbelMessageTimingFacet.class).getTransmissionTime())
            .isCloseTo(ZonedDateTime.now(), new TemporalUnitWithinOffset(1, ChronoUnit.SECONDS));
    }

    @Test
    void reverseProxy_shouldAddTimingInformation() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar").asString();

        assertThat(tigerProxy.getRbelMessagesList().get(0)
            .getFacetOrFail(RbelMessageTimingFacet.class).getTransmissionTime())
            .isCloseTo(ZonedDateTime.now(), new TemporalUnitWithinOffset(1, ChronoUnit.SECONDS));
        assertThat(tigerProxy.getRbelMessagesList().get(1)
            .getFacetOrFail(RbelMessageTimingFacet.class).getTransmissionTime())
            .isCloseTo(ZonedDateTime.now(), new TemporalUnitWithinOffset(1, ChronoUnit.SECONDS));
    }

    @Test
    void forwardAllRoute_shouldAddTimingInformation() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder().build());

        final UnirestInstance unirestInstance = Unirest.spawnInstance();
        unirestInstance.config().proxy("localhost", tigerProxy.getProxyPort());
        unirestInstance.get("http://localhost:" + fakeBackendServer.port() + "/foobar").asString();

        assertThat(tigerProxy.getRbelMessagesList().get(0)
            .getFacetOrFail(RbelMessageTimingFacet.class).getTransmissionTime())
            .isCloseTo(ZonedDateTime.now(), new TemporalUnitWithinOffset(1, ChronoUnit.SECONDS));
        assertThat(tigerProxy.getRbelMessagesList().get(1)
            .getFacetOrFail(RbelMessageTimingFacet.class).getTransmissionTime())
            .isCloseTo(ZonedDateTime.now(), new TemporalUnitWithinOffset(1, ChronoUnit.SECONDS));
    }

    @Test
    void emptyBuffer_shouldNotRetainMessages() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .rbelBufferSizeInMb(0)
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        proxyRest.get("http://backend/foobar")
            .header("foo", "bar")
            .header("x-forwarded-for", "someStuff")
            .asString();

        assertThat(tigerProxy.getRbelMessagesList())
            .isEmpty();
    }

    // AKR: we need the 'localhost|view-localhost' because of mockserver for all checkClientAddresses-tests.
    private void checkClientAddresses(final List<String> hostnameSenderList, final List<String> hostnameReceiverList) {
        log.info("hostnames: {} and {}, matching to {} and {}",
            extractHostnames(RbelTcpIpMessageFacet::getSender).collect(Collectors.toUnmodifiableList()),
            extractHostnames(RbelTcpIpMessageFacet::getReceiver).collect(Collectors.toUnmodifiableList()),
            hostnameSenderList, hostnameReceiverList);
        for (int i = 0; i < hostnameSenderList.size(); i += 2) {
            final int index = i;
            //TODO TGR-651 wieder reaktivieren
            //
//            assertThat(extractHostnames(RbelTcpIpMessageFacet::getSender))
//                .matches(value -> value.get(index / 2).matches(hostnameSenderList.get(index)));
            assertThat(extractHostnames(RbelTcpIpMessageFacet::getReceiver))
                .matches(value -> value.get(index / 2).matches(hostnameReceiverList.get(index)));
        }
    }

    private void checkPortsAreCorrect() {
        assertThat(tigerProxy.getRbelMessagesList().get(1).getFacetOrFail(RbelTcpIpMessageFacet.class)
            .getSenderHostname().getPort())
            .isEqualTo(tigerProxy.getRbelMessagesList().get(0).getFacetOrFail(RbelTcpIpMessageFacet.class)
                .getReceiverHostname().getPort());
/* TODO TGR-651 as we have no more client adress method in mock, we are not able to get the client port from where the request originated
   so for now we use the local adress which is equal to the proxy port :(
       assertThat(tigerProxy.getRbelMessagesList().get(2).getFacetOrFail(RbelTcpIpMessageFacet.class)
            .getSenderHostname().getPort())
            .isEqualTo(tigerProxy.getRbelMessagesList().get(3).getFacetOrFail(RbelTcpIpMessageFacet.class)
                .getReceiverHostname().getPort());
        assertThat(tigerProxy.getRbelMessagesList().get(0).getFacetOrFail(RbelTcpIpMessageFacet.class)
            .getSenderHostname().getPort())
            .isNotEqualTo(tigerProxy.getRbelMessagesList().get(2).getFacetOrFail(RbelTcpIpMessageFacet.class)
                .getSenderHostname().getPort());
 */

    }

    @NotNull
    private Stream<String> extractHostnames(final Function<RbelTcpIpMessageFacet, RbelElement> hostnameExtractor) {
        return tigerProxy.getRbelMessagesList().stream()
            .map(msg -> msg.getFacetOrFail(RbelTcpIpMessageFacet.class))
            .map(hostnameExtractor)
            .filter(Objects::nonNull)
            .map(el -> el.getFacet(RbelHostnameFacet.class))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(RbelHostnameFacet::toRbelHostname)
            .map(RbelHostname::getHostname)
            .map(Objects::toString);
    }
}
