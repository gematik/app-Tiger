/*
 * Copyright (c) 2022 gematik GmbH
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.util.CryptoLoader;
import de.gematik.rbellogger.util.RbelPkiIdentity;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerTlsConfiguration;
import de.gematik.test.tiger.common.pki.TigerConfigurationPkiIdentity;
import de.gematik.test.tiger.common.pki.TigerPkiIdentity;
import io.restassured.RestAssured;
import io.restassured.config.SSLConfig;
import io.restassured.response.Response;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.List;
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
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@Slf4j
@TestInstance(Lifecycle.PER_CLASS)
class TestTigerProxyTls extends AbstractTigerProxyTest {

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
        try (final UnirestInstance unirestInstance = new UnirestInstance(new Config()
            .sslContext(ctx))) {
            unirestInstance
                .get("https://localhost:" + tigerProxy.getProxyPort() + "/foobar").asString();
            assertThat(verifyWasCalledSuccesfully).isTrue();
        }
    }

    @Test
    void useTslBetweenClientAndProxy_shouldForward() throws UnirestException {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("https://backend")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        final HttpResponse<JsonNode> response = proxyRest.get("https://backend/foobar")
            .asJson();

        assertThat(response.getStatus()).isEqualTo(666);
        assertThat(response.getBody().getObject().get("foo").toString()).isEqualTo("bar");
    }

    @SneakyThrows
    @Test
    void serverCertificateChainShouldContainMultipleCertificatesIfGiven() throws UnirestException {
        Security.insertProviderAt(new BouncyCastleJsseProvider(), 1);
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("https://authn.aktor.epa.telematik-test")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .tls(TigerTlsConfiguration.builder()
                .serverIdentity(new TigerConfigurationPkiIdentity("src/test/resources/rsaStoreWithChain.jks;gematik"))
                .build())
            .build());

        AtomicInteger callCounter = new AtomicInteger(0);

        final UnirestInstance unirestInstance = Unirest.spawnInstance();
        unirestInstance.config().verifySsl(true);
        unirestInstance.config().proxy("localhost", tigerProxy.getProxyPort());
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
        unirestInstance.config().sslContext(ctx);

        unirestInstance.get("https://authn.aktor.epa.telematik-test/foobar").asString();

        await().atMost(2, TimeUnit.SECONDS)
            .until(() -> callCounter.get() > 0);
    }

    @Test
    void rsaCaFileInP12File_shouldVerifyConnection() throws UnirestException {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("https://backend")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .tls(TigerTlsConfiguration.builder()
                .serverRootCa(new TigerConfigurationPkiIdentity("src/test/resources/selfSignedCa/rootCa.p12;00"))
                .build())
            .build());

        final HttpResponse<JsonNode> response = proxyRest.get("https://backend/foobar")
            .asJson();

        assertThat(response.getStatus()).isEqualTo(666);
        assertThat(response.getBody().getObject().get("foo").toString()).isEqualTo("bar");
    }

    @Test
    void defunctCertificate_expectException() throws UnirestException {
        assertThatThrownBy(() -> new TigerProxy(TigerProxyConfiguration.builder()
            .tls(TigerTlsConfiguration.builder()
                .serverRootCa(
                    new TigerConfigurationPkiIdentity("src/test/resources/selfSignedCa/rootCa.p12;wrongPassword"))
                .build())
            .build()))
            .isInstanceOf(RuntimeException.class);
    }

    // TODO TGR-263 really fix this and reactivate @Test, Julian knows more
    public void customEccCaFileInTruststore_shouldVerifyConnection() throws UnirestException, IOException {
        final RbelPkiIdentity ca = CryptoLoader.getIdentityFromP12(
            FileUtils.readFileToByteArray(new File("src/test/resources/customCa.p12")), "00");

        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("https://backend")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .tls(TigerTlsConfiguration.builder()
                .serverRootCa(new TigerConfigurationPkiIdentity("src/test/resources/customCa.p12;00"))
                .build())
            .build());

        try (UnirestInstance unirestInstance = Unirest.spawnInstance()) {
            unirestInstance.config().proxy("localhost", tigerProxy.getProxyPort());
            unirestInstance.config().verifySsl(true);
            unirestInstance.config().sslContext(tigerProxy.buildSslContext());

            final HttpResponse<JsonNode> response = unirestInstance.get("https://backend/foobar")
                .asJson();

            assertThat(response.getStatus()).isEqualTo(666);
            assertThat(response.getBody().getObject().get("foo").toString()).isEqualTo("bar");
        }
    }

    @Test
    void useTslBetweenProxyAndServer_shouldForward() throws UnirestException {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("https://localhost:" + fakeBackendServer.httpsPort())
                .build()))
            .build());

        final HttpResponse<JsonNode> response = proxyRest.get("http://backend/foobar")
            .asJson();

        assertThat(response.getStatus()).isEqualTo(666);
        assertThat(response.getBody().getObject().get("foo").toString()).isEqualTo("bar");
    }

    @Test
    void blanketReverseProxy_shouldForwardHttpsRequest() {
        AtomicInteger callCounter = new AtomicInteger(0);

        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .tls(TigerTlsConfiguration.builder()
                .serverRootCa(new TigerConfigurationPkiIdentity(
                    "src/test/resources/selfSignedCa/rootCa.p12;00"))
                .build())
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        tigerProxy.addRbelMessageListener(message -> callCounter.incrementAndGet());

        proxyRest.get("https://localhost:" + tigerProxy.getProxyPort() + "/foobar").asString();
        awaitMessagesInTiger(2);

        assertThat(callCounter.get()).isEqualTo(2);
    }

    @Test
    void forwardMutualTlsAndTerminatingTls_shouldUseCorrectTerminatingCa() throws UnirestException {
        final TigerConfigurationPkiIdentity clientIdentity = new TigerConfigurationPkiIdentity(
            "src/test/resources/rsaStoreWithChain.jks;gematik");

        TigerProxy secondProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("https://localhost:" + secondProxy.getProxyPort())
                .build()))
            .tls(TigerTlsConfiguration.builder()
                .forwardMutualTlsIdentity(clientIdentity)
                .build())
            .build());

        final HttpResponse<String> response = proxyRest.get("http://backend/foobar").asString();
        awaitMessagesInTiger(2);

        assertThat(response.getStatus())
            .isEqualTo(666);

        assertThat(secondProxy.getRbelMessagesList().get(0)
            .findElement("$.clientTlsCertificateChain.0.subject")
            .map(RbelElement::getRawStringContent))
            .get()
            .usingComparator((s1, s2) -> splitDn(s1).containsAll(splitDn(s2)) ? 0 : 1)
            .isEqualTo(clientIdentity.getCertificate().getSubjectDN().getName());
    }

    @NotNull
    private static List<String> splitDn(String s1) {
        return Stream.of(s1.split(","))
            .map(String::trim)
            .collect(Collectors.toList());
    }

    @ParameterizedTest
    @CsvSource({
        "'TLSv1,TLSv1.2', 'TLSv1.2', true, TLSv1.2",
        "'TLSv1,TLSv1.2,TLSv1.3', 'TLSv1.2', true, TLSv1.2",
        "'TLSv1', 'TLSv1.2', false, TLSv1",
        "'TLSv1.3', 'TLSv1.3', true, TLSv1.3"
    })
    void serverSslVersion_shouldBeHonored(String clientTlsSuites, String serverTlsSuites,
        boolean shouldConnect, String assertSuiteUsed) {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("https://localhost:" + fakeBackendServer.httpsPort())
                .build()))
            .tls(TigerTlsConfiguration.builder()
                .serverTlsProtocols(Stream.of(serverTlsSuites.split(","))
                    .collect(Collectors.toList()))
                .build())
            .build());

        try (UnirestInstance unirestInstance = Unirest.spawnInstance()) {
            SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(
                tigerProxy.getConfiguredTigerProxySslContext(),
                clientTlsSuites.split(","),
                null,
                (hostname, session) -> {
                    assertThat(session.getProtocol())
                        .isEqualTo(assertSuiteUsed);
                    return true;
                });
            var httpClient = HttpClients.custom()
                .setSSLSocketFactory(sslSocketFactory)
                .build();
            unirestInstance.config().httpClient(ApacheClient.builder(httpClient));
            final GetRequest request = unirestInstance.get(
                "https://localhost:" + tigerProxy.getProxyPort() + "/foobar");
            if (shouldConnect) {
                request.asString();
            } else {
                assertThatThrownBy(request::asString)
                    .hasCauseInstanceOf(SSLHandshakeException.class);
            }
        }
    }

    @Test
    void extractSubjectDnFromClientCertificate_saveInTigerProxy() throws Exception {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("https://localhost:" + fakeBackendServer.httpsPort())
                .build()))
            .build());

        try (UnirestInstance unirestInstance = Unirest.spawnInstance()) {
            unirestInstance.config().httpClient(loadSslContextForClientCert());
            unirestInstance.get("https://localhost:" + tigerProxy.getProxyPort() + "/foobar").asString();
        }
        awaitMessagesInTiger(2);

        assertThat(tigerProxy.getRbelMessagesList().get(0).findElement("$.clientTlsCertificateChain.0.subject")
            .get().getRawStringContent())
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

        final SSLContext sslContext = SSLContexts.custom()
            .loadTrustMaterial(trustStore, new TrustAllStrategy())
            .loadKeyMaterial(trustStore, "00".toCharArray(), (aliases, socket) -> "alias")
            .build();

        return HttpClients.custom().setSSLContext(sslContext).build();
    }

    @Test
    void perRouteCertificate_shouldBePresentedOnlyForThisRoute() throws UnirestException {
        final TigerConfigurationPkiIdentity serverIdentity
            = new TigerConfigurationPkiIdentity("src/test/resources/rsaStoreWithChain.jks;gematik");

        spawnTigerProxyWith(TigerProxyConfiguration.builder()
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
            new Config().proxy("localhost", tigerProxy.getProxyPort())
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

    @Test
    void configureServerTslSuites() {
        final String configuredSslSuite = "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA";
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .tls(TigerTlsConfiguration.builder()
                .serverSslSuites(List.of(configuredSslSuite))
                .build())
            .build());

        SSLContext ctx = tigerProxy.buildSslContext();
        new UnirestInstance(new Config()
            .sslContext(ctx)
            .proxy("localhost", tigerProxy.getProxyPort()))
            .get("https://localhost:" + fakeBackendServer.port() + "/foobar").asString();

        assertThat(ctx.getClientSessionContext()
            .getSession(ctx.getClientSessionContext().getIds().nextElement())
            .getCipherSuite())
            .isEqualTo(configuredSslSuite);
    }

    @Test
    void autoconfigureSslContextUnirest_shouldTrustTigerProxy() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("https://backend")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        var restInstanceWithSslContextConfigured = Unirest.spawnInstance();
        restInstanceWithSslContextConfigured.config().proxy("localhost", tigerProxy.getProxyPort());
        restInstanceWithSslContextConfigured.config().sslContext(tigerProxy.buildSslContext());

        final HttpResponse<JsonNode> response = restInstanceWithSslContextConfigured.get("https://backend/foobar")
            .asJson();

        assertThat(response.getStatus()).isEqualTo(666);
        assertThat(response.getBody().getObject().get("foo").toString()).isEqualTo("bar");
    }

    @Test
    void noConfiguredSslContextUnirest_shouldNotTrustTigerProxy() {
        assertThatThrownBy(() -> {
            spawnTigerProxyWith(TigerProxyConfiguration.builder()
                .proxyRoutes(List.of(TigerRoute.builder()
                    .from("https://backend")
                    .to("http://localhost:" + fakeBackendServer.port())
                    .build()))
                .build());

            var restInstanceWithoutSslContextConfigured = Unirest.spawnInstance();
            restInstanceWithoutSslContextConfigured.config().proxy("localhost", tigerProxy.getProxyPort());
            restInstanceWithoutSslContextConfigured.get("https://backend/foobar")
                .asJson();

        }).hasMessageContaining(
            "PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target");
    }

    @Test
    void autoconfigureSslContextOkHttp_shouldTrustTigerProxy() throws IOException {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("https://backend")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        OkHttpClient client = new OkHttpClient.Builder()
            .proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost", tigerProxy.getProxyPort())))
            .sslSocketFactory(tigerProxy.getConfiguredTigerProxySslContext().getSocketFactory(),
                tigerProxy.buildTrustManagerForTigerProxy())
            .build();

        Request request = new Request.Builder()
            .url("https://backend/foobar")
            .build();

        okhttp3.Response response = client.newCall(request).execute();

        assertThat(response.code()).isEqualTo(666);
    }

    @Test
    void noConfiguredSslContextOKHttp_shouldNotTrustTigerProxy() {
        assertThatThrownBy(() -> {
            spawnTigerProxyWith(TigerProxyConfiguration.builder()
                .proxyRoutes(List.of(TigerRoute.builder()
                    .from("https://backend")
                    .to("http://localhost:" + fakeBackendServer.port())
                    .build()))
                .build());

            OkHttpClient client = new OkHttpClient.Builder()
                .proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost", tigerProxy.getProxyPort())))
                .build();

            Request request = new Request.Builder()
                .url("https://backend/foobar")
                .build();

            client.newCall(request).execute();

        }).hasMessageContaining(
            "PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target");
    }

    @Test
    void autoconfigureSslContextRestAssured_shouldTrustTigerProxy() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("https://backend")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        RestAssured.config = RestAssured.config().sslConfig(SSLConfig.sslConfig()
            .trustStore(tigerProxy.buildTruststore()));

        RestAssured.proxy("localhost", tigerProxy.getProxyPort());
        Response response = RestAssured.get(
                "https://backend/foobar").
            andReturn();

        assertThat(response.getStatusCode()).isEqualTo(666);
    }

    @Test
    void autoconfigureSslContextRestAssured_shouldNotTrustTigerProxy() {
        assertThatThrownBy(() -> {
            spawnTigerProxyWith(TigerProxyConfiguration.builder()
                .proxyRoutes(List.of(TigerRoute.builder()
                    .from("https://backend")
                    .to("http://localhost:" + fakeBackendServer.port())
                    .build()))
                .build());

            RestAssured.config = RestAssured.config().sslConfig(SSLConfig.sslConfig()
                .trustStore(null));

            RestAssured.proxy("localhost", tigerProxy.getProxyPort());
            RestAssured.get(
                    "https://backend/foobar").
                andReturn();

        }).hasMessageContaining(
            "PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target");
    }
}
