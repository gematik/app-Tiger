/*
 * Copyright 2024 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.facet.RbelHostnameFacet;
import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import de.gematik.rbellogger.data.facet.RbelMessageTimingFacet;
import de.gematik.rbellogger.data.facet.RbelTcpIpMessageFacet;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.*;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import java.io.File;
import java.nio.charset.StandardCharsets;
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
import lombok.val;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.NoHttpResponseException;
import org.assertj.core.data.TemporalUnitWithinOffset;
import org.jetbrains.annotations.NotNull;
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
    if (!forwardProxy.getStubMappings().isEmpty()) {
      return;
    }
    log.info(
        "Started Forward-Proxy-Server on port {} (fake backend on {})",
        forwardProxy.getPort(),
        runtimeInfo.getHttpPort());

    forwardProxy.stubFor(
        any(anyUrl())
            .willReturn(aResponse().proxiedFrom("http://localhost:" + runtimeInfo.getHttpPort())));
  }

  @Test
  void forwardProxy_shouldUseSameTcpConnection() {
    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());

    final HttpResponse<JsonNode> response = proxyRest.get("http://backend/foobar").asJson();
    final HttpResponse<JsonNode> response2 = proxyRest.get("http://backend/foobar").asJson();

    awaitMessagesInTiger(4);

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

    assertThat(response2.getStatus()).isEqualTo(666);
    assertThat(response2.getBody().getObject().get("foo")).hasToString("bar");
    assertThat(
            tigerProxy
                .getRbelMessagesList()
                .get(2)
                .getFacetOrFail(RbelTcpIpMessageFacet.class)
                .getReceiverHostname())
        .isEqualTo(new RbelHostname("backend", 80));
    assertThat(
            tigerProxy
                .getRbelMessagesList()
                .get(3)
                .getFacetOrFail(RbelTcpIpMessageFacet.class)
                .getSenderHostname())
        .isEqualTo(new RbelHostname("backend", 80));
  }

  @Test
  void useAsWebProxyServer_shouldForward() {
    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());

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
    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());

    proxyRest
        .get("http://backend/foobar")
        .header("foo", "bar")
        .header("x-forwarded-for", "someStuff")
        .asString();
    awaitMessagesInTiger(2);

    assertThat(tigerProxy.getRbelMessagesList().get(0))
        .extractChildWithPath("$.header.foo")
        .hasStringContentEqualTo("bar")
        .andTheInitialElement()
        .extractChildWithPath("$.header.x-forwarded-for")
        .hasStringContentEqualTo("someStuff")
        .andTheInitialElement()
        .extractChildWithPath("$.header.Host")
        .hasStringContentEqualTo("localhost:" + fakeBackendServerPort);
    assertThat(tigerProxy.getRbelMessagesList().get(1))
        .extractChildWithPath("$.header.[~'content-length']")
        .hasStringContentEqualTo("13");
  }

  @Test
  void reverseProxy_headersShouldBeUntouched() {
    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());

    Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar")
        .header("foo", "bar")
        .header("x-forwarded-for", "someStuff")
        .header("Host", "RandomStuffShouldBePreserved")
        .asString();
    Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar")
        .header("foo", "bar")
        .header("x-forwarded-for", "someStuff")
        .header("Host", "RandomStuffShouldBePreserved")
        .asString();
    Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar")
        .header("foo", "bar")
        .header("x-forwarded-for", "someStuff")
        .header("Host", "RandomStuffShouldBePreserved")
        .asString();
    awaitMessagesInTiger(6);

    final RbelElement request = tigerProxy.getRbelMessagesList().get(0);
    assertThat(request).extractChildWithPath("$.header.foo").hasStringContentEqualTo("bar");
    assertThat(request)
        .extractChildWithPath("$.header.x-forwarded-for")
        .hasStringContentEqualTo("someStuff");
    assertThat(request)
        .extractChildWithPath("$.header.Host")
        .hasStringContentEqualTo("RandomStuffShouldBePreserved");
    assertThat(tigerProxy.getRbelMessagesList().get(1))
        .extractChildWithPath("$.header.[~'content-length']")
        .hasStringContentEqualTo("13");
  }

  @Test
  void reverseProxy_shouldRewriteHostHeaderIfConfigured() {
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder().rewriteHostHeader(true).build());

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
    spawnTigerProxyWithDefaultRoutesAndWith(
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
    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());

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
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
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
    spawnTigerProxyWithDefaultRoutesAndWith(
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
    spawnTigerProxyWithDefaultRoutesAndWith(
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
  void binaryMessage_shouldGiveBinaryResult(WireMockRuntimeInfo runtimeInfo) {
    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());

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
    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());

    proxyRest.get("http://backend/foobar").asString().getBody();
    awaitMessagesInTiger(2);

    assertThat(tigerProxy.getRbelMessagesList().get(1))
        .extractChildWithPath("$.body.foo.content")
        .hasStringContentEqualTo("bar");
  }

  @Test
  void registerListenerThenSentRequest_shouldTriggerListener() throws UnirestException {
    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());

    final AtomicInteger callCounter = new AtomicInteger(0);
    tigerProxy.addRbelMessageListener(message -> callCounter.incrementAndGet());

    proxyRest.get("http://backend/foobar").asString().getBody();
    awaitMessagesInTiger(2);

    assertThat(callCounter.get()).isEqualTo(2);
  }

  @Test
  void implicitReverseProxy_shouldForwardRequest() {
    final AtomicInteger callCounter = new AtomicInteger(0);

    spawnTigerProxyWithDefaultRoutesAndWith(
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
  void blanketReverseProxy_shouldForwardRequest() {
    final AtomicInteger callCounter = new AtomicInteger(0);

    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());

    tigerProxy.addRbelMessageListener(message -> callCounter.incrementAndGet());

    // no (forward)-proxy! we use the tiger-proxy as a reverse-proxy
    Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar").asString();
    awaitMessagesInTiger(2);

    assertThat(callCounter.get()).isEqualTo(2);
  }

  @Test
  void activateFileSaving_shouldAddRouteTrafficToFile() {
    final String filename = "target/test-log.tgr";
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
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
    spawnTigerProxyWithDefaultRoutesAndWith(
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

    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("http://backendWithBasicAuth")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .basicAuth(new TigerBasicAuthConfiguration("user", "password"))
                        .build()))
            .build());

    assertThatThrownBy(() -> proxyRest.get("/authenticatedPath").asJson())
        .isInstanceOf(UnirestException.class);
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

    final HttpResponse<JsonNode> response = proxyRest.get("http://backend/deep/foobar").asJson();

    assertThat(response.getStatus()).isEqualTo(777);
  }

  @SneakyThrows
  @Test
  void reverseProxyRouteViaAnotherForwardProxy() {
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(TigerRoute.builder().from("/").to("http://notARealServer").build()))
            .forwardToProxy(
                ForwardProxyInfo.builder()
                    .port(forwardProxy.getPort())
                    .hostname("localhost")
                    .build())
            .build());

    final HttpResponse<JsonNode> response =
        Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/deep/foobar").asJson();

    assertThat(response.getStatus()).isEqualTo(777);
  }

  @SneakyThrows
  @Test
  // gemSpec_Krypt, A_21888
  void tigerProxyShouldHaveFixedVauKeyLoaded() {
    final String jwsSerialized =
        "eyJhbGciOiJCUDI1NlIxIn0.Zm9vYmFy.lNinGioEewNWK1IJyBzteAbDRixKemqPYkbYbVj_HOJrxwnyUitcrnB3mrXsFYenetnYTCLCviaMwVW7xA33cw";

    spawnTigerProxyWithDefaultRoutesAndWith(
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
    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());

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
    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());

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
    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());

    final UnirestInstance unirestInstance = Unirest.spawnInstance();
    unirestInstance.config().proxy("localhost", tigerProxy.getProxyPort());
    unirestInstance.get("http://backend/foobar").asString();

    log.info("Now second instance");

    final UnirestInstance secondInstance = Unirest.spawnInstance();
    secondInstance.config().proxy("localhost", tigerProxy.getProxyPort());
    secondInstance.get("http://backend/foobar").asString();

    awaitMessagesInTiger(4);

    checkMessageAddresses(LOCALHOST_REGEX, "backend");
    checkMessagePorts();
  }

  @Test
  void reverseProxy_checkClientAddresses() {
    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());

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
    spawnTigerProxyWithDefaultRoutesAndWith(TigerProxyConfiguration.builder().build());

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
    spawnTigerProxyWithDefaultRoutesAndWith(TigerProxyConfiguration.builder().build());
    assertThat(tigerProxy.getProxyPort()).isBetween(10000, 100000);
  }

  @Test
  void checkGetNotSetTigerProxyPort_ShouldThrowTigerConfigurationException() {
    TigerGlobalConfiguration.reset();
    int availableTcpPort =
        TigerGlobalConfiguration.readIntegerOptional("free.port.100").orElseThrow();
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder().proxyPort(availableTcpPort).build());

    assertThat(tigerProxy.getProxyPort()).isEqualTo(availableTcpPort);
  }

  @Test
  void forwardProxy_shouldAddTimingInformation() {
    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());

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
    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());

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
    spawnTigerProxyWithDefaultRoutesAndWith(TigerProxyConfiguration.builder().build());

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
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("http://server")
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
            "HTTP GET /mainserver with body ''",
            "HTTP GET /deep/foobar/mainserver with body ''",
            "HTTP 777 with body '{\"foo\":\"bar\"}'",
            "HTTP 777 with body '{\"foo\":\"bar\"}'");
  }

  @Test
  void xmlContentTypeWithNonConformingEncodedCharacter_shouldPreserveContent()
      throws UnirestException {
    final byte[] rawContent = "hellö\uD83D\uDC4C\uD83C\uDFFB".getBytes(StandardCharsets.UTF_8);

    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());

    final HttpResponse<byte[]> response =
        proxyRest
            .post("http://backend/echo")
            .body(rawContent)
            .contentType(MediaType.APPLICATION_XML.toString())
            .asBytes();

    awaitMessagesInTiger(2);

    assertThat(response.getBody()).isEqualTo(rawContent);

    assertThat(tigerProxy.getRbelMessagesList().get(0))
        .extractChildWithPath("$.body")
        .getContent()
        .isEqualTo(rawContent);
  }

  @Test
  void emptyBuffer_shouldNotRetainMessages() {
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder().rbelBufferSizeInMb(0).build());

    proxyRest
        .get("http://backend/foobar")
        .header("foo", "bar")
        .header("x-forwarded-for", "someStuff")
        .asString();

    assertThat(tigerProxy.getRbelMessagesList()).isEmpty();
  }

  @Test
  void limitMaxMessageSize_shouldSkipParsing() {
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder().skipParsingWhenMessageLargerThanKb(1).build());

    Unirest.post("http://localhost:" + tigerProxy.getProxyPort() + "/foobar")
        .body("{'foobar':'" + RandomStringUtils.insecure().nextAlphanumeric(2_000) + "'}")
        .asString();
    awaitMessagesInTiger(2);

    assertThat(tigerProxy.getRbelMessagesList().get(0)).doesNotHaveChildWithPath("$.body.foobar");
  }

  @Test
  void queryParametersStartWithExclamationMark_shouldTransmitUnaltered() {
    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());

    Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar?foo=!bar&!foo=bar")
        .asString();
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
    spawnTigerProxyWithDefaultRoutesAndWith(
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

  @Test
  void emptyStatusMessageFromBackend_shouldBeEmptyAfterTigerProxyAsWell() {
    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());

    final HttpResponse<String> response =
        proxyRest.post("http://backend/echo").body("Hello World!").asString();

    awaitMessagesInTiger(2);

    assertThat(response.getStatusText()).isEmpty();
    assertThat(tigerProxy.getRbelMessagesList().get(1))
        .extractChildWithPath("$.reasonPhrase")
        .hasNullContent();
  }

  @SneakyThrows
  @Test
  void connectionReset_shouldKeepRequestInLog() {
    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());

    proxyRest.config().automaticRetries(false);
    assertThatThrownBy(() -> proxyRest.get("http://backend/error").asString())
        .isInstanceOf(UnirestException.class)
        .hasCauseInstanceOf(NoHttpResponseException.class);

    awaitMessagesInTiger(2);

    assertThat(tigerProxy.getRbelMessagesList().get(1))
        .extractChildWithPath("$.sender")
        .hasStringContentEqualTo("backend:80");
  }

  @SneakyThrows
  @Test
  void noRoutes_unkownHost_shouldCloseConnection() {
    spawnTigerProxyWith(new TigerProxyConfiguration());

    val request = proxyRest.get("http://foobar/");
    assertThatThrownBy(request::asEmpty).hasRootCauseInstanceOf(NoHttpResponseException.class);
  }

  @SneakyThrows
  @Test
  void noRoutes_reverseProxyRequest_shouldCloseConnection() {
    spawnTigerProxyWith(new TigerProxyConfiguration());

    val request = Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/");
    assertThatThrownBy(request::asEmpty).hasRootCauseInstanceOf(NoHttpResponseException.class);
  }
}
