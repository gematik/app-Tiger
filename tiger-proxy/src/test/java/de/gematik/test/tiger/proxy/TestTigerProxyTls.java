/*
 *
 * Copyright 2021-2025 gematik GmbH
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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.test.tiger.proxy;

import static de.gematik.rbellogger.data.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static uk.org.webcompere.systemstubs.SystemStubs.restoreSystemProperties;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import de.gematik.rbellogger.data.RbelElementAssertion;
import de.gematik.rbellogger.util.CryptoLoader;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerConfigurationRoute;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration.TigerProxyConfigurationBuilder;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerTlsConfiguration;
import de.gematik.test.tiger.common.pki.TigerConfigurationPkiIdentity;
import de.gematik.test.tiger.common.pki.TigerPkiIdentity;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.proxy.certificate.TlsFacet;
import io.restassured.RestAssured;
import io.restassured.config.SSLConfig;
import io.restassured.response.Response;
import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.*;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.net.ssl.*;
import kong.unirest.core.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpHost;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClients;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.bouncycastle.tls.CertificateRequest;
import org.bouncycastle.tls.DefaultTlsClient;
import org.bouncycastle.tls.TlsAuthentication;
import org.bouncycastle.tls.TlsClientProtocol;
import org.bouncycastle.tls.TlsCredentials;
import org.bouncycastle.tls.TlsFatalAlert;
import org.bouncycastle.tls.TlsServerCertificate;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@Slf4j
@TestInstance(Lifecycle.PER_CLASS)
@ResetTigerConfiguration
@WireMockTest(httpsEnabled = true)
class TestTigerProxyTls extends AbstractTigerProxyTest {

  @Test
  void reverseProxy_shouldUseConfiguredAlternativeNameInTlsCertificate()
      throws NoSuchAlgorithmException, KeyManagementException {
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
            .tls(TigerTlsConfiguration.builder().domainName("muahaha").build())
            .build());

    AtomicBoolean verifyWasCalledSuccessfully = new AtomicBoolean(false);
    SSLContext ctx = SSLContext.getInstance("TLSv1.2");
    ctx.init(
        null,
        new TrustManager[] {
          new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType) {}

            public void checkServerTrusted(X509Certificate[] chain, String authType) {
              assertThat(chain[0].getSubjectX500Principal().getName()).contains("muahaha");
              verifyWasCalledSuccessfully.set(true);
            }

            public X509Certificate[] getAcceptedIssuers() {
              return new X509Certificate[0];
            }
          }
        },
        new SecureRandom());
    try (final UnirestInstance unirestInstance =
        new UnirestInstance(new Config().sslContext(ctx))) {
      unirestInstance.get("https://localhost:" + tigerProxy.getProxyPort() + "/foobar").asString();
      assertThat(verifyWasCalledSuccessfully).isTrue();
    }
  }

  @Test
  void useTlsBetweenClientAndProxy_shouldForward() throws UnirestException {
    spawnTigerProxyWithDefaultRoutesAndWith(TigerProxyConfiguration.builder().build());

    final HttpResponse<JsonNode> response = proxyRest.get("https://backend/foobar").asJson();

    assertThat(response.getStatus()).isEqualTo(666);
    assertThat(response.getBody().getObject().get("foo")).hasToString("bar");
  }

  @Test
  void forwardProxyWithoutConfiguredServerName_certificateShouldContainCorrectServerName() {
    spawnTigerProxyWith(new TigerProxyConfiguration());

    final HttpResponse<JsonNode> response = proxyRest.get("https://google.com").asJson();
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @SneakyThrows
  @Test
  void serverCertificateChainShouldContainMultipleCertificatesIfGiven() throws UnirestException {
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerConfigurationRoute.builder()
                        .from("https://authn.aktor.epa.telematik-test")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .tls(
                TigerTlsConfiguration.builder()
                    .serverIdentity(
                        new TigerConfigurationPkiIdentity(
                            "src/test/resources/rsaStoreWithChain.jks;gematik"))
                    .build())
            .build());

    AtomicInteger callCounter = new AtomicInteger(0);

    try (final UnirestInstance unirestInstance = Unirest.spawnInstance()) {
      unirestInstance.config().verifySsl(true);
      unirestInstance.config().proxy("localhost", tigerProxy.getProxyPort());
      SSLContext ctx = SSLContext.getInstance("TLSv1.2");
      ctx.init(
          null,
          new TrustManager[] {
            new X509TrustManager() {
              public void checkClientTrusted(X509Certificate[] chain, String authType) {}

              public void checkServerTrusted(X509Certificate[] chain, String authType) {
                assertThat(chain).hasSize(3);
                callCounter.incrementAndGet();
              }

              public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
              }
            }
          },
          new SecureRandom());
      unirestInstance.config().sslContext(ctx);

      unirestInstance.get("https://authn.aktor.epa.telematik-test/foobar").asString();

      await().atMost(2, TimeUnit.SECONDS).until(() -> callCounter.get() > 0);
    }
  }

  @SneakyThrows
  @Test
  void eccBrainPoolServerCertificate_shouldWork() throws UnirestException {
    restoreSystemProperties(
        () -> {
          System.setProperty(
              "jdk.tls.namedGroups",
              "brainpoolP256r1, brainpoolP384r1, brainpoolP512r1, secp256r1, secp384r1");
          spawnTigerProxyWithDefaultRoutesAndWith(
              TigerProxyConfiguration.builder()
                  .proxyRoutes(
                      List.of(
                          TigerConfigurationRoute.builder()
                              .from("https://authn.aktor.epa.telematik-test")
                              .to("http://localhost:" + fakeBackendServerPort)
                              .build()))
                  .tls(
                      TigerTlsConfiguration.builder()
                          .serverIdentity(
                              new TigerConfigurationPkiIdentity(
                                  "src/test/resources/eccStoreWithChain.jks;gematik"))
                          .serverSslSuites(List.of("TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384"))
                          .build())
                  .build());

          assertThatNoException()
              .isThrownBy(proxyRest.get("https://authn.aktor.epa.telematik-test/foobar")::asString);
        });
  }

  @Test
  void rsaCaFileInP12File_shouldVerifyConnection() throws UnirestException {
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
            .tls(
                TigerTlsConfiguration.builder()
                    .serverRootCa(
                        new TigerConfigurationPkiIdentity(
                            "src/test/resources/selfSignedCa/rootCa.p12;00"))
                    .build())
            .build());

    final HttpResponse<JsonNode> response = proxyRest.get("https://backend/foobar").asJson();

    assertThat(response.getStatus()).isEqualTo(666);
    assertThat(response.getBody().getObject().get("foo")).hasToString("bar");
  }

  @Test
  void defunctCertificate_expectException() throws UnirestException {
    assertThatThrownBy(
            () ->
                new TigerConfigurationPkiIdentity(
                    "src/test/resources/selfSignedCa/rootCa.p12;wrongPassword"))
        .isInstanceOf(RuntimeException.class);
  }

  // TODO TGR-263 really fix this and reactivate @Test, Julian knows more
  public void customEccCaFileInTruststore_shouldVerifyConnection()
      throws UnirestException, IOException {
    final TigerPkiIdentity ca =
        CryptoLoader.getIdentityFromP12(
            FileUtils.readFileToByteArray(new File("src/test/resources/customCa.p12")), "00");

    final TigerProxy tigerProxy =
        new TigerProxy(
            TigerProxyConfiguration.builder()
                .tls(
                    TigerTlsConfiguration.builder()
                        .serverRootCa(
                            new TigerConfigurationPkiIdentity("src/test/resources/customCa.p12;00"))
                        .build())
                .build());

    try (UnirestInstance unirestInstance = Unirest.spawnInstance()) {
      unirestInstance.config().proxy("localhost", tigerProxy.getProxyPort());
      unirestInstance.config().verifySsl(true);
      unirestInstance.config().sslContext(tigerProxy.buildSslContext());

      final HttpResponse<JsonNode> response =
          unirestInstance.get("https://backend/foobar").asJson();

      assertThat(response.getStatus()).isEqualTo(666);
      assertThat(response.getBody().getObject().get("foo")).hasToString("bar");
    }
  }

  @Test
  void blanketReverseProxy_shouldForwardHttpsRequest() {
    AtomicInteger callCounter = new AtomicInteger(0);

    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
            .tls(
                TigerTlsConfiguration.builder()
                    .serverRootCa(
                        new TigerConfigurationPkiIdentity(
                            "src/test/resources/selfSignedCa/rootCa.p12;00"))
                    .build())
            .proxyRoutes(
                List.of(
                    TigerConfigurationRoute.builder()
                        .from("http://backend/")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());

    tigerProxy.addRbelMessageListener(msg -> callCounter.incrementAndGet());

    proxyRest.get("http://backend/foobar").asString();
    awaitMessagesInTigerProxy(2);

    assertThat(callCounter.get()).isEqualTo(2);
  }

  @Test
  void forwardMutualTlsAndTerminatingTls_shouldUseCorrectTerminatingCa() throws UnirestException {
    final TigerConfigurationPkiIdentity clientIdentity =
        new TigerConfigurationPkiIdentity("src/test/resources/rsa.p12;00");

    try (TigerProxy secondProxy =
        new TigerProxy(
            TigerProxyConfiguration.builder()
                .activateRbelParsingFor(List.of("X509"))
                .proxyRoutes(
                    List.of(
                        TigerConfigurationRoute.builder()
                            .from("/")
                            .to("http://localhost:" + fakeBackendServerPort)
                            .build()))
                .name("secondProxy")
                .build())) {

      spawnTigerProxyWith(
          TigerProxyConfiguration.builder()
              .proxyRoutes(
                  List.of(
                      TigerConfigurationRoute.builder()
                          .from("http://backend")
                          .to("https://localhost:" + secondProxy.getProxyPort())
                          .build()))
              .tls(TigerTlsConfiguration.builder().forwardMutualTlsIdentity(clientIdentity).build())
              .build());

      final HttpResponse<String> response = proxyRest.get("http://backend/foobar").asString();
      awaitMessagesInTigerProxy(secondProxy, 2);

      assertThat(response.getStatus()).isEqualTo(666);

      RbelElementAssertion.assertThat(secondProxy.getRbelMessagesList().get(1))
          .extractChildWithPath("$.clientTlsCertificateChain.0.subject")
          .valueAsString()
          .get()
          .usingComparator((s1, s2) -> splitDn(s1).containsAll(splitDn(s2)) ? 0 : 1)
          .isEqualTo(clientIdentity.getCertificate().getSubjectX500Principal().getName());
    }
  }

  @NotNull
  private static List<String> splitDn(String s1) {
    return Stream.of(s1.split(",")).map(String::trim).toList();
  }

  @ParameterizedTest
  @CsvSource({
    "'TLSv1,TLSv1.2', 'TLSv1.2', true, TLSv1.2",
    "'TLSv1,TLSv1.2,TLSv1.3', 'TLSv1.2', true, TLSv1.2",
    "'TLSv1', 'TLSv1.2', false, TLSv1",
    "'TLSv1.3', 'TLSv1.3', true, TLSv1.3"
  })
  @SneakyThrows
  void serverSslVersion_shouldBeHonored(
      String clientTlsVersion,
      String serverTlsVersion,
      boolean shouldConnect,
      String assertVersionUsed) {
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
            .tls(
                TigerTlsConfiguration.builder()
                    .serverTlsProtocols(Stream.of(serverTlsVersion.split(",")).toList())
                    .build())
            .build());

    SSLConnectionSocketFactory sslSocketFactory =
        new SSLConnectionSocketFactory(
            tigerProxy.getConfiguredTigerProxySslContext(),
            clientTlsVersion.split(","),
            null,
            (hostname, session) -> {
              assertThat(session.getProtocol()).isEqualTo(assertVersionUsed);
              return true;
            });
    try (var httpClient = HttpClients.custom().setSSLSocketFactory(sslSocketFactory).build()) {
      var request =
          RequestBuilder.get("https://localhost:" + tigerProxy.getProxyPort() + "/foobar").build();
      if (shouldConnect) {
        httpClient.execute(request);
        tigerProxy.waitForAllCurrentMessagesToBeParsed();
        assertThat(tigerProxy.getRbelMessagesList().get(0))
            .hasFacet(TlsFacet.class)
            .extractChildWithPath("$.tlsVersion")
            .hasStringContentEqualTo(assertVersionUsed);
        assertThat(tigerProxy.getRbelMessagesList().get(1))
            .hasFacet(TlsFacet.class)
            .extractChildWithPath("$.tlsVersion")
            .hasStringContentEqualTo(assertVersionUsed);
      } else {
        assertThatThrownBy(() -> httpClient.execute(request))
            .isInstanceOf(IllegalStateException.class);
      }
    }
  }

  // @Test
  // @Disabled("Waiting for the next mockserver release (>5.15.0)")
  // void extractSubjectDnFromClientCertificate_saveInTigerProxy() throws Exception {
  //  spawnTigerProxyWithDefaultRoutesAndWith(TigerProxyConfiguration.builder().build());

  //  try (UnirestInstance unirestInstance = Unirest.spawnInstance()) {
  //    unirestInstance.config().httpClient(loadSslContextForClientCert());
  //    unirestInstance.get("https://localhost:" + tigerProxy.getProxyPort() +
  // "/foobar").asString();
  //  }
  //  awaitMessagesInTiger(2);

  //  assertThat(tigerProxy.getRbelMessagesList().get(0))
  //      .extractChildWithPath("$.clientTlsCertificateChain.0.subject")
  //      .valueAsString()
  //      .get(InstanceOfAssertFactories.STRING)
  //      .contains("CN=mailuser-rsa1");
  // }

  // private CloseableHttpClient loadSslContextForClientCert() throws Exception {
  //  KeyStore trustStore = KeyStore.getInstance("PKCS12");

  //  try (FileInputStream instream = new FileInputStream("src/test/resources/mailuser-rsa1.p12")) {
  //    trustStore.load(instream, "00".toCharArray());
  //  }

  //  final SSLContext sslContext =
  //      SSLContexts.custom()
  //          .loadTrustMaterial(trustStore, new TrustAllStrategy())
  //          .loadKeyMaterial(trustStore, "00".toCharArray(), (aliases, socket) -> "alias")
  //          .build();

  //  return HttpClients.custom().setSSLContext(sslContext).build();
  // }

  @Test
  void perRouteCertificate_shouldBePresentedOnlyForThisRoute() throws UnirestException {
    final TigerConfigurationPkiIdentity serverIdentity =
        new TigerConfigurationPkiIdentity("src/test/resources/rsaStoreWithChain.jks;gematik");

    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
            .tls(TigerTlsConfiguration.builder().serverIdentity(serverIdentity).build())
            .proxyRoutes(
                List.of(
                    TigerConfigurationRoute.builder()
                        // aktor-gateway.gematik.de ist der DN des obigen zertifikats
                        .from("https://authn.aktor.epa.telematik-test")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build(),
                    TigerConfigurationRoute.builder()
                        .from("https://falsche-url")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());

    try (final UnirestInstance unirestInstance =
        new UnirestInstance(
            new Config()
                .proxy("localhost", tigerProxy.getProxyPort())
                .verifySsl(true)
                .sslContext(buildSslContextTrustingOnly(serverIdentity)))) {

      assertThat(
              unirestInstance
                  .get("https://authn.aktor.epa.telematik-test/foobar")
                  .asString()
                  .getStatus())
          .isEqualTo(666);

      assertThatThrownBy(() -> unirestInstance.get("https://falsche-url/foobar").asString())
          .rootCause()
          .isInstanceOf(CertificateException.class)
          .hasMessageContaining(
              "No subject alternative name found matching domain name falsche-url");
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA", "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA"})
  void configureServerTslSuites(String configuredSslSuite) {
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
            .tls(
                TigerTlsConfiguration.builder()
                    .serverSslSuites(List.of(configuredSslSuite))
                    .build())
            .build());

    SSLContext ctx = tigerProxy.buildSslContext();
    try (var unirestInstance = new UnirestInstance(new Config().sslContext(ctx))) {
      unirestInstance.get("https://localhost:" + tigerProxy.getProxyPort() + "/foobar").asString();

      tigerProxy.waitForAllCurrentMessagesToBeParsed();
      assertThat(tigerProxy.getRbelMessagesList().get(0))
          .hasFacet(TlsFacet.class)
          .extractChildWithPath("$.cipherSuite")
          .hasStringContentEqualTo(configuredSslSuite);
      assertThat(tigerProxy.getRbelMessagesList().get(1))
          .hasFacet(TlsFacet.class)
          .extractChildWithPath("$.cipherSuite")
          .hasStringContentEqualTo(configuredSslSuite);
      assertThat(
              ctx.getClientSessionContext()
                  .getSession(ctx.getClientSessionContext().getIds().nextElement())
                  .getCipherSuite())
          .isEqualTo(configuredSslSuite);
    }
  }

  @Test
  void mutualTlsWithEcc_konnektorGradeHandshake() {
    final TigerConfigurationPkiIdentity clientIdentity =
        new TigerConfigurationPkiIdentity("src/test/resources/gateway_ecc.p12;00");

    int serverPort = startKonnektorAlikeServerReturningAlways555(Optional.of(clientIdentity));

    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerConfigurationRoute.builder()
                        .from("http://backend")
                        .to("https://localhost:" + serverPort)
                        .build()))
            .tls(
                TigerTlsConfiguration.builder()
                    .forwardMutualTlsIdentity(clientIdentity)
                    .clientSslSuites(
                        List.of(
                            "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
                            "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384"))
                    .clientSupportedGroups(
                        List.of("brainpoolP256r1", "brainpoolP384r1", "secp256r1", "secp384r1"))
                    .build())
            .build());

    final HttpResponse<String> response = proxyRest.get("http://backend/foobar").asString();
    assertThat(response.getStatus()).isEqualTo(555);
  }

  @Test
  void autoconfigureSslContextUnirest_shouldTrustTigerProxy() {
    spawnTigerProxyWithDefaultRoutesAndWith(TigerProxyConfiguration.builder().build());

    try (var restInstanceWithSslContextConfigured = Unirest.spawnInstance()) {
      restInstanceWithSslContextConfigured.config().proxy("localhost", tigerProxy.getProxyPort());
      restInstanceWithSslContextConfigured.config().sslContext(tigerProxy.buildSslContext());

      final HttpResponse<JsonNode> response =
          restInstanceWithSslContextConfigured.get("https://backend/foobar").asJson();

      assertThat(response.getStatus()).isEqualTo(666);
      assertThat(response.getBody().getObject().get("foo")).hasToString("bar");
    }
  }

  @Test
  void noConfiguredSslContextUnirest_shouldNotTrustTigerProxy() {
    assertThatThrownBy(
            () -> {
              spawnTigerProxyWithDefaultRoutesAndWith(TigerProxyConfiguration.builder().build());

              try (var restInstanceWithoutSslContextConfigured = Unirest.spawnInstance()) {
                final SSLContext instance =
                    SSLContext.getInstance("TLSv1.2", new BouncyCastleJsseProvider());
                instance.init(null, null, null);
                restInstanceWithoutSslContextConfigured
                    .config()
                    .sslContext(instance)
                    .proxy("localhost", tigerProxy.getProxyPort());
                restInstanceWithoutSslContextConfigured.get("https://backend/foobar").asJson();
              }
            })
        .hasRootCauseInstanceOf(CertPathBuilderException.class);
  }

  @Test
  void autoconfigureSslContextOkHttp_shouldTrustTigerProxy() throws IOException {
    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());

    OkHttpClient client =
        new OkHttpClient.Builder()
            .proxy(
                new Proxy(Type.HTTP, new InetSocketAddress("localhost", tigerProxy.getProxyPort())))
            .sslSocketFactory(
                tigerProxy.getConfiguredTigerProxySslContext().getSocketFactory(),
                tigerProxy.buildTrustManagerForTigerProxy())
            .build();

    Request request = new Request.Builder().url("http://backend/foobar").build();

    try (okhttp3.Response response = client.newCall(request).execute()) {

      assertThat(response.code()).isEqualTo(666);
    }
  }

  @Test
  void autoconfigureSslContextRestAssured_shouldTrustTigerProxy() {
    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());

    RestAssured.config =
        RestAssured.config()
            .sslConfig(SSLConfig.sslConfig().trustStore(tigerProxy.buildTruststore()));

    RestAssured.proxy("localhost", tigerProxy.getProxyPort());
    Response response = RestAssured.get("https://backend/foobar").andReturn();

    assertThat(response.getStatusCode()).isEqualTo(666);
  }

  @Test
  void dynamicallyCreateCaAndEeCertificate_shouldBeValidForOneYear() throws Exception {
    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());

    AtomicInteger checkCounter = new AtomicInteger(0);
    try (final UnirestInstance unirestInstance = Unirest.spawnInstance()) {
      unirestInstance.config().verifySsl(true);
      SSLContext ctx = SSLContext.getInstance("TLSv1.2");
      ctx.init(
          null,
          new TrustManager[] {
            new X509TrustManager() {
              public void checkClientTrusted(X509Certificate[] chain, String authType) {}

              public void checkServerTrusted(X509Certificate[] chain, String authType) {
                Arrays.stream(chain)
                    .forEach(
                        cert -> {
                          try {
                            cert.checkValidity(
                                Date.from(ZonedDateTime.now().plusYears(1).toInstant()));
                            checkCounter.incrementAndGet();
                          } catch (CertificateExpiredException
                              | CertificateNotYetValidException e) {
                            throw new RuntimeException(e);
                          }
                        });
              }

              public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
              }
            }
          },
          new SecureRandom());
      unirestInstance.config().sslContext(ctx);
      unirestInstance.get("https://localhost:" + tigerProxy.getProxyPort() + "/foobar").asString();

      assertThat(checkCounter).hasValueGreaterThanOrEqualTo(1);
    }
  }

  @Test
  void changeCertificateDuringRuntime_shouldBeUsedDuringHandshake() throws UnirestException {
    final TigerProxyConfiguration cfg = new TigerProxyConfiguration();
    spawnTigerProxyWithDefaultRoutesAndWith(cfg);

    // initial get (force certificate to be used)
    proxyRest.get("https://backend/foobar").asJson();

    // change certificate
    cfg.getTls()
        .setServerRootCa(
            new TigerConfigurationPkiIdentity("src/test/resources/selfSignedCa/rootCa.p12;00"));
    tigerProxy.restartMockserver();

    var request = proxyRest.get("https://backend/foobar");
    // should use new certificate
    assertThatThrownBy(request::asJson).isInstanceOf(RuntimeException.class);
    try (var newRestClient = Unirest.spawnInstance()) {
      newRestClient
          .config()
          .proxy("localhost", tigerProxy.getProxyPort())
          .sslContext(tigerProxy.buildSslContext());

      final HttpResponse<JsonNode> response = newRestClient.get("https://backend/foobar").asJson();

      assertThat(response.getStatus()).isEqualTo(666);
      assertThat(response.getBody().getObject().get("foo")).hasToString("bar");
    }
  }

  @Test
  void restartMockServer_generatedCaShouldBeUnchanged() throws UnirestException {
    final TigerConfigurationPkiIdentity clientIdentity =
        new TigerConfigurationPkiIdentity("src/test/resources/rsa.p12;00");

    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());
    final SSLContext sslContext = tigerProxy.buildSslContext();

    proxyRest.get("http://backend/foobar").asJson();

    tigerProxy
        .getTigerProxyConfiguration()
        .setTls(TigerTlsConfiguration.builder().forwardMutualTlsIdentity(clientIdentity).build());
    tigerProxy.restartMockserver();
    try (var ownRestClient = Unirest.spawnInstance()) {
      ownRestClient.config().proxy("localhost", tigerProxy.getProxyPort()).sslContext(sslContext);

      final HttpResponse<JsonNode> response = ownRestClient.get("http://backend/foobar").asJson();
      assertThat(response.getStatus()).isEqualTo(666);
    }
  }

  @Test
  void tigerProxyAsServerWithActivatedOcspStapling_shouldSendValidOcspResponseInHandshake() {
    spawnTigerProxyAndConnectWithBouncyCastleAndCheckServerCertificate(
        serverCertificate ->
            assertThat(
                    serverCertificate
                        .getCertificateStatus()
                        .getOCSPResponse()
                        .getResponseStatus()
                        .getIntValue())
                .isZero(),
        TigerProxyConfiguration.builder()
            .tls(
                TigerTlsConfiguration.builder()
                    .ocspSignerIdentity(
                        new TigerConfigurationPkiIdentity("src/test/resources/ocspSigner.p12;00"))
                    .serverIdentity(
                        new TigerConfigurationPkiIdentity(
                            "src/test/resources/rsaStoreWithChain.jks;gematik"))
                    .build()));
  }

  @Test
  void tigerProxyAsServerWithoutActivatedOcspStapling_shouldNotSendValidOcspResponseInHandshake() {
    spawnTigerProxyAndConnectWithBouncyCastleAndCheckServerCertificate(
        serverCertificate -> assertThat(serverCertificate.getCertificateStatus()).isNull(),
        TigerProxyConfiguration.builder().tls(TigerTlsConfiguration.builder().build()));
  }

  private void spawnTigerProxyAndConnectWithBouncyCastleAndCheckServerCertificate(
      Consumer<TlsServerCertificate> serverCertificateConsumer,
      TigerProxyConfigurationBuilder proxyConfigurationBuilder) {
    AtomicInteger checkCounter = new AtomicInteger(0);

    spawnTigerProxyWith(
        proxyConfigurationBuilder
            .proxyRoutes(
                List.of(
                    TigerConfigurationRoute.builder()
                        .from("/")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());

    try (Socket socket = new Socket("127.0.0.1", tigerProxy.getProxyPort())) {
      TlsClientProtocol tlsClientProtocol =
          new TlsClientProtocol(socket.getInputStream(), socket.getOutputStream());
      tlsClientProtocol.connect(
          new DefaultTlsClient(new BcTlsCrypto()) {
            public TlsAuthentication getAuthentication() {
              return new TlsAuthentication() {
                public void notifyServerCertificate(TlsServerCertificate serverCertificate) {
                  serverCertificateConsumer.accept(serverCertificate);
                  checkCounter.incrementAndGet();
                }

                @Override
                public TlsCredentials getClientCredentials(CertificateRequest certificateRequest) {
                  return null;
                }
              };
            }
          });
      tlsClientProtocol.getOutputStream().write("GET /foobar HTTP/1.1\r\n\r\n".getBytes());

      assertThat(checkCounter)
          .withFailMessage(
              () -> "No notification of server certificate. Maybe the connection didnt take place?")
          .hasValueGreaterThanOrEqualTo(1);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @SneakyThrows
  @Test
  void masterSecretFileDefined_shouldDumpSecretInCorrectForm() throws UnirestException {
    final Path masterSecretsFile = Paths.get("target/master-secrets.txt");
    FileUtils.deleteQuietly(masterSecretsFile.toFile());

    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerConfigurationRoute.builder()
                        .from("https://blub")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .tls(
                TigerTlsConfiguration.builder()
                    .masterSecretsFile(masterSecretsFile.toString())
                    .build())
            .build());

    proxyRest.get("https://blub/foobar").asString();

    final String tls1_2sharedSecret = "(CLIENT_RANDOM [0-9a-fA-F]{64} [0-9a-fA-F]{96}\\n)*";
    // "CLIENT_RANDOM" followed by client-random followed by master secret
    // The exact length of the secret might differ in other TLS versions
    assertThat(masterSecretsFile).exists().content().matches(tls1_2sharedSecret);
  }

  @SneakyThrows
  @Test
  void mutualTlsWithTigerProxyAsServerAndBouncyCastleClientCertificate() {
    final TigerPkiIdentity clientIdentity =
        new TigerPkiIdentity("src/test/resources/brainpoolClientTls.p12;00");

    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());

    // Initialize SSLContext
    TrustManagerFactory tmf =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    tmf.init(tigerProxy.buildTruststore());
    SSLContext sslContext = SSLContext.getInstance("TLS", new BouncyCastleJsseProvider());
    TrustManager[] trustManagers = tmf.getTrustManagers();

    KeyManagerFactory keyManagerFactory =
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    keyManagerFactory.init(
        clientIdentity.toKeyStoreWithPassword("gematik"), "gematik".toCharArray());

    sslContext.init(keyManagerFactory.getKeyManagers(), trustManagers, null);
    try (final UnirestInstance mutualTlsInstance = Unirest.spawnInstance()) {
      mutualTlsInstance.config().sslContext(sslContext);

      assertThatNoException()
          .isThrownBy(
              () ->
                  mutualTlsInstance
                      .get("https://localhost:" + tigerProxy.getProxyPort() + "/foobar")
                      .asString());
    }
  }

  @Test
  void reverseProxyWithMultipleServers_shouldSelectCorrectCertificate() throws UnirestException {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .tls(
                TigerTlsConfiguration.builder()
                    .serverRootCa(
                        new TigerConfigurationPkiIdentity(
                            "src/test/resources/selfSignedCa/rootCa.p12;00"))
                    .serverIdentities(
                        List.of(
                            new TigerConfigurationPkiIdentity("src/test/resources/rsa.p12;00"),
                            new TigerConfigurationPkiIdentity(
                                "src/test/resources/eccServerCertificate.p12;00")))
                    .allowGenericFallbackIdentity(true)
                    .build())
            .proxyRoutes(
                List.of(
                    TigerConfigurationRoute.builder()
                        .from("/")
                        .to("http://localhost:" + fakeBackendServerPort + "/foobar")
                        .hosts(List.of("kon-instanz2.titus.ti-dienste.de"))
                        .build(),
                    TigerConfigurationRoute.builder()
                        .from("/")
                        .to("http://localhost:" + fakeBackendServerPort + "/deep/foobar")
                        .hosts(List.of("CGMAG-IM-FDSIM.ts-ttcn3.sig-test.telematik-test"))
                        .build(),
                    TigerConfigurationRoute.builder()
                        .from("/")
                        .to("http://localhost:" + fakeBackendServerPort + "/foobar")
                        .hosts(List.of("some.other.server"))
                        .build()))
            .build());

    executeRequestToPathWhileOnlyTrusting(
        "kon-instanz2.titus.ti-dienste.de", "src/test/resources/rsa.p12;00");
    executeRequestToPathWhileOnlyTrusting(
        "CGMAG-IM-FDSIM.ts-ttcn3.sig-test.telematik-test",
        "src/test/resources/eccServerCertificate.p12;00");
    executeRequestToPathWhileOnlyTrusting(
        "cgmag-im-fdsim.ts-ttcn3.SIG-TEST.TELEMATIK-TEST",
        "src/test/resources/eccServerCertificate.p12;00");
    executeRequestToPathWhileOnlyTrusting(
        "some.other.server", "src/test/resources/selfSignedCa/rootCa.p12;00");

    assertThatThrownBy(
            () ->
                executeRequestToPathWhileOnlyTrusting(
                    "CGMAG-IM-FDSIM.ts-ttcn3.sig-test.telematik-test",
                    "src/test/resources/rsa.p12;00"))
        .isInstanceOf(TlsFatalAlert.class)
        .hasRootCauseInstanceOf(CertPathBuilderException.class);
  }

  @Test
  void reverseProxyWithMultipleServersAlternativeNames_shouldSelectCorrectCertificate()
      throws UnirestException {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .tls(
                TigerTlsConfiguration.builder()
                    .serverIdentities(
                        List.of(
                            new TigerConfigurationPkiIdentity(
                                "src/test/resources/epa-as-1.dev.epa4all.de_NIST_X509.p12"),
                            new TigerConfigurationPkiIdentity(
                                "src/test/resources/epa-as-2.dev.epa4all.de_NIST_X509.p12"),
                            new TigerConfigurationPkiIdentity(
                                "src/test/resources/erp-dev.app.ti-dienste.de_NIST_X509.p12"),
                            new TigerConfigurationPkiIdentity(
                                "src/test/resources/erp-ref.app.ti-dienste.de_NIST_X509.p12"),
                            new TigerConfigurationPkiIdentity(
                                "src/test/resources/idp-ref.zentral.idp.splitdns.ti-dienste.de_NIST_X509.p12"),
                            new TigerConfigurationPkiIdentity(
                                "src/test/resources/localhostIdentity.p12")))
                    .allowGenericFallbackIdentity(true)
                    .build())
            .proxyRoutes(
                List.of(
                    TigerConfigurationRoute.builder()
                        .from("/")
                        .to("http://localhost:" + fakeBackendServerPort + "/foobar")
                        .hosts(List.of("epa-as-1.dev.epa4all.de"))
                        .build(),
                    TigerConfigurationRoute.builder()
                        .from("/")
                        .to("http://localhost:" + fakeBackendServerPort + "/deep/foobar")
                        .hosts(List.of("epa-as-2.dev.epa4all.de"))
                        .build(),
                    TigerConfigurationRoute.builder()
                        .from("/")
                        .to("http://localhost:" + fakeBackendServerPort + "/vauServer")
                        .hosts(
                            List.of(
                                "erp-ref.zentral.erp.splitdns.ti-dienste.de",
                                "erp-ref.app.ti-dienste.de",
                                "subscription-ref.zentral.erp.splitdns.ti-dienste.de"))
                        .build()))
            .build());

    executeRequestToPathWhileOnlyTrusting(
        "erp-ref.zentral.erp.splitdns.ti-dienste.de",
        "src/test/resources/erp-ref.app.ti-dienste.de_NIST_X509.p12;00");
  }

  @SneakyThrows
  @Test
  void onlyOneServerIdentityWithMismatchedFqdn_shouldStillBeUsedIfConfigured()
      throws UnirestException {
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
            .tls(
                TigerTlsConfiguration.builder()
                    .serverIdentity(
                        new TigerConfigurationPkiIdentity(
                            "src/test/resources/rsaStoreWithChain.jks;gematik"))
                    .build())
            .build());

    assertThatNoException()
        .isThrownBy(
            () ->
                executeRequestToPathWhileOnlyTrusting(
                    "www.schmoobar.com", "src/test/resources/rsaStoreWithChain.jks;gematik"));
  }

  @SneakyThrows
  @Test
  void onlyOneServerIdentityButDynamicFallback_fallbackShouldBeUsed() throws UnirestException {
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
            .tls(
                TigerTlsConfiguration.builder()
                    .serverIdentity(
                        new TigerConfigurationPkiIdentity(
                            "src/test/resources/rsaStoreWithChain.jks;gematik"))
                    .serverRootCa(
                        new TigerConfigurationPkiIdentity(
                            "src/test/resources/selfSignedCa/rootCa.p12;00"))
                    .build())
            .build());

    assertThatNoException()
        .isThrownBy(
            () ->
                executeRequestToPathWhileOnlyTrusting(
                    "www.schmoobar.com", "src/test/resources/selfSignedCa/rootCa.p12;00"));
  }

  @SneakyThrows
  private void executeRequestToPathWhileOnlyTrusting(String host, String fileLoadingInformation) {
    SSLContext sslContext =
        buildSslContextTrustingOnly(new TigerConfigurationPkiIdentity(fileLoadingInformation));

    try (var apacheClient =
        HttpClients.custom()
            .setSSLHostnameVerifier((hostname, session) -> true)
            .setSSLContext(sslContext)
            .build()) {
      apacheClient.execute(
          new HttpHost(
              InetAddress.getByName("127.0.0.1"), host, tigerProxy.getProxyPort(), "https"),
          RequestBuilder.get().addHeader("host", host).build());
    }
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
    TrustManagerFactory tmf =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());

    tmf.init(ks);

    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(null, tmf.getTrustManagers(), null);

    return sslContext;
  }
}
