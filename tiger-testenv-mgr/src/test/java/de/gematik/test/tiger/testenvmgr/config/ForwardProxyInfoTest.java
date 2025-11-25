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
package de.gematik.test.tiger.testenvmgr.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import de.gematik.rbellogger.facets.http.RbelHttpRequestFacet;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.pki.TigerConfigurationPkiIdentity;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import de.gematik.test.tiger.testenvmgr.servers.TigerProxyServer;
import de.gematik.test.tiger.testenvmgr.util.TigerEnvironmentStartupException;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
class ForwardProxyInfoTest {

  private AtomicBoolean shouldServerRun = new AtomicBoolean(true);

  @SneakyThrows
  @Test
  @TigerTest(
      tigerYaml =
          """
          tigerProxy:
            forwardToProxy:
              hostname: 127.0.0.1
              port: ${free.port.24}
          servers:
            someProxyServer:
              type: tigerProxy
              tigerProxyConfiguration:
                adminPort: ${free.port.14}
                proxyPort: ${free.port.24}
            virtualExternalServer:
              type: externalUrl
              dependsUpon: someProxyServer
              source:
                - http://localhost:${free.port.14}/foobar
          """)
  void localSource_shouldNotUseForwardProxy(TigerTestEnvMgr envMgr) {
    assertThat(
            ((TigerProxyServer) envMgr.getServers().get("someProxyServer"))
                .getApplicationContext()
                .getBean(TigerProxy.class)
                .getRbelMessages())
        .isEmpty();
  }

  @SneakyThrows
  @Test
  @TigerTest(
      tigerYaml =
          """
          tigerProxy:
          servers:
            virtualExternalServer:
              type: externalUrl
              source:
                - http://localhost:${free.port.30}
          """,
      skipEnvironmentSetup = true)
  void localSourceNonHttp_shouldStillStartUp(TigerTestEnvMgr envMgr) {
    AtomicBoolean shouldServerRun = new AtomicBoolean(true);
    final Integer port = TigerGlobalConfiguration.readIntegerOptional("free.port.30").orElseThrow();
    try (ServerSocket serverSocket = new ServerSocket(port)) {
      final Thread serverThread =
          new Thread(
              () -> {
                while (shouldServerRun.get()) {
                  try {
                    Socket socket = serverSocket.accept();
                    OutputStream out = socket.getOutputStream();
                    out.write("pong".getBytes());
                    out.close();
                    socket.close();
                  } catch (IOException e) {
                    // swallow
                  }
                }
              });
      serverThread.start();
      assertThatNoException().isThrownBy(envMgr::setUpEnvironment);
    } finally {
      shouldServerRun.set(false);
    }
  }

  @SneakyThrows
  @Test
  @TigerTest(
      tigerYaml =
          """
          tigerProxy:
          servers:
            virtualExternalServer:
              type: externalUrl
              source:
                - http://localhost:${free.port.30}
          """,
      skipEnvironmentSetup = true)
  void failingHttpsServer_shouldStillStartUp(TigerTestEnvMgr envMgr) {
    AtomicBoolean shouldServerRun = new AtomicBoolean(true);
    final Integer port = TigerGlobalConfiguration.readIntegerOptional("free.port.30").orElseThrow();
    try (ServerSocket serverSocket = new ServerSocket(port)) {
      final Thread serverThread =
          new Thread(
              () -> {
                while (shouldServerRun.get()) {
                  try {
                    Socket socket = serverSocket.accept();
                    OutputStream out = socket.getOutputStream();
                    out.write("pong".getBytes());
                    out.close();
                    socket.close();
                  } catch (IOException e) {
                    // swallow
                  }
                }
              });
      serverThread.start();
      assertThatNoException().isThrownBy(envMgr::setUpEnvironment);
    } finally {
      shouldServerRun.set(false);
    }
  }

  @SneakyThrows
  @Test
  @TigerTest(
      tigerYaml =
          """
          tigerProxy:
            forwardToProxy:
              hostname: 127.0.0.1
              port: ${free.port.20}
          servers:
            someProxyServer:
              type: tigerProxy
              tigerProxyConfiguration:
                adminPort: ${free.port.11}
                proxyPort: ${free.port.21}
            virtualExternalServer:
              type: externalUrl
              source:
                - https://localhost:${free.port.31}
          """,
      skipEnvironmentSetup = true)
  void localSourceNonHttpAndForwardProxyConfigured_shouldStillStartUp(TigerTestEnvMgr envMgr) {
    AtomicBoolean shouldServerRun = new AtomicBoolean(true);
    final Integer port = TigerGlobalConfiguration.readIntegerOptional("free.port.31").orElseThrow();
    try {
      startDistrustingSslServer(port);
      assertThatNoException().isThrownBy(envMgr::setUpEnvironment);
    } finally {
      shouldServerRun.set(false);
    }
  }

