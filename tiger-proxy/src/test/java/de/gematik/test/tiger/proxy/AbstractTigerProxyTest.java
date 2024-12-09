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

import static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder.responseDefinition;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.fail;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import de.gematik.rbellogger.data.facet.RbelParsingNotCompleteFacet;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerConfigurationRoute;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerTlsConfiguration;
import de.gematik.test.tiger.common.pki.TigerConfigurationPkiIdentity;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.*;
import kong.unirest.Config;
import kong.unirest.UnirestInstance;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.bouncycastle.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

@Slf4j
@WireMockTest(httpsEnabled = true)
public abstract class AbstractTigerProxyTest {

  public static int fakeBackendServerPort = 0;
  public static int fakeBackendServerTlsPort = 0;
  public static byte[] binaryMessageContent = new byte[100];
  public TigerProxy tigerProxy;
  public UnirestInstance proxyRest;

  @BeforeEach
  public void setupBackendServer(WireMockRuntimeInfo runtimeInfo) {
    fakeBackendServerPort = runtimeInfo.getHttpPort();
    fakeBackendServerTlsPort = runtimeInfo.getHttpsPort();
    log.info(
        "Fake backend server started on ports {} (http) and {} (https)",
        fakeBackendServerPort,
        fakeBackendServerTlsPort);
    runtimeInfo
        .getWireMock()
        .register(
            get(urlMatching("/foobar.*"))
                .willReturn(
                    ok().withStatus(666)
                        .withStatusMessage("EVIL")
                        .withHeader("foo", "bar1", "bar2")
                        .withHeader("Some-Header-Field", "complicated-value §$%&/((=)(/(/&$()=§$§")
                        .withHeader("fooValue", "{{request.query.foo}}")
                        .withBody("{\"foo\":\"bar\"}")
                        .withTransformers("response-template")));
    runtimeInfo
        .getWireMock()
        .register(
            get(urlMatching("/deep/foobar.*"))
                .willReturn(
                    ok().withStatus(777)
                        .withStatusMessage("DEEPEREVIL")
                        .withHeader("foo", "bar1", "bar2")
                        .withBody("{\"foo\":\"bar\"}")));

    runtimeInfo
        .getWireMock()
        .register(get("/").willReturn(ok().withStatus(888).withBody("{\"home\":\"page\"}")));

    runtimeInfo
        .getWireMock()
        .register(get(urlMatching("/forward.*")).willReturn(permanentRedirect("/foobar")));
    runtimeInfo
        .getWireMock()
        .register(
            get(urlMatching("/deep/forward.*")).willReturn(permanentRedirect("/deep/foobar")));
    runtimeInfo
        .getWireMock()
        .register(
            get("/redirect/withAdditionalHeaders")
                .willReturn(
                    temporaryRedirect("/redirect/foobar")
                        .withHeader("additional-header", "test_value")));

    binaryMessageContent =
        Arrays.concatenate(
            "This is a meaningless string which will be binary content. And some more test chars: "
                .getBytes(StandardCharsets.UTF_8),
            RandomUtils.nextBytes(100));
    runtimeInfo
        .getWireMock()
        .register(post("/foobar").willReturn(ok().withBody(binaryMessageContent)));

    runtimeInfo
        .getWireMock()
        .register(
            post("/echo")
                .willReturn(
                    status(200)
                        .withStatusMessage("")
                        .withBody("{{request.body}}")
                        .withTransformers("response-template")));
    runtimeInfo
        .getWireMock()
        .register(
            get("/ok")
                .willReturn(status(200).withStatusMessage("").withBody("{'request':'body'}")));

    runtimeInfo
        .getWireMock()
        .register(
            get("/error")
                .willReturn(responseDefinition().withFault(Fault.CONNECTION_RESET_BY_PEER)));
  }

  @BeforeEach
  public void logTestName(TestInfo testInfo) {
    log.info(
        "Now executing test '{}' ({}:{})",
        testInfo.getDisplayName(),
        testInfo.getTestClass().map(Class::getName).orElse("<>"),
        testInfo.getTestMethod().map(Method::getName).orElse("<>"));
  }

  @AfterEach
  public void stopSpawnedTigerProxy() {
    shouldServerRun.set(false);
    if (tigerProxy != null) {
      log.info("Closing tigerProxy from '{}'...", this.getClass().getSimpleName());
      tigerProxy.close();
    }
  }

