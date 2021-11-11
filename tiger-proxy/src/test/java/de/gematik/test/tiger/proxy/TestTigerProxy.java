/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import de.gematik.rbellogger.configuration.RbelFileSaveInfo;
import de.gematik.rbellogger.converter.brainpool.BrainpoolCurves;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.test.tiger.common.config.tigerProxy.*;
import de.gematik.test.tiger.common.pki.KeyMgr;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyConfigurationException;
import kong.unirest.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jose4j.jws.JsonWebSignature;
import org.junit.Test;
import org.mockserver.model.MediaType;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.IOException;
import java.security.Key;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@Slf4j
public class TestTigerProxy extends AbstractTigerProxyTest {

    @Test
    public void useAsWebProxyServer_shouldForward() throws IOException {
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

        assertThat(tigerProxy.getRbelMessages().get(0).getFacetOrFail(RbelTcpIpMessageFacet.class).getReceiverHostname())
            .isEqualTo(new RbelHostname("backend", 80));
        assertThat(tigerProxy.getRbelMessages().get(1).getFacetOrFail(RbelTcpIpMessageFacet.class).getSenderHostname())
            .isEqualTo(new RbelHostname("backend", 80));

        FileUtils.writeStringToFile(new File("target/out.html"),
            new RbelHtmlRenderer().doRender(tigerProxy.getRbelMessages()));
    }