  @SneakyThrows
  public int startDistrustingSslServer(Integer port) {
    var threadPool = Executors.newCachedThreadPool();
    SSLContext sslContext = getSSLContext();
    SSLServerSocketFactory ssf = sslContext.getServerSocketFactory();
    SSLServerSocket serverSocket = (SSLServerSocket) ssf.createServerSocket(port);
    serverSocket.setNeedClientAuth(true);

    threadPool.execute(
        () -> {
          while (shouldServerRun.get()) {
            try {
              Socket socket = serverSocket.accept();
              socket.close();
            } catch (IOException e) {
              // swallow
            }
          }
        });

    return serverSocket.getLocalPort();
  }

  protected SSLContext getSSLContext() throws Exception {
    SSLContext sslContext = SSLContext.getInstance("TLS");
    final TigerConfigurationPkiIdentity serverCert =
        new TigerConfigurationPkiIdentity(
            "../tiger-proxy/src/test/resources/eccStoreWithChain.jks;gematik");
    // Set up key manager factory to use our key store
    KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    kmf.init(serverCert.toKeyStoreWithPassword("00"), "00".toCharArray());

    // Initialize the SSLContext to work with our key managers.
    final X509TrustManager x509TrustManager =
        new X509TrustManager() {
          @Override
          public void checkClientTrusted(X509Certificate[] chain, String authType)
              throws CertificateException {
            throw new CertificateException("go fish");
          }

          @Override
          public void checkServerTrusted(X509Certificate[] chain, String authType) {
            // swallow
          }

          @Override
          public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[] {};
          }
        };
    sslContext.init(kmf.getKeyManagers(), new X509TrustManager[] {x509TrustManager}, null);

    return sslContext;
  }

  @SneakyThrows
  @Test
  @TigerTest(
      tigerYaml =
          """
          tigerProxy:
            forwardToProxy:
              hostname: 127.0.0.1
              port: ${free.port.22}
          servers:
            someProxyServer:
              type: tigerProxy
              tigerProxyConfiguration:
                adminPort: ${free.port.12}
                proxyPort: ${free.port.22}
            virtualExternalServer:
              type: externalUrl
              startupTimeoutSec: 1
              source:
                - http://localhost:${free.port.32}
          """,
      skipEnvironmentSetup = true)
  void localSourceServerNotRunningAndForwardProxyConfigured_shouldGiveStartupError(
      TigerTestEnvMgr envMgr) {
    assertThatThrownBy(envMgr::setUpEnvironment)
        .isInstanceOf(TigerEnvironmentStartupException.class)
        .cause()
        .isInstanceOf(TigerTestEnvException.class)
        .hasMessageStartingWith(
            "Timeout waiting for external server 'virtualExternalServer' to respond at");
  }

  @SneakyThrows
  @Test
  @TigerTest(
      tigerYaml =
          """
          tigerProxy:
            forwardToProxy:
              hostname: 127.0.0.1
              port: ${free.port.26}
          servers:
            someProxyServer:
              type: tigerProxy
              tigerProxyConfiguration:
                adminPort: ${free.port.16}
                proxyPort: ${free.port.26}
            virtualExternalServer:
              type: externalUrl
              dependsUpon: someProxyServer
              source:
                - http://badssl/foobar
          """)
  void externalSource_shouldUseForwardProxy(TigerTestEnvMgr envMgr) {
    await()
        .atMost(5, TimeUnit.SECONDS)
        .until(
            () ->
                ((TigerProxyServer) envMgr.getServers().get("someProxyServer"))
                    .getApplicationContext().getBean(TigerProxy.class).getRbelMessages().stream()
                        .anyMatch(
                            el ->
                                el.getFacet(RbelHttpRequestFacet.class)
                                    .map(
                                        req ->
                                            req.getPath().getRawStringContent().contains("foobar"))
                                    .orElse(false)));
  }
}
