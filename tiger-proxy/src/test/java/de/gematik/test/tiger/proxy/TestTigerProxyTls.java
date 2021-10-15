/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import de.gematik.rbellogger.util.CryptoLoader;
import de.gematik.rbellogger.util.RbelPkiIdentity;
import de.gematik.test.tiger.common.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.common.config.tigerProxy.TigerTlsConfiguration;
import de.gematik.test.tiger.common.pki.TigerPkiIdentity;
import kong.unirest.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.junit.Test;

import javax.net.ssl.*;
import java.io.File;
import java.io.IOException;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@Slf4j
public class TestTigerProxyTls extends AbstractTigerProxyTest {

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
    public void useTslBetweenClientAndProxy_shouldForward() throws UnirestException {
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
    public void serverCertificateChainShouldContainMultipleCertificatesIfGiven() throws UnirestException {
        Security.insertProviderAt(new BouncyCastleJsseProvider(), 1);
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("https://authn.aktor.epa.telematik-test")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .tls(TigerTlsConfiguration.builder()
                .serverIdentity(new TigerPkiIdentity("src/test/resources/rsaStoreWithChain.jks;gematik"))
                .build())
            .build());

        AtomicInteger callCounter = new AtomicInteger(0);

        final UnirestInstance unirestInstance = Unirest.spawnInstance();
        unirestInstance.config().verifySsl(true);
        unirestInstance.config().proxy("localhost", tigerProxy.getPort());
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
    public void rsaCaFileInP12File_shouldVerifyConnection() throws UnirestException {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("https://backend")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .tls(TigerTlsConfiguration.builder()
                .serverRootCa(new TigerPkiIdentity("src/test/resources/selfSignedCa/rootCa.p12;00"))
                .build())
            .build());

        final HttpResponse<JsonNode> response = proxyRest.get("https://backend/foobar")
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

        final HttpResponse<JsonNode> response = Unirest.get("https://backend/foobar")
            .asJson();

        assertThat(response.getStatus()).isEqualTo(666);
        assertThat(response.getBody().getObject().get("foo").toString()).isEqualTo("bar");
    }

    @Test
    public void useTslBetweenProxyAndServer_shouldForward() throws UnirestException {
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
    public void blanketRerverseProxy_shouldForwardHttpsRequest() {
        AtomicInteger callCounter = new AtomicInteger(0);

        spawnTigerProxyWith(TigerProxyConfiguration.builder()
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

        proxyRest.get("https://localhost:" + tigerProxy.getPort() + "/foobar").asString();

        assertThat(callCounter.get()).isEqualTo(2);
    }

    @Test
    public void forwardMutualTlsAndTerminatingTls_shouldUseCorrectTerminatingCa() throws UnirestException, IOException {
        final TigerPkiIdentity ca = new TigerPkiIdentity(
            "src/test/resources/selfSignedCa/rootCa.p12;00");

        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("https://backend")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .tls(TigerTlsConfiguration.builder()
                .serverRootCa(ca)
                .forwardMutualTlsIdentity(new TigerPkiIdentity("src/test/resources/rsa.p12;00"))
                .build())
            .build());

        assertThat(proxyRest.get("https://backend/foobar").asString()
            .getStatus())
            .isEqualTo(666);
    }

    @Test
    public void perRouteCertificate_shouldBePresentedOnlyForThisRoute() throws UnirestException {
        final TigerPkiIdentity serverIdentity
            = new TigerPkiIdentity("src/test/resources/rsaStoreWithChain.jks;gematik");

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
}