    @Test
    public void forwardProxy_headersShouldBeUntouchedExceptForHost() {
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
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

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
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        Unirest.get("http://localhost:" + tigerProxy.getPort() + "/foobar").asString();

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
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
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
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://foo.bar")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        proxyRest.get("http://foo.bar/foobar").asString();

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
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://foo")
                .to("http://bar")
                .build()))
            .build());

        final HttpResponse<JsonNode> response = proxyRest.get("http://localhost:" + fakeBackendServer.port() + "/foobar")
            .asJson();

        assertThat(response.getStatus()).isEqualTo(666);

        assertThat(tigerProxy.getRbelMessages().get(1).getFacetOrFail(RbelHttpResponseFacet.class)
            .getResponseCode().getRawStringContent())
            .isEqualTo("666");
    }

    @Test
    public void addAlreadyExistingRoute_shouldThrowException() {
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
    public void binaryMessage_shouldGiveBinaryResult() {
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

        assertThat(tigerProxy.getRbelMessages().get(tigerProxy.getRbelMessages().size() - 1)
            .findRbelPathMembers("$.body").get(0)
            .getRawContent())
            .containsExactly("Hallo".getBytes());
    }

    @Test
    public void testTigerWebEndpoint() throws UnirestException {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .activateRbelEndpoint(true)
            .build());

        assertThat(Unirest.get("http://localhost:" + tigerProxy.getPort() + "/rbel").asString()
            .getBody())
            .contains("<html");

        assertThat(proxyRest.get("http://rbel").asString()
            .getBody())
            .contains("<html");
    }

    @Test
    public void requestAndResponseThroughWebProxy_shouldGiveRbelObjects() throws UnirestException {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        proxyRest.get("http://backend/foobar").asString().getBody();

        assertThat(tigerProxy.getRbelMessages().get(1)
            .findRbelPathMembers("$.body.foo.content")
            .get(0).getRawStringContent()
        ).isEqualTo("bar");
    }

    @Test
    public void registerListenerThenSentRequest_shouldTriggerListener() throws UnirestException {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        AtomicInteger callCounter = new AtomicInteger(0);
        tigerProxy.addRbelMessageListener(message -> callCounter.incrementAndGet());

        proxyRest.get("http://backend/foobar").asString().getBody();

        assertThat(callCounter.get()).isEqualTo(2);
    }

    @Test
    public void implicitReverseProxy_shouldForwardReqeust() {
        AtomicInteger callCounter = new AtomicInteger(0);

        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/notAServer")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        tigerProxy.addRbelMessageListener(message -> callCounter.incrementAndGet());

        // no (forward)-proxy! we use the tiger-proxy as a reverse-proxy
        Unirest.get("http://localhost:" + tigerProxy.getPort() + "/notAServer/foobar").asString();

        assertThat(callCounter.get()).isEqualTo(2);
        assertThat(tigerProxy.getRbelMessages().get(1)
            .findRbelPathMembers("$.body.foo.content")
            .get(0).getRawStringContent()
        ).isEqualTo("bar");
    }

    @Test
    public void blanketReverseProxy_shouldForwardReqeust() {
        AtomicInteger callCounter = new AtomicInteger(0);

        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        tigerProxy.addRbelMessageListener(message -> callCounter.incrementAndGet());

        // no (forward)-proxy! we use the tiger-proxy as a reverse-proxy
        Unirest.get("http://localhost:" + tigerProxy.getPort() + "/foobar").asString();

        assertThat(callCounter.get()).isEqualTo(2);
    }

    @Test
    public void activateFileSaving_shouldAddRouteTrafficToFile() {
        final String filename = "target/test-log.tgr";
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
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

        proxyRest.get("http://backend/foobar").asString();

        await()
            .atMost(2, TimeUnit.SECONDS)
            .until(() -> new File(filename).exists());
    }

    @Test
    public void activateFileSaving_shouldAddRoutelessTrafficToFile() {
        final String filename = "target/test-log.tgr";
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .activateForwardAllLogging(true)
            .fileSaveInfo(RbelFileSaveInfo.builder()
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
    public void basicAuthenticationRequiredAndConfigured_ShouldWork() {
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
    public void forwardProxyRouteViaAnotherForwardProxy() {
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
    public void reverseProxyRouteViaAnotherForwardProxy() {
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
            .get("http://localhost:" + tigerProxy.getPort() + "/foobar")
            .asJson();

        assertThat(response.getStatus()).isEqualTo(666);
    }

    @Test
    public void forwardProxyToNestedTarget_ShouldAdressCorrectly() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + fakeBackendServer.port() + "/deep")
                .build()))
            .build());

        assertThat(proxyRest.get("http://backend/foobar").asString()
            .getStatus())
            .isEqualTo(777);

        assertThat(tigerProxy.getRbelMessages().get(0)
            .findElement("$.header.Host")
            .get().getRawStringContent())
            .isEqualTo("localhost:" + fakeBackendServer.port());
    }

    @Test
    public void forwardProxyToNestedTargetWithPlainPath_ShouldAdressCorrectly() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + fakeBackendServer.port() + "/foobar")
                .build()))
            .build());

        assertThat(proxyRest.get("http://backend").asString()
            .getStatus())
            .isEqualTo(666);

        assertThat(tigerProxy.getRbelMessages().get(0)
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

        JsonWebSignature jws = new JsonWebSignature();
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

        assertThat(tigerProxy.getRbelMessages().get(0)
            .findElement("$.path.jws.value.signature.isValid")
            .get().seekValue(Boolean.class).get())
            .isTrue();
    }

    @Test
    public void reverseProxyToNestedTarget_ShouldAdressCorrectly() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("http://localhost:" + fakeBackendServer.port() + "/deep")
                .build()))
            .build());

        assertThat(Unirest.get("http://localhost:" + tigerProxy.getPort() + "/foobar").asString()
            .getStatus())
            .isEqualTo(777);

        assertThat(tigerProxy.getRbelMessages().get(0)
            .findElement("$.header.Host")
            .get().getRawStringContent())
            .isEqualTo("localhost:" + fakeBackendServer.port());
    }

    @Test
    public void forwardProxyWithQueryParameters() {
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
    public void reverseProxyWithQueryParameters() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        Unirest.get("http://localhost:" + tigerProxy.getPort() + "/foobar?foo=bar1&foo=bar2&schmoo").asString();

        assertThat(getLastRequest().getQueryParams())
            .containsOnlyKeys("foo", "schmoo");
        assertThat(getLastRequest().getQueryParams().get("foo").values())
            .containsExactly("bar1", "bar2");
        assertThat(getLastRequest().getQueryParams().get("schmoo").values())
            .containsExactly("");
    }

    @Test
    public void forwardProxy_checkClientAddresses() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        final UnirestInstance unirestInstance = Unirest.spawnInstance();
        unirestInstance.config().proxy("localhost", tigerProxy.getPort());
        unirestInstance.get("http://backend/foobar").asString();

        final UnirestInstance secondInstance = Unirest.spawnInstance();
        secondInstance.config().proxy("localhost", tigerProxy.getPort());
        secondInstance.get("http://backend/foobar").asString();

        assertThat(extractHostnames(RbelTcpIpMessageFacet::getSenderHostname))
            .containsExactly("localhost", "backend", "localhost", "backend");
        assertThat(extractHostnames(RbelTcpIpMessageFacet::getReceiverHostname))
            .containsExactly("backend", "localhost", "backend", "localhost");

        checkPortsAreCorrect();
    }

    @Test
    public void reverseProxy_checkClientAddresses() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        final UnirestInstance unirestInstance = Unirest.spawnInstance();
        unirestInstance.get("http://localhost:" + tigerProxy.getPort() + "/foobar").asString();

        final UnirestInstance secondInstance = Unirest.spawnInstance();
        secondInstance.get("http://localhost:" + tigerProxy.getPort() + "/foobar").asString();

        assertThat(extractHostnames(RbelTcpIpMessageFacet::getSenderHostname))
            .containsExactly("localhost", "localhost", "localhost", "localhost");
        assertThat(extractHostnames(RbelTcpIpMessageFacet::getReceiverHostname))
            .containsExactly("localhost", "localhost", "localhost", "localhost");

        checkPortsAreCorrect();
    }

    @Test
    public void catchAllRoute_checkClientAddresses() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder().build());

        final UnirestInstance unirestInstance = Unirest.spawnInstance();
        unirestInstance.config().proxy("localhost", tigerProxy.getPort());
        unirestInstance.get("http://localhost:" + fakeBackendServer.port() + "/foobar").asString();

        final UnirestInstance secondInstance = Unirest.spawnInstance();
        secondInstance.config().proxy("localhost", tigerProxy.getPort());
        secondInstance.get("http://localhost:" + fakeBackendServer.port() + "/foobar").asString();

        assertThat(extractHostnames(RbelTcpIpMessageFacet::getSenderHostname))
            .containsExactly("localhost", "localhost", "localhost", "localhost");
        assertThat(extractHostnames(RbelTcpIpMessageFacet::getReceiverHostname))
            .containsExactly("localhost", "localhost", "localhost", "localhost");

        checkPortsAreCorrect();
    }

    private void checkPortsAreCorrect() {
        assertThat(tigerProxy.getRbelMessages().get(1).getFacetOrFail(RbelTcpIpMessageFacet.class)
            .getSenderHostname().getPort())
            .isEqualTo(tigerProxy.getRbelMessages().get(0).getFacetOrFail(RbelTcpIpMessageFacet.class)
                .getReceiverHostname().getPort());
        assertThat(tigerProxy.getRbelMessages().get(2).getFacetOrFail(RbelTcpIpMessageFacet.class)
            .getSenderHostname().getPort())
            .isEqualTo(tigerProxy.getRbelMessages().get(3).getFacetOrFail(RbelTcpIpMessageFacet.class)
                .getReceiverHostname().getPort());
        assertThat(tigerProxy.getRbelMessages().get(0).getFacetOrFail(RbelTcpIpMessageFacet.class)
            .getSenderHostname().getPort())
            .isNotEqualTo(tigerProxy.getRbelMessages().get(2).getFacetOrFail(RbelTcpIpMessageFacet.class)
                .getSenderHostname().getPort());
    }

    @NotNull
    private Stream<String> extractHostnames(Function<RbelTcpIpMessageFacet, RbelHostname> hostnameExtractor) {
        return tigerProxy.getRbelMessages().stream()
            .map(msg -> msg.getFacetOrFail(RbelTcpIpMessageFacet.class))
            .map(hostnameExtractor)
            .map(RbelHostname::getHostname)
            .map(Objects::toString);
    }
}
