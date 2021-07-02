/*
 * Copyright (c) 2021 gematik GmbH
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

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.elements.RbelBinaryElement;
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
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        assertThat(tigerProxy.getRbelMessages().get(0).getRecipient())
                .isEqualTo(new RbelHostname("backend", 80));
        assertThat(tigerProxy.getRbelMessages().get(0).getSender())
                .isNull();
        assertThat(tigerProxy.getRbelMessages().get(1).getSender())
                .isEqualTo(new RbelHostname("backend", 80));
        assertThat(tigerProxy.getRbelMessages().get(1).getRecipient())
                .isNull();

        new RbelHtmlRenderer().doRender(tigerProxy.getRbelMessages());
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
                        .withBody(Base64.getEncoder().encode("Hallo".getBytes()))));

        Unirest.get("http://backend/binary").asBytes();

        assertThat(tigerProxy.getRbelMessages().get(tigerProxy.getRbelMessages().size() - 1)
                .findRbelPathMembers("$.body").get(0))
                .isInstanceOf(RbelBinaryElement.class);
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

        assertThat(tigerProxy.getRbelMessages().get(1).getHttpMessage()
                .getFirst("body").get()
                .getFirst("foo").get()
                .getContent()
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

    //    @Test
//      public void startProxyFor30s() {
//        TigerProxy tp = new TigerProxy(TigerProxyConfiguration.builder()
//                // .forwardToProxy(new ForwardProxyInfo("192.168.230.85", 3128))
//                .activateRbelEndpoint(true)
//                .proxyLogLevel("TRACE")
//                .port(6666)
////            .forwardToProxy(new ForwardProxyInfo("192.168.110.10", 3128))
//                .proxyRoutes(Map.of(
//                        "http://localhost:11001", "http://frontend.titus.ti-dienste.de/",
//                        "https://localhost:11001", "https://frontend.titus.ti-dienste.de/",
//                        "http://frontend.titus.ti-dienste.de/", "http://frontend.titus.ti-dienste.de/",
//                        "https://frontend.titus.ti-dienste.de/", "https://frontend.titus.ti-dienste.de/",
//                        "https://127.0.0.1:11001", "https://gateway.epa-instanz1.titus.ti-dienste.de",
//                        "https://localhost:9101", "https://gateway.epa-instanz1.titus.ti-dienste.de",
//                        "https://127.0.0.1:9101", "https://gateway.epa-instanz1.titus.ti-dienste.de",
//                        "https://localhost:9001", "https://gateway.epa-instanz1.titus.ti-dienste.de",
//                        "https://127.0.0.1:9001", "https://gateway.epa-instanz1.titus.ti-dienste.de"
////                "https://gateway.epa-instanz1.titus.ti-dienste.de", "https://gateway.epa-instanz1.titus.ti-dienste.de"
//                )).proxyLogLevel("DEBUG")
//                .serverRootCaCertPem("src/main/resources/CertificateAuthorityCertificate.pem")
//                .serverRootCaKeyPem("src/main/resources/PKCS8CertificateAuthorityPrivateKey.pem")
//                .build());
//        System.out.println(tp.getBaseUrl() + " with " + tp.getPort());
//        try {
//            Thread.sleep(30 * 1_000 * 1_000);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//    }
}
