/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import de.gematik.rbellogger.data.facet.RbelHttpRequestFacet;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import de.gematik.test.tiger.testenvmgr.servers.TigerProxyServer;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
class ForwardProxyInfoTest {

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
            tigerProxyCfg:
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
          forwardToProxy:
            hostname: 127.0.0.1
            port: ${free.port.20}
        servers:
          someProxyServer:
            type: tigerProxy
            tigerProxyCfg:
              adminPort: ${free.port.11}
              proxyPort: ${free.port.21}
          virtualExternalServer:
            type: externalUrl
            source:
              - http://localhost:${free.port.31}
        """,
      skipEnvironmentSetup = true)
  void localSourceNonHttpAndForwardProxyConfigured_shouldStillStartUp(TigerTestEnvMgr envMgr) {
    AtomicBoolean shouldServerRun = new AtomicBoolean(true);
    final Integer port = TigerGlobalConfiguration.readIntegerOptional("free.port.31").orElseThrow();
    try (ServerSocket serverSocket = new ServerSocket(port)) {
      final Thread serverThread =
          new Thread(
              () -> {
                while (shouldServerRun.get()) {
                  try {
                    Socket socket = serverSocket.accept();
                    OutputStream out = socket.getOutputStream();
                    out.write("fdsa".getBytes());
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
            port: ${free.port.22}
        servers:
          someProxyServer:
            type: tigerProxy
            tigerProxyCfg:
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
    assertThatThrownBy(() -> envMgr.setUpEnvironment())
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
            tigerProxyCfg:
              adminPort: ${free.port.16}
              proxyPort: ${free.port.26}
          virtualExternalServer:
            type: externalUrl
            dependsUpon: someProxyServer
            source:
              - http://google.com/foobar
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
