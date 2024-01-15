/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static de.gematik.rbellogger.data.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import de.gematik.rbellogger.converter.RbelConverterPlugin;
import de.gematik.rbellogger.converter.brainpool.BrainpoolCurves;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.facet.RbelHostnameFacet;
import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import de.gematik.rbellogger.data.facet.RbelMessageTimingFacet;
import de.gematik.rbellogger.data.facet.RbelTcpIpMessageFacet;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.*;
import de.gematik.test.tiger.common.pki.KeyMgr;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyConfigurationException;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
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
import org.apache.commons.lang3.RandomStringUtils;
import org.assertj.core.data.TemporalUnitWithinOffset;
import org.jetbrains.annotations.NotNull;
import org.jose4j.jws.JsonWebSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.MediaType;

@Slf4j
@TestInstance(Lifecycle.PER_CLASS)
@ResetTigerConfiguration
class TestTigerProxy extends AbstractTigerProxyTest {

  // AKR: we need the 'localhost|view-localhost' because of mockserver for all
  // checkClientAddresses-tests.
  private static final String LOCALHOST_REGEX = "localhost|view-localhost|127\\.0\\.0\\.1";

  @RegisterExtension
  static WireMockExtension forwardProxy =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort())
          .configureStaticDsl(true)
          .build();

  @BeforeEach
  public void setupForwardProxy(WireMockRuntimeInfo runtimeInfo) {
    if (forwardProxy != null) {
      return;
    }
    log.info("Started Forward-Proxy-Server on port {}", forwardProxy.getPort());

    forwardProxy.stubFor(
        get(urlMatching(".*"))
            .willReturn(aResponse().proxiedFrom("http://localhost:" + runtimeInfo.getHttpPort())));
  }

  @Test
  void useAsWebProxyServer_shouldForward() {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("http://backend")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());

    final HttpResponse<JsonNode> response = proxyRest.get("http://backend/foobar").asJson();
    awaitMessagesInTiger(2);

    assertThat(response.getStatus()).isEqualTo(666);
    assertThat(response.getBody().getObject().get("foo")).hasToString("bar");
    assertThat(
            tigerProxy
                .getRbelMessagesList()
                .get(0)
                .getFacetOrFail(RbelTcpIpMessageFacet.class)
                .getReceiverHostname())
        .isEqualTo(new RbelHostname("backend", 80));
    assertThat(
            tigerProxy
                .getRbelMessagesList()
                .get(1)
                .getFacetOrFail(RbelTcpIpMessageFacet.class)
                .getSenderHostname())
        .isEqualTo(new RbelHostname("backend", 80));
  }

  @Test
  void forwardProxy_headersShouldBeUntouchedExceptForHost() {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("http://backend")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());

    proxyRest
        .get("http://backend/foobar")
        .header("foo", "bar")
        .header("x-forwarded-for", "someStuff")
        .asString();
    awaitMessagesInTiger(2);

    assertThat(
            tigerProxy
                .getRbelMessagesList()
                .get(0)
                .findElement("$.header.foo")
                .get()
                .getRawStringContent())
        .isEqualTo("bar");
    assertThat(
            tigerProxy
                .getRbelMessagesList()
                .get(0)
                .findElement("$.header.x-forwarded-for")
                .get()
                .getRawStringContent())
        .isEqualTo("someStuff");
    assertThat(
            tigerProxy
                .getRbelMessagesList()
                .get(0)
                .findElement("$.header.Host")
                .get()
                .getRawStringContent())
        .isEqualTo("localhost:" + fakeBackendServerPort);
  }

  @Test
  void reverseProxy_headersShouldBeUntouched() {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("/")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());

    Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar")
        .header("foo", "bar")
        .header("x-forwarded-for", "someStuff")
        .header("Host", "RandomStuffShouldBePreserved")
        .asString();
    awaitMessagesInTiger(2);

    assertThat(
            tigerProxy
                .getRbelMessagesList()
                .get(0)
                .findElement("$.header.foo")
                .get()
                .getRawStringContent())
        .isEqualTo("bar");
    assertThat(
            tigerProxy
                .getRbelMessagesList()
                .get(0)
                .findElement("$.header.x-forwarded-for")
                .get()
                .getRawStringContent())
        .isEqualTo("someStuff");
    assertThat(
            tigerProxy
                .getRbelMessagesList()
                .get(0)
                .findElement("$.header.Host")
                .get()
                .getRawStringContent())
        .isEqualTo("RandomStuffShouldBePreserved");
  }

  @Test
  void reverseProxy_shouldRewriteHostHeaderIfConfigured() {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .rewriteHostHeader(true)
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("/")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());

    Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar")
        .header("Host", "RandomStuffShouldBeOverwritten")
        .asString();
    awaitMessagesInTiger(2);

    assertThat(
            tigerProxy
                .getRbelMessagesList()
                .get(0)
                .findElement("$.header.Host")
                .get()
                .getRawStringContent())
        .contains("localhost:");
  }

  @Test
  void forwardProxy_shouldRewriteHostHeaderIfConfigured() {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .rewriteHostHeader(true)
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("http://foo.bar")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());

    proxyRest.get("http://foo.bar/foobar").asString();
    awaitMessagesInTiger(2);

    assertThat(
            tigerProxy
                .getRbelMessagesList()
                .get(0)
                .findElement("$.header.Host")
                .get()
                .getRawStringContent())
        .contains("localhost:");
  }

  @Test
  void reverseProxy_shouldGiveReceiverAndSenderInRbelMessage() {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("/")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());

    Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar").asString();
    awaitMessagesInTiger(2);

    assertThat(
            tigerProxy
                .getRbelMessagesList()
                .get(0)
                .findElement("$.receiver")
                .flatMap(el -> el.getFacet(RbelHostnameFacet.class))
                .map(Object::toString))
        .get()
        .isEqualTo("localhost:" + fakeBackendServerPort);
    assertThat(
            tigerProxy
                .getRbelMessagesList()
                .get(1)
                .findElement("$.sender")
                .flatMap(el -> el.getFacet(RbelHostnameFacet.class))
                .map(Object::toString))
        .get()
        .isEqualTo("localhost:" + fakeBackendServerPort);
  }

  @Test
  void reverseProxy_shouldUseConfiguredAlternativeNameInTlsCertificate()
      throws NoSuchAlgorithmException, KeyManagementException {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("/")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .tls(TigerTlsConfiguration.builder().domainName("muahaha").build())
            .build());

    final AtomicBoolean verifyWasCalledSuccesfully = new AtomicBoolean(false);
    final SSLContext ctx = SSLContext.getInstance("TLSv1.2");
    ctx.init(
        null,
        new TrustManager[] {
          new X509TrustManager() {
            @Override
            public void checkClientTrusted(final X509Certificate[] chain, final String authType) {}

            @Override
            public void checkServerTrusted(final X509Certificate[] chain, final String authType) {
              assertThat(chain[0].getSubjectDN().getName()).contains("muahaha");
              verifyWasCalledSuccesfully.set(true);
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
              return new X509Certificate[0];
            }
          }
        },
        new SecureRandom());
    new UnirestInstance(new Config().sslContext(ctx))
        .get("https://localhost:" + tigerProxy.getProxyPort() + "/foobar")
        .asString();

    assertThat(verifyWasCalledSuccesfully).isTrue();
  }

  @Test
  void forwardProxy_shouldGiveReceiverAndSenderInRbelMessage() {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("http://foo.bar")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());

    proxyRest.get("http://foo.bar/foobar").asString();
    awaitMessagesInTiger(2);

    assertThat(
            tigerProxy
                .getRbelMessagesList()
                .get(0)
                .findElement("$.receiver")
                .flatMap(el -> el.getFacet(RbelHostnameFacet.class))
                .map(Object::toString))
        .get()
        .isEqualTo("foo.bar:80");
    assertThat(
            tigerProxy
                .getRbelMessagesList()
                .get(1)
                .findElement("$.sender")
                .flatMap(el -> el.getFacet(RbelHostnameFacet.class))
                .map(Object::toString))
        .get()
        .isEqualTo("foo.bar:80");
  }

  @Test
  void routeLessTraffic_shouldLogInRbel() {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder().from("http://foo").to("http://bar").build()))
            .build());

    final HttpResponse<JsonNode> response =
        proxyRest.get("http://localhost:" + fakeBackendServerPort + "/foobar").asJson();
    awaitMessagesInTiger(2);

    assertThat(response.getStatus()).isEqualTo(666);

    assertThat(
            tigerProxy
                .getRbelMessagesList()
                .get(1)
                .getFacetOrFail(RbelHttpResponseFacet.class)
                .getResponseCode()
                .getRawStringContent())
        .isEqualTo("666");
  }

  @Test
  void addAlreadyExistingRoute_shouldThrowException() {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("http://backend")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());

    var route =
        TigerRoute.builder()
            .from("http://backend")
            .to("http://localhost:" + fakeBackendServerPort)
            .build();
    assertThatThrownBy(() -> tigerProxy.addRoute(route))
        .isInstanceOf(TigerProxyConfigurationException.class);
  }

  @Test
  void binaryMessage_shouldGiveBinaryResult(WireMockRuntimeInfo runtimeInfo) {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("http://backend")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());

    runtimeInfo
        .getWireMock()
        .register(
            stubFor(
                get("/binary")
                    .willReturn(
                        ok().withHeader(
                                "content-type", MediaType.APPLICATION_OCTET_STREAM.toString())
                            .withBody("Hallo".getBytes()))));

    proxyRest.get("http://backend/binary").asBytes();
    awaitMessagesInTiger(2);

    assertThat(tigerProxy.getRbelMessagesList().get(tigerProxy.getRbelMessagesList().size() - 1))
        .extractChildWithPath("$.body")
        .hasStringContentEqualTo("Hallo");
  }

  @Test
  void requestAndResponseThroughWebProxy_shouldGiveRbelObjects() throws UnirestException {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("http://backend")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());

    proxyRest.get("http://backend/foobar").asString().getBody();
    awaitMessagesInTiger(2);

    assertThat(tigerProxy.getRbelMessagesList().get(1))
        .extractChildWithPath("$.body.foo.content")
        .hasStringContentEqualTo("bar");
  }

  @Test
  void registerListenerThenSentRequest_shouldTriggerListener() throws UnirestException {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("http://backend")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());

    final AtomicInteger callCounter = new AtomicInteger(0);
    tigerProxy.addRbelMessageListener(message -> callCounter.incrementAndGet());

    proxyRest.get("http://backend/foobar").asString().getBody();
    awaitMessagesInTiger(2);

    assertThat(callCounter.get()).isEqualTo(2);
  }

  @Test
  void implicitReverseProxy_shouldForwardReqeust() {
    final AtomicInteger callCounter = new AtomicInteger(0);

    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("/notAServer")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());

    tigerProxy.addRbelMessageListener(message -> callCounter.incrementAndGet());

    // no (forward)-proxy! we use the tiger-proxy as a reverse-proxy
    Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/notAServer/foobar").asString();
    awaitMessagesInTiger(2);

    assertThat(callCounter.get()).isEqualTo(2);
    assertThat(tigerProxy.getRbelMessagesList().get(1))
        .extractChildWithPath("$.body.foo.content")
        .hasStringContentEqualTo("bar");
  }

  @Test
  void blanketReverseProxy_shouldForwardReqeust() {
    final AtomicInteger callCounter = new AtomicInteger(0);

    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("/")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());

    tigerProxy.addRbelMessageListener(message -> callCounter.incrementAndGet());

    // no (forward)-proxy! we use the tiger-proxy as a reverse-proxy
    Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar").asString();
    awaitMessagesInTiger(2);

    assertThat(callCounter.get()).isEqualTo(2);
  }

  @Test
  void activateFileSaving_shouldAddRouteTrafficToFile() {
    final String filename = "target/test-log.tgr";
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("http://backend")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .fileSaveInfo(
                TigerFileSaveInfo.builder()
                    .writeToFile(true)
                    .clearFileOnBoot(true)
                    .filename(filename)
                    .build())
            .build());

    proxyRest.get("http://backend/foobar").asString();

    await().atMost(2, TimeUnit.SECONDS).until(() -> new File(filename).exists());
  }

  @Test
  void activateFileSaving_shouldAddRoutelessTrafficToFile() {
    final String filename = "target/test-log.tgr";
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .activateForwardAllLogging(true)
            .fileSaveInfo(
                TigerFileSaveInfo.builder()
                    .writeToFile(true)
                    .clearFileOnBoot(true)
                    .filename(filename)
                    .build())
            .build());

    proxyRest.get("http://localhost:" + fakeBackendServerPort + "/foobar").asString();

    await().atMost(2, TimeUnit.SECONDS).until(() -> new File(filename).exists());
  }

  @Test
  void basicAuthenticationRequiredAndConfigured_ShouldWork(WireMockRuntimeInfo runtimeInfo) {

    runtimeInfo
        .getWireMock()
        .register(
            stubFor(
                get("/authenticatedPath")
                    .withBasicAuth("user", "password")
                    .willReturn(aResponse().withStatus(777).withBody("{\"foo\":\"bar\"}"))));

    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("http://backendWithBasicAuth")
                        .to("http://localhost:" + fakeBackendServerPort)
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
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("http://backend")
                        .to("http://notARealServer")
                        .build()))
            .forwardToProxy(
                ForwardProxyInfo.builder()
                    .port(forwardProxy.getPort())
                    .hostname("localhost")
                    .type(TigerProxyType.HTTP)
                    .build())
            .build());

    final HttpResponse<JsonNode> response = proxyRest.get("http://backend/foobar").asJson();

    assertThat(response.getStatus()).isEqualTo(666);
  }

  @Test
  void reverseProxyRouteViaAnotherForwardProxy() {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(TigerRoute.builder().from("/").to("http://notARealServer").build()))
            .forwardToProxy(
                ForwardProxyInfo.builder()
                    .port(forwardProxy.getPort())
                    .hostname("localhost")
                    .type(TigerProxyType.HTTP)
                    .build())
            .build());

    final HttpResponse<JsonNode> response =
        Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar").asJson();

    assertThat(response.getStatus()).isEqualTo(666);
  }

  @SneakyThrows
  @Test
  // gemSpec_Krypt, A_21888
  void tigerProxyShouldHaveFixedVauKeyLoaded() {
    BrainpoolCurves.init();
    final Key key =
        KeyMgr.readKeyFromPem(
            FileUtils.readFileToString(new File("src/test/resources/fixVauKey.pem")));

    final JsonWebSignature jws = new JsonWebSignature();
    jws.setKey(key);
    jws.setPayload("foobar");
    jws.setAlgorithmHeaderValue("BP256R1");
    final String jwsSerialized = jws.getCompactSerialization();

    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("http://backend")
                        .to("http://localhost:" + fakeBackendServerPort + "/foobar")
                        .build()))
            .build());

    proxyRest.get("http://backend?jws=" + jwsSerialized).asString();
    awaitMessagesInTiger(2);

    assertThat(tigerProxy.getRbelMessagesList().get(0))
        .extractChildWithPath("$.path.jws.value.signature.isValid")
        .hasValueEqualTo(Boolean.TRUE);
  }

  @Test
  void forwardProxyWithQueryParameters(WireMockRuntimeInfo runtimeInfo) {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("http://backend")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());

    proxyRest.get("http://backend/foobar?foo=bar1&foo=bar2&schmoo").asString();

    assertThat(getLastRequest(runtimeInfo.getWireMock()).getQueryParams())
        .containsOnlyKeys("foo", "schmoo");
    assertThat(getLastRequest(runtimeInfo.getWireMock()).getQueryParams().get("foo").getValues())
        .containsExactly("bar1", "bar2");
    assertThat(getLastRequest(runtimeInfo.getWireMock()).getQueryParams().get("schmoo").getValues())
        .containsExactly("");
  }

  @Test
  void reverseProxyWithQueryParameters(WireMockRuntimeInfo runtimeInfo) {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("/")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());

    Unirest.get(
            "http://localhost:" + tigerProxy.getProxyPort() + "/foobar?foo=bar1&foo=bar2&schmoo")
        .asString();

    assertThat(getLastRequest(runtimeInfo.getWireMock()).getQueryParams())
        .containsOnlyKeys("foo", "schmoo");
    assertThat(getLastRequest(runtimeInfo.getWireMock()).getQueryParams().get("foo").getValues())
        .containsExactly("bar1", "bar2");
    assertThat(getLastRequest(runtimeInfo.getWireMock()).getQueryParams().get("schmoo").getValues())
        .containsExactly("");
  }

  @Test
  void forwardProxy_checkClientAddresses() {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("http://backend")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());

    final UnirestInstance unirestInstance = Unirest.spawnInstance();
    unirestInstance.config().proxy("localhost", tigerProxy.getProxyPort());
    unirestInstance.get("http://backend/foobar").asString();

    final UnirestInstance secondInstance = Unirest.spawnInstance();
    secondInstance.config().proxy("localhost", tigerProxy.getProxyPort());
    secondInstance.get("http://backend/foobar").asString();

    awaitMessagesInTiger(4);

    checkMessageAddresses(LOCALHOST_REGEX, "backend");
    checkMessagePorts();
  }

  @Test
  void reverseProxy_checkClientAddresses() {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("/")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());

    final UnirestInstance unirestInstance = Unirest.spawnInstance();
    unirestInstance.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar").asString();

    final UnirestInstance secondInstance = Unirest.spawnInstance();
    secondInstance.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar").asString();

    awaitMessagesInTiger(4);

    checkMessageAddresses(LOCALHOST_REGEX, LOCALHOST_REGEX);
    checkMessagePorts();
  }

  @Test
  void forwardAllRoute_checkClientAddresses() {
    spawnTigerProxyWith(TigerProxyConfiguration.builder().build());

    final UnirestInstance unirestInstance = Unirest.spawnInstance();
    unirestInstance.config().proxy("localhost", tigerProxy.getProxyPort());
    unirestInstance.get("http://localhost:" + fakeBackendServerPort + "/foobar").asString();

    final UnirestInstance secondInstance = Unirest.spawnInstance();
    secondInstance.config().proxy("localhost", tigerProxy.getProxyPort());
    secondInstance.get("http://localhost:" + fakeBackendServerPort + "/foobar").asString();

    // make sure both messages have been parsed successfully
    await()
        .pollInterval(200, TimeUnit.MILLISECONDS)
        .atMost(20, TimeUnit.SECONDS)
        .until(() -> extractHostnames(RbelTcpIpMessageFacet::getReceiver).toList().size() == 4);

    checkMessageAddresses(LOCALHOST_REGEX, LOCALHOST_REGEX);
    checkMessagePorts();
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
    int availableTcpPort =
        TigerGlobalConfiguration.readIntegerOptional("free.port.100").orElseThrow();
    spawnTigerProxyWith(TigerProxyConfiguration.builder().proxyPort(availableTcpPort).build());

    assertThat(tigerProxy.getProxyPort()).isEqualTo(availableTcpPort);
  }

  @Test
  void forwardProxy_shouldAddTimingInformation() {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("http://backend")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());

    proxyRest.get("http://backend/foobar").asString();
    awaitMessagesInTiger(2);

    assertThat(
            tigerProxy
                .getRbelMessagesList()
                .get(0)
                .getFacetOrFail(RbelMessageTimingFacet.class)
                .getTransmissionTime())
        .isCloseTo(ZonedDateTime.now(), new TemporalUnitWithinOffset(1, ChronoUnit.SECONDS));
    assertThat(
            tigerProxy
                .getRbelMessagesList()
                .get(1)
                .getFacetOrFail(RbelMessageTimingFacet.class)
                .getTransmissionTime())
        .isCloseTo(ZonedDateTime.now(), new TemporalUnitWithinOffset(1, ChronoUnit.SECONDS));
  }

  @Test
  void reverseProxy_shouldAddTimingInformation() {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("/")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());

    Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar").asString();
    awaitMessagesInTiger(2);

    assertThat(
            tigerProxy
                .getRbelMessagesList()
                .get(0)
                .getFacetOrFail(RbelMessageTimingFacet.class)
                .getTransmissionTime())
        .isCloseTo(ZonedDateTime.now(), new TemporalUnitWithinOffset(1, ChronoUnit.SECONDS));
    assertThat(
            tigerProxy
                .getRbelMessagesList()
                .get(1)
                .getFacetOrFail(RbelMessageTimingFacet.class)
                .getTransmissionTime())
        .isCloseTo(ZonedDateTime.now(), new TemporalUnitWithinOffset(1, ChronoUnit.SECONDS));
  }

  @Test
  void forwardAllRoute_shouldAddTimingInformation() {
    spawnTigerProxyWith(TigerProxyConfiguration.builder().build());

    final UnirestInstance unirestInstance = Unirest.spawnInstance();
    unirestInstance.config().proxy("localhost", tigerProxy.getProxyPort());
    unirestInstance.get("http://localhost:" + fakeBackendServerPort + "/foobar").asString();
    awaitMessagesInTiger(2);

    assertThat(
            tigerProxy
                .getRbelMessagesList()
                .get(0)
                .getFacetOrFail(RbelMessageTimingFacet.class)
                .getTransmissionTime())
        .isCloseTo(ZonedDateTime.now(), new TemporalUnitWithinOffset(1, ChronoUnit.SECONDS));
    assertThat(
            tigerProxy
                .getRbelMessagesList()
                .get(1)
                .getFacetOrFail(RbelMessageTimingFacet.class)
                .getTransmissionTime())
        .isCloseTo(ZonedDateTime.now(), new TemporalUnitWithinOffset(1, ChronoUnit.SECONDS));
  }

  /**
   * Request to a mainserver, which in turns queries a backend-server. The timing information should
   * be correct, the order in the Rbel-Log should be different.
   */
  @Test
  void querySecondBackendServer_timingInformationShouldBeCorrect(WireMockRuntimeInfo runtimeInfo) {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("http://server")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build(),
                    TigerRoute.builder()
                        .from("/")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());

    runtimeInfo
        .getWireMock()
        .register(
            stubFor(
                get("/mainserver")
                    .willReturn(
                        aResponse()
                            .proxiedFrom(
                                "http://localhost:"
                                    + tigerProxy.getProxyPort()
                                    + "/deep/foobar"))));

    proxyRest.get("http://server/mainserver").asString();
    awaitMessagesInTiger(4);

    assertThat(
            tigerProxy.getRbelMessages().stream()
                .sorted(
                    Comparator.comparing(
                        el ->
                            el.getFacetOrFail(RbelMessageTimingFacet.class).getTransmissionTime()))
                .map(RbelElement::printHttpDescription)
                .toList())
        .containsExactly(
            "HTTP GET /mainserver with body ''",
            "HTTP GET /deep/foobar/mainserver with body ''",
            "HTTP 777 with body '{\"foo\":\"bar\"}'",
            "HTTP 777 with body '{\"foo\":\"bar\"}'");

    assertThat(
            tigerProxy.getRbelMessages().stream().map(RbelElement::printHttpDescription).toList())
        .containsExactly(
            "HTTP GET /deep/foobar/mainserver with body ''",
            "HTTP 777 with body '{\"foo\":\"bar\"}'",
            "HTTP GET /mainserver with body ''",
            "HTTP 777 with body '{\"foo\":\"bar\"}'");
  }

  @Test
  void xmlContentTypeWithNonConformingEncodedCharacter_shouldPreserveContent() throws UnirestException {
    final byte[] rawContent = "hellö\uD83D\uDC4C\uD83C\uDFFB".getBytes(StandardCharsets.UTF_8);

    spawnTigerProxyWith(TigerProxyConfiguration.builder()
      .proxyRoutes(List.of(TigerRoute.builder()
        .from("http://backend")
        .to("http://localhost:" + fakeBackendServerPort)
        .build()))
      .build());

    final HttpResponse<byte[]> response = proxyRest.post("http://backend/echo")
      .body(rawContent)
      .contentType(MediaType.APPLICATION_XML.toString())
      .asBytes();

    awaitMessagesInTiger(2);

    assertThat(response.getBody())
      .isEqualTo(rawContent);

    assertThat(tigerProxy.getRbelMessagesList().get(0))
      .extractChildWithPath("$.body")
      .getContent()
      .isEqualTo(rawContent);
  }

  @Test
  void emptyBuffer_shouldNotRetainMessages() {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .rbelBufferSizeInMb(0)
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("http://backend")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());

    proxyRest
        .get("http://backend/foobar")
        .header("foo", "bar")
        .header("x-forwarded-for", "someStuff")
        .asString();

    assertThat(tigerProxy.getRbelMessagesList()).isEmpty();
  }

  @Test
  void limitMaxMessageSize_shouldSkipParsing() {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("/")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .skipParsingWhenMessageLargerThanKb(1)
            .build());

    Unirest.post("http://localhost:" + tigerProxy.getProxyPort() + "/foobar")
        .body("{'foobar':'" + RandomStringUtils.randomAlphanumeric(2_000) + "'}")
        .asString();
    awaitMessagesInTiger(2);

    assertThat(tigerProxy.getRbelMessagesList().get(0))
        .doesNotContainChildWithPath("$.body.foobar");
  }

  @Test
  void reverseProxy_parsingShouldNotBlockCommunication() {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("/")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());
    AtomicBoolean messageWasReceivedInTheClient = new AtomicBoolean(false);
    AtomicBoolean allowMessageParsingToComplete = new AtomicBoolean(false);

    final RbelConverterPlugin blockConversionUntilCommunicationIsComplete =
        (el, conv) -> {
          log.info("Entering wait");
          await().atMost(2, TimeUnit.SECONDS).until(messageWasReceivedInTheClient::get);
          allowMessageParsingToComplete.set(true);
          log.info("Exiting wait");
        };
    tigerProxy
        .getRbelLogger()
        .getRbelConverter()
        .addPostConversionListener(blockConversionUntilCommunicationIsComplete);

    Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar").asString();

    log.info("Message was received in the client...");
    messageWasReceivedInTheClient.set(true);

    await().atMost(2, TimeUnit.SECONDS).until(allowMessageParsingToComplete::get);
  }

  @Test
  @SuppressWarnings("java:S2925")
  void reverseProxy_parsingShouldBlockCommunicationIfConfigured() throws InterruptedException {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("/")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .parsingShouldBlockCommunication(true)
            .build());
    AtomicBoolean clientHasWaitedAndNotReceivedMessageYet = new AtomicBoolean(false);
    AtomicBoolean messageParsingHasStarted = new AtomicBoolean(false);

    final RbelConverterPlugin blockConversionUntilCommunicationIsComplete =
        (el, conv) -> {
          log.info("Entering wait with " + el.getRawStringContent());
          messageParsingHasStarted.set(true);
          await().atMost(20, TimeUnit.SECONDS).until(clientHasWaitedAndNotReceivedMessageYet::get);
          log.info("Exiting wait");
        };
    tigerProxy
        .getRbelLogger()
        .getRbelConverter()
        .addPostConversionListener(blockConversionUntilCommunicationIsComplete);

    final CompletableFuture<HttpResponse<String>> asyncMessage =
        Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar").asStringAsync();
    await().atMost(20, TimeUnit.SECONDS).until(messageParsingHasStarted::get);

    // guarantee that a parse would succeed by now
    Thread.sleep(100);

    assertThat(asyncMessage.isDone()).isFalse();

    log.info("Switching clientHasWaitedAndNotReceivedMessageYet...");
    clientHasWaitedAndNotReceivedMessageYet.set(true);
    await().atMost(20, TimeUnit.SECONDS).until(asyncMessage::isDone);
  }

  @Test
  void queryParametersStartWithExclamationMark_shouldTransmitUnaltered() {
    spawnTigerProxyWith(
      TigerProxyConfiguration.builder()
        .proxyRoutes(
          List.of(
            TigerRoute.builder()
              .from("/")
              .to("http://localhost:" + fakeBackendServerPort)
              .build()))
        .build());

    Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar?foo=!bar&!foo=bar").asString();
    awaitMessagesInTiger(2);

    assertThat(tigerProxy.getRbelMessagesList().get(0))
      .extractChildWithPath("$.path.foo.value")
      .hasStringContentEqualTo("!bar");
    assertThat(tigerProxy.getRbelMessagesList().get(0))
      .extractChildWithPath("$.path.!foo.value")
      .hasStringContentEqualTo("bar");
  }

  private void checkMessageAddresses(final String clientRegex, final String serverRegex) {
    final List<String> extractedReceiverHostnames =
        extractHostnames(RbelTcpIpMessageFacet::getReceiver).collect(Collectors.toList());
    final List<String> extractedSenderHostnames =
        extractHostnames(RbelTcpIpMessageFacet::getSender).toList();
    log.info(
        "hostnames: {} and {} (sender and receiver), matching to {} and {}",
        extractedSenderHostnames,
        extractedReceiverHostnames,
        clientRegex,
        serverRegex);
    for (int i = 0; i < extractedSenderHostnames.size(); i++) {
      String senderRegex = i % 2 == 0 ? clientRegex : serverRegex;
      String receiverRegex = i % 2 == 0 ? serverRegex : clientRegex;
      assertThat(extractedSenderHostnames.get(i)).matches(senderRegex);
      assertThat(extractedReceiverHostnames.get(i)).matches(receiverRegex);
    }
  }

  private void checkMessagePorts() {
    assertThat(
            tigerProxy
                .getRbelMessagesList()
                .get(1)
                .getFacetOrFail(RbelTcpIpMessageFacet.class)
                .getSenderHostname()
                .getPort())
        .isPositive()
        .isEqualTo(
            tigerProxy
                .getRbelMessagesList()
                .get(0)
                .getFacetOrFail(RbelTcpIpMessageFacet.class)
                .getReceiverHostname()
                .getPort());
    assertThat(
            tigerProxy
                .getRbelMessagesList()
                .get(2)
                .getFacetOrFail(RbelTcpIpMessageFacet.class)
                .getSenderHostname()
                .getPort())
        .isPositive()
        .isEqualTo(
            tigerProxy
                .getRbelMessagesList()
                .get(3)
                .getFacetOrFail(RbelTcpIpMessageFacet.class)
                .getReceiverHostname()
                .getPort());
    assertThat(
            tigerProxy
                .getRbelMessagesList()
                .get(0)
                .getFacetOrFail(RbelTcpIpMessageFacet.class)
                .getSenderHostname()
                .getPort())
        .isPositive()
        .isNotEqualTo(
            tigerProxy
                .getRbelMessagesList()
                .get(2)
                .getFacetOrFail(RbelTcpIpMessageFacet.class)
                .getSenderHostname()
                .getPort());
  }

  @NotNull
  private Stream<String> extractHostnames(
      final Function<RbelTcpIpMessageFacet, RbelElement> hostnameExtractor) {
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

  @Test
  void forwardProxy_queryParamsWithSpaces_shouldNotBeRewritten() {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("http://foo.bar")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());

    proxyRest.get("http://foo.bar/foobar?foo=this%20is%20bar").asString();
    awaitMessagesInTiger(2);

    assertThat(tigerProxy.getRbelMessagesList().get(0))
        .extractChildWithPath("$.path.foo")
        .hasStringContentEqualTo("foo=this%20is%20bar");
    assertThat(tigerProxy.getRbelMessagesList().get(0))
        .extractChildWithPath("$.path.foo.value")
        .hasStringContentEqualTo("this is bar");
  }
}