  public void spawnTigerProxyWithDefaultRoutesAndWith(TigerProxyConfiguration configuration) {
    if (configuration.getProxyRoutes() == null) {
      configuration.setProxyRoutes(new ArrayList<>());
    } else {
      configuration.setProxyRoutes(new ArrayList<>(configuration.getProxyRoutes()));
    }

    configuration
        .getProxyRoutes()
        .add(
            TigerConfigurationRoute.builder()
                .from("https://backend")
                .to("http://localhost:" + fakeBackendServerPort)
                .build());
    configuration
        .getProxyRoutes()
        .add(
            TigerConfigurationRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + fakeBackendServerPort)
                .build());
    configuration
        .getProxyRoutes()
        .add(
            TigerConfigurationRoute.builder()
                .from("/")
                .to("http://localhost:" + fakeBackendServerPort)
                .build());

    spawnTigerProxyWith(configuration);
  }

  public void spawnTigerProxyWith(TigerProxyConfiguration configuration) {
    configuration.setProxyLogLevel("ERROR");
    configuration.setName("Primary Tiger Proxy");
    if (configuration.getTls() == null) {
      configuration.setTls(new TigerTlsConfiguration());
    }
    if (StringUtils.isEmpty(configuration.getTls().getMasterSecretsFile())) {
      configuration.getTls().setMasterSecretsFile("target/master-secrets.txt");
    }
    tigerProxy = new TigerProxy(configuration);

    proxyRest =
        new UnirestInstance(
            new Config()
                .proxy("localhost", tigerProxy.getProxyPort())
                .sslContext(tigerProxy.buildSslContext())
                .connectTimeout(1000 * 1000)
                .socketTimeout(1000 * 1000)
                .automaticRetries(false));

    log.info(
        "WIRESHARK filter | | (http or tls) && (tcp.port in { {} {} {} })",
        tigerProxy.getProxyPort(),
        fakeBackendServerTlsPort,
        fakeBackendServerPort);
  }

  public void awaitMessagesInTiger(int numberOfMessagesExpected) {
    await()
        .atMost(5, TimeUnit.SECONDS)
        .until(
            () ->
                tigerProxy.getRbelLogger().getMessageHistory().stream()
                        .filter(el -> !el.hasFacet(RbelParsingNotCompleteFacet.class))
                        .count()
                    >= numberOfMessagesExpected);
  }

  public LoggedRequest getLastRequest(WireMock wireMock) {
    final List<ServeEvent> serveEvents = wireMock.getServeEvents();
    if (serveEvents.isEmpty()) {
      fail("Tried to get info on last request. None were found however!");
    }
    return serveEvents.get(serveEvents.size() - 1).getRequest();
  }

  AtomicBoolean shouldServerRun = new AtomicBoolean(true);
  ExecutorService threadPool = Executors.newCachedThreadPool();

  @SneakyThrows
  public int startKonnektorAlikeServerReturningAlways555(
      Optional<TigerConfigurationPkiIdentity> clientIdentity) {
    shouldServerRun.set(true);
    SSLContext sslContext = getSSLContext(clientIdentity);
    SSLServerSocketFactory ssf = sslContext.getServerSocketFactory();
    SSLServerSocket serverSocket = (SSLServerSocket) ssf.createServerSocket(0);
    String[] ciphers = {
      "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256", "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384"
    };
    serverSocket.setEnabledCipherSuites(ciphers);
    serverSocket.setEnabledProtocols(new String[] {"TLSv1.2"});
    clientIdentity.ifPresent(cert -> serverSocket.setNeedClientAuth(true));

    threadPool.execute(
        () -> {
          while (shouldServerRun.get()) {
            try {
              Socket socket = serverSocket.accept();
              OutputStream out = socket.getOutputStream();
              out.write("HTTP/1.1 555\r\nContent-Length: 0\r\n\r\n".getBytes());
              out.flush();
            } catch (IOException e) {
              // swallow
            }
          }
        });

    return serverSocket.getLocalPort();
  }

  protected SSLContext getSSLContext(Optional<TigerConfigurationPkiIdentity> clientIdentity)
      throws Exception {
    SSLContext sslContext = SSLContext.getInstance("TLS", new BouncyCastleJsseProvider());
    final TigerConfigurationPkiIdentity serverCert =
        new TigerConfigurationPkiIdentity("src/test/resources/eccStoreWithChain.jks;gematik");
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
            return clientIdentity
                .map(TigerConfigurationPkiIdentity::getCertificate)
                .map(cert -> new X509Certificate[] {cert})
                .orElse(new X509Certificate[0]);
          }
        };
    sslContext.init(kmf.getKeyManagers(), new X509TrustManager[] {x509TrustManager}, null);

    return sslContext;
  }
}
