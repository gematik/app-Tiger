/*
 * Copyright (c) 2024 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.proxy;

import static de.gematik.rbellogger.data.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import de.gematik.rbellogger.data.RbelElementAssertion;
import de.gematik.rbellogger.util.CryptoLoader;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerRoute;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerTlsConfiguration;
import de.gematik.test.tiger.common.pki.TigerConfigurationPkiIdentity;
import de.gematik.test.tiger.common.pki.TigerPkiIdentity;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import io.restassured.RestAssured;
import io.restassured.config.SSLConfig;
import io.restassured.response.Response;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.Socket;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.net.ssl.*;
import kong.unirest.*;
import kong.unirest.apache.ApacheClient;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.commons.io.FileUtils;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@Slf4j
@TestInstance(Lifecycle.PER_CLASS)
@ResetTigerConfiguration
class TestTigerProxyTls extends AbstractTigerProxyTest {

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

    AtomicBoolean verifyWasCalledSuccesfully = new AtomicBoolean(false);
    SSLContext ctx = SSLContext.getInstance("TLSv1.2");
    ctx.init(
        null,
        new TrustManager[] {
          new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType) {}

            public void checkServerTrusted(X509Certificate[] chain, String authType) {
              assertThat(chain[0].getSubjectDN().getName()).contains("muahaha");
              verifyWasCalledSuccesfully.set(true);
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
      assertThat(verifyWasCalledSuccesfully).isTrue();
    }
  }

  @Test
  void useTslBetweenClientAndProxy_shouldForward() throws UnirestException {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("https://backend")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());

    final HttpResponse<JsonNode> response = proxyRest.get("https://backend/foobar").asJson();

    assertThat(response.getStatus()).isEqualTo(666);
    assertThat(response.getBody().getObject().get("foo")).hasToString("bar");
  }

  @Test
  void forwardProxyWithoutConfiguredServerName_certificateShouldContainCorrectServerName() {
    spawnTigerProxyWith(TigerProxyConfiguration.builder().build());

    final HttpResponse<JsonNode> response = proxyRest.get("https://google.com").asJson();
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @SneakyThrows
  @Test
  void serverCertificateChainShouldContainMultipleCertificatesIfGiven() throws UnirestException {
    Security.insertProviderAt(new BouncyCastleJsseProvider(), 1);
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
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

    final UnirestInstance unirestInstance = Unirest.spawnInstance();
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

  @Test
  void rsaCaFileInP12File_shouldVerifyConnection() throws UnirestException {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("https://backend")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
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
                .proxyRoutes(
                    List.of(
                        TigerRoute.builder()
                            .from("https://backend")
                            .to("http://localhost:" + fakeBackendServerPort)
                            .build()))
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
  void useTslBetweenProxyAndServer_shouldForward() throws UnirestException {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("http://backend")
                        .to("https://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());

    final HttpResponse<JsonNode> response = proxyRest.get("http://backend/foobar").asJson();

    assertThat(response.getStatus()).isEqualTo(666);
    assertThat(response.getBody().getObject().get("foo")).hasToString("bar");
  }

  @Test
  void blanketReverseProxy_shouldForwardHttpsRequest() {
    AtomicInteger callCounter = new AtomicInteger(0);

    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .tls(
                TigerTlsConfiguration.builder()
                    .serverRootCa(
                        new TigerConfigurationPkiIdentity(
                            "src/test/resources/selfSignedCa/rootCa.p12;00"))
                    .build())
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("/")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());

    tigerProxy.addRbelMessageListener(message -> callCounter.incrementAndGet());

    proxyRest.get("https://localhost:" + tigerProxy.getProxyPort() + "/foobar").asString();
    awaitMessagesInTiger(2);

    assertThat(callCounter.get()).isEqualTo(2);
  }

  @Test
  @Disabled("Waiting for the next mockserver release (>5.15.0). See TGR-898, TGR-815")
  void forwardMutualTlsAndTerminatingTls_shouldUseCorrectTerminatingCa() throws UnirestException {
    final TigerConfigurationPkiIdentity clientIdentity =
        new TigerConfigurationPkiIdentity("src/test/resources/rsaStoreWithChain.jks;gematik");

    TigerProxy secondProxy =
        new TigerProxy(
            TigerProxyConfiguration.builder()
                .proxyRoutes(
                    List.of(
                        TigerRoute.builder()
                            .from("/")
                            .to("http://localhost:" + fakeBackendServerPort)
                            .build()))
                .name("secondProxy")
                .build());

    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("/")
                        .to("https://localhost:" + secondProxy.getProxyPort())
                        .build()))
            .tls(TigerTlsConfiguration.builder().forwardMutualTlsIdentity(clientIdentity).build())
            .build());

    final HttpResponse<String> response = proxyRest.get("http://backend/foobar").asString();
    awaitMessagesInTiger(2);

    assertThat(response.getStatus()).isEqualTo(666);

    RbelElementAssertion.assertThat(secondProxy.getRbelMessagesList().get(0))
        .extractChildWithPath("$.clientTlsCertificateChain.0.subject")
        .valueAsString()
        .get()
        .usingComparator((s1, s2) -> splitDn(s1).containsAll(splitDn(s2)) ? 0 : 1)
        .isEqualTo(clientIdentity.getCertificate().getSubjectDN().getName());
  }

  @NotNull
  private static List<String> splitDn(String s1) {
    return Stream.of(s1.split(",")).map(String::trim).collect(Collectors.toList());
  }

  @ParameterizedTest
  @CsvSource({
    "'TLSv1,TLSv1.2', 'TLSv1.2', true, TLSv1.2",
    "'TLSv1,TLSv1.2,TLSv1.3', 'TLSv1.2', true, TLSv1.2",
    "'TLSv1', 'TLSv1.2', false, TLSv1",
    "'TLSv1.3', 'TLSv1.3', true, TLSv1.3"
  })
  void serverSslVersion_shouldBeHonored(
      String clientTlsSuites,
      String serverTlsSuites,
      boolean shouldConnect,
      String assertSuiteUsed) {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("/")
                        .to("https://localhost:" + fakeBackendServerPort)
                        .build()))
            .tls(
                TigerTlsConfiguration.builder()
                    .serverTlsProtocols(
                        Stream.of(serverTlsSuites.split(",")).collect(Collectors.toList()))
                    .build())
            .build());

    try (UnirestInstance unirestInstance = Unirest.spawnInstance()) {
      SSLConnectionSocketFactory sslSocketFactory =
          new SSLConnectionSocketFactory(
              tigerProxy.getConfiguredTigerProxySslContext(),
              clientTlsSuites.split(","),
              null,
              (hostname, session) -> {
                assertThat(session.getProtocol()).isEqualTo(assertSuiteUsed);
                return true;
              });
      var httpClient = HttpClients.custom().setSSLSocketFactory(sslSocketFactory).build();
      unirestInstance.config().httpClient(ApacheClient.builder(httpClient));
      final GetRequest request =
          unirestInstance.get("https://localhost:" + tigerProxy.getProxyPort() + "/foobar");
      if (shouldConnect) {
        request.asString();
      } else {
        assertThatThrownBy(request::asString).hasCauseInstanceOf(IllegalStateException.class);
      }
    }
  }

  @Test
  @Disabled("Waiting for the next mockserver release (>5.15.0)")
  void extractSubjectDnFromClientCertificate_saveInTigerProxy() throws Exception {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("/")
                        .to("https://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());

    try (UnirestInstance unirestInstance = Unirest.spawnInstance()) {
      unirestInstance.config().httpClient(loadSslContextForClientCert());
      unirestInstance.get("https://localhost:" + tigerProxy.getProxyPort() + "/foobar").asString();
    }
    awaitMessagesInTiger(2);

    assertThat(tigerProxy.getRbelMessagesList().get(0))
        .extractChildWithPath("$.clientTlsCertificateChain.0.subject")
        .valueAsString()
        .get(InstanceOfAssertFactories.STRING)
        .contains("CN=mailuser-rsa1");
  }

  private CloseableHttpClient loadSslContextForClientCert() throws Exception {
    KeyStore trustStore = KeyStore.getInstance("PKCS12");

    FileInputStream instream = new FileInputStream("src/test/resources/mailuser-rsa1.p12");
    try {
      trustStore.load(instream, "00".toCharArray());
    } finally {
      instream.close();
    }

    final SSLContext sslContext =
        SSLContexts.custom()
            .loadTrustMaterial(trustStore, new TrustAllStrategy())
            .loadKeyMaterial(trustStore, "00".toCharArray(), (aliases, socket) -> "alias")
            .build();

    return HttpClients.custom().setSSLContext(sslContext).build();
  }

  @Test
  void perRouteCertificate_shouldBePresentedOnlyForThisRoute() throws UnirestException {
    final TigerConfigurationPkiIdentity serverIdentity =
        new TigerConfigurationPkiIdentity("src/test/resources/rsaStoreWithChain.jks;gematik");

    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .tls(TigerTlsConfiguration.builder().serverIdentity(serverIdentity).build())
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        // aktor-gateway.gematik.de ist der DN des obigen zertifikats
                        .from("https://authn.aktor.epa.telematik-test")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build(),
                    TigerRoute.builder()
                        .from("https://falsche-url")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());

    final UnirestInstance unirestInstance =
        new UnirestInstance(
            new Config()
                .proxy("localhost", tigerProxy.getProxyPort())
                .verifySsl(true)
                .sslContext(buildSslContextTrustingOnly(serverIdentity)));

    assertThat(
            unirestInstance
                .get("https://authn.aktor.epa.telematik-test/foobar")
                .asString()
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
    TrustManagerFactory tmf =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());

    tmf.init(ks);

    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(null, tmf.getTrustManagers(), null);

    return sslContext;
  }

  @Test
  void configureServerTslSuites() {
    final String configuredSslSuite = "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA";
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .tls(
                TigerTlsConfiguration.builder()
                    .serverSslSuites(List.of(configuredSslSuite))
                    .build())
            .build());

    SSLContext ctx = tigerProxy.buildSslContext();
    new UnirestInstance(new Config().sslContext(ctx).proxy("localhost", tigerProxy.getProxyPort()))
        .get("https://localhost:" + fakeBackendServerPort + "/foobar")
        .asString();

    assertThat(
            ctx.getClientSessionContext()
                .getSession(ctx.getClientSessionContext().getIds().nextElement())
                .getCipherSuite())
        .isEqualTo(configuredSslSuite);
  }

  @Test
  void mutualTlsWithEcc_konnektorGradeHandshake() {
    final TigerConfigurationPkiIdentity clientIdentity =
        new TigerConfigurationPkiIdentity("src/test/resources/gateway_ecc.p12;00");

    int serverPort = startKonnektorAlikeServerReturningAlways555(clientIdentity);

    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
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
                        List.of("brainpoolP256r1", "brainpoolP384r1", "prime256v1", "secp384r1"))
                    .build())
            .build());

    final HttpResponse<String> response = proxyRest.get("http://backend/foobar").asString();

    assertThat(response.getStatus()).isEqualTo(555);
  }

  AtomicBoolean shouldServerRun = new AtomicBoolean(true);

  @SneakyThrows
  public int startKonnektorAlikeServerReturningAlways555(
      TigerConfigurationPkiIdentity clientIdentity) {
    var threadPool = Executors.newCachedThreadPool();

    System.setProperty("jdk.tls.namedGroups", "brainpoolP384r1");
    Security.setProperty("ssl.KeyManagerFactory.algorithm", "PKIX");
    Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
    Security.insertProviderAt(new BouncyCastleProvider(), 1);
    Security.removeProvider(BouncyCastleJsseProvider.PROVIDER_NAME);
    Security.insertProviderAt(new BouncyCastleJsseProvider(), 2);
    SSLContext sslContext = getSSLContext(clientIdentity);
    SSLServerSocketFactory ssf = sslContext.getServerSocketFactory();
    SSLServerSocket serverSocket = (SSLServerSocket) ssf.createServerSocket(0);
    String[] ciphers = {"TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256"};
    serverSocket.setEnabledCipherSuites(ciphers);
    serverSocket.setEnabledProtocols(new String[] {"TLSv1.2"});
    serverSocket.setNeedClientAuth(true);

    System.clearProperty("jdk.tls.namedGroups");

    threadPool.execute(
        () -> {
          while (shouldServerRun.get()) {
            try {
              Socket socket = serverSocket.accept();
              OutputStream out = socket.getOutputStream();
              out.write("HTTP/1.1 555\r\n".getBytes());
              out.close();
              socket.close();
            } catch (IOException e) {
              // swallow
            }
          }
        });

    return serverSocket.getLocalPort();
  }

  protected SSLContext getSSLContext(TigerConfigurationPkiIdentity clientIdentity)
      throws Exception {
    SSLContext sslContext = SSLContext.getInstance("TLS");
    final TigerConfigurationPkiIdentity serverCert =
        new TigerConfigurationPkiIdentity("src/test/resources/eccStoreWithChain.jks;gematik");
    // Set up key manager factory to use our key store
    KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    kmf.init(serverCert.toKeyStoreWithPassword("00"), "00".toCharArray());

    // Initialize the SSLContext to work with our key managers.
    final X509TrustManager x509TrustManager =
        new X509TrustManager() {
          @Override
          public void checkClientTrusted(X509Certificate[] chain, String authType)
              throws CertificateException {
            // swallow
          }

          @Override
          public void checkServerTrusted(X509Certificate[] chain, String authType)
              throws CertificateException {
            // swallow
          }

          @Override
          public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[] {clientIdentity.getCertificate()};
          }
        };
    sslContext.init(kmf.getKeyManagers(), new X509TrustManager[] {x509TrustManager}, null);

    return sslContext;
  }

  @Test
  void autoconfigureSslContextUnirest_shouldTrustTigerProxy() {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("https://backend")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());

    var restInstanceWithSslContextConfigured = Unirest.spawnInstance();
    restInstanceWithSslContextConfigured.config().proxy("localhost", tigerProxy.getProxyPort());
    restInstanceWithSslContextConfigured.config().sslContext(tigerProxy.buildSslContext());

    final HttpResponse<JsonNode> response =
        restInstanceWithSslContextConfigured.get("https://backend/foobar").asJson();

    assertThat(response.getStatus()).isEqualTo(666);
    assertThat(response.getBody().getObject().get("foo")).hasToString("bar");
  }

  @Test
  void noConfiguredSslContextUnirest_shouldNotTrustTigerProxy() {
    assertThatThrownBy(
            () -> {
              spawnTigerProxyWith(
                  TigerProxyConfiguration.builder()
                      .proxyRoutes(
                          List.of(
                              TigerRoute.builder()
                                  .from("https://backend")
                                  .to("http://localhost:" + fakeBackendServerPort)
                                  .build()))
                      .build());

              var restInstanceWithoutSslContextConfigured = Unirest.spawnInstance();
              restInstanceWithoutSslContextConfigured
                  .config()
                  .proxy("localhost", tigerProxy.getProxyPort());
              restInstanceWithoutSslContextConfigured.get("https://backend/foobar").asJson();
            })
        .hasMessageContaining("certificate_unknown");
  }

  @Test
  void autoconfigureSslContextOkHttp_shouldTrustTigerProxy() throws IOException {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("http://backend")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());

    OkHttpClient client =
        new OkHttpClient.Builder()
            .proxy(
                new Proxy(Type.HTTP, new InetSocketAddress("localhost", tigerProxy.getProxyPort())))
            .sslSocketFactory(
                tigerProxy.getConfiguredTigerProxySslContext().getSocketFactory(),
                tigerProxy.buildTrustManagerForTigerProxy())
            .build();

    Request request = new Request.Builder().url("http://backend/foobar").build();

    okhttp3.Response response = client.newCall(request).execute();

    assertThat(response.code()).isEqualTo(666);
  }

  @Test
  void noConfiguredSslContextOKHttp_shouldNotTrustTigerProxy() {
    assertThatThrownBy(
            () -> {
              spawnTigerProxyWith(
                  TigerProxyConfiguration.builder()
                      .proxyRoutes(
                          List.of(
                              TigerRoute.builder()
                                  .from("https://backend")
                                  .to("http://localhost:" + fakeBackendServerPort)
                                  .build()))
                      .build());

              OkHttpClient client =
                  new OkHttpClient.Builder()
                      .proxy(
                          new Proxy(
                              Proxy.Type.HTTP,
                              new InetSocketAddress("localhost", tigerProxy.getProxyPort())))
                      .build();

              Request request = new Request.Builder().url("https://backend/foobar").build();

              client.newCall(request).execute();
            })
        .hasMessageContaining("certificate_unknown");
  }

  @Test
  void autoconfigureSslContextRestAssured_shouldTrustTigerProxy() {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("https://backend")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());

    RestAssured.config =
        RestAssured.config()
            .sslConfig(SSLConfig.sslConfig().trustStore(tigerProxy.buildTruststore()));

    RestAssured.proxy("localhost", tigerProxy.getProxyPort());
    Response response = RestAssured.get("https://backend/foobar").andReturn();

    assertThat(response.getStatusCode()).isEqualTo(666);
  }

  @Test
  void autoconfigureSslContextRestAssured_shouldNotTrustTigerProxy() {
    assertThatThrownBy(
            () -> {
              spawnTigerProxyWith(
                  TigerProxyConfiguration.builder()
                      .proxyRoutes(
                          List.of(
                              TigerRoute.builder()
                                  .from("https://backend")
                                  .to("http://localhost:" + fakeBackendServerPort)
                                  .build()))
                      .build());

              RestAssured.config =
                  RestAssured.config().sslConfig(SSLConfig.sslConfig().trustStore(null));

              RestAssured.proxy("localhost", tigerProxy.getProxyPort());
              RestAssured.get("https://backend/foobar").andReturn();
            })
        .hasMessageContaining("certificate_unknown");
  }

  @Test
  void dynamicallyCreateCaAndEeCertificate_shouldBeValidForOneYear() throws Exception {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("/")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());

    AtomicInteger checkCounter = new AtomicInteger(0);
    final UnirestInstance unirestInstance = Unirest.spawnInstance();
    unirestInstance.config().verifySsl(true);
    unirestInstance.config().proxy("localhost", tigerProxy.getProxyPort());
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
                        } catch (CertificateExpiredException | CertificateNotYetValidException e) {
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

  @Test
  void changeCertificateDuringRuntime_shouldBeUsedDuringHandshake() throws UnirestException {
    final TigerProxyConfiguration cfg =
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("https://backend")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .build();
    spawnTigerProxyWith(cfg);

    // initial get (force certificate to be used)
    proxyRest.get("https://backend/foobar").asJson();

    // change certificate
    cfg.getTls()
        .setServerRootCa(
            new TigerConfigurationPkiIdentity("src/test/resources/selfSignedCa/rootCa.p12;00"));
    tigerProxy.restartMockserver();

    var request = proxyRest.get("https://backend/foobar");
    // should use new certificate
    assertThatThrownBy(() -> request.asJson()).isInstanceOf(RuntimeException.class);
    var newRestClient = Unirest.spawnInstance();
    newRestClient
        .config()
        .proxy("localhost", tigerProxy.getProxyPort())
        .sslContext(tigerProxy.buildSslContext());

    final HttpResponse<JsonNode> response = newRestClient.get("https://backend/foobar").asJson();

    assertThat(response.getStatus()).isEqualTo(666);
    assertThat(response.getBody().getObject().get("foo")).hasToString("bar");
  }
}
