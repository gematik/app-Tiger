/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr;

import static de.gematik.rbellogger.data.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import de.gematik.test.tiger.testenvmgr.servers.TigerProxyServer;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import kong.unirest.UnirestInstance;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.jcip.annotations.NotThreadSafe;
import org.junit.jupiter.api.Test;

@Slf4j
@Getter
@ResetTigerConfiguration
@NotThreadSafe
class TigerProxyMeshTest extends AbstractTestTigerTestEnvMgr {

  /**
   * This is a very unfortunate hack: Even after extensive analysis these tests failed on repeated
   * execution on only a handful systems. To get the tests running stable this is the measure we
   * took: for the tests where we let the socket timeout we wait at the start and at the end of the
   * test method 4 seconds Note: The conditions for the error to occur (repeated restarting of a
   * testenv-mgr with the given setup in the SAME JVM) seems extremely rare and should never happen
   * in production. This is a hack, but a carefully deliberated one.
   */
  private void waitShortTime() {
    await().pollDelay(4, TimeUnit.SECONDS).until(() -> true);
  }

  @SneakyThrows
  @Test
  @TigerTest(
      tigerYaml =
          """
            tigerProxy:
              skipTrafficEndpointsSubscription: true
              trafficEndpoints:
                - http://localhost:${free.port.2}
            servers:
              httpbin:
                type: httpbin
                serverPort: ${free.port.0}
                healthcheckUrl: http://127.0.0.1:${free.port.0}/status/200
              aggregatingProxy:
                type: tigerProxy
                dependsUpon: reverseProxy
                tigerProxyConfiguration:
                  adminPort: ${free.port.2}
                  proxyPort: ${free.port.3}
                  activateRbelParsing: false
                  rbelBufferSizeInMb: 0
                  trafficEndpoints:
                    - http://localhost:${free.port.4}
              reverseProxy:
                type: tigerProxy
                tigerProxyConfiguration:
                  adminPort: ${free.port.4}
                  proxiedServer: httpbin
                  proxyPort: ${free.port.5}
            lib:
              trafficVisualization: true
            """)
  void aggregateFromOneRemoteProxy_shouldTransmitMetadata(TigerTestEnvMgr envMgr) {
    waitShortTime();
    final String path = "/status/404";
    final UnirestInstance unirestInstance = Unirest.spawnInstance();
    unirestInstance
        .config()
        .sslContext(
            ((TigerProxyServer) envMgr.getServers().get("reverseProxy"))
                .getTigerProxy()
                .getConfiguredTigerProxySslContext());

    HttpResponse<String> response =
        unirestInstance
            .get("https://localhost:" + TigerGlobalConfiguration.readString("free.port.5") + path)
            .asString();
    int status = response.getStatus();

    assertThat(status).isEqualTo(404);

    await()
        .atMost(10, TimeUnit.SECONDS)
        .until(
            () -> {
              log.info(
                  "Local Proxy size: {}, aggregating Proxy size: {}, Remote Proxy size {}",
                  envMgr.getLocalTigerProxyOrFail().getRbelMessages().size(),
                  ((TigerProxyServer) envMgr.getServers().get("aggregatingProxy"))
                      .getTigerProxy()
                      .getRbelMessages()
                      .size(),
                  ((TigerProxyServer) envMgr.getServers().get("reverseProxy"))
                      .getTigerProxy()
                      .getRbelMessages()
                      .size());
              return envMgr.getLocalTigerProxyOrFail().getRbelLogger().getMessageHistory().size()
                      >= 2
                  && envMgr
                      .getLocalTigerProxyOrFail()
                      .getRbelLogger()
                      .getMessageHistory()
                      .getFirst()
                      .findElement("$.path")
                      .map(RbelElement::getRawStringContent)
                      .map(p -> p.endsWith(path))
                      .orElse(false);
            });

    envMgr.getLocalTigerProxyOrFail().waitForAllCurrentMessagesToBeParsed();
    assertThat(envMgr.getLocalTigerProxyOrFail().getRbelLogger().getMessageHistory().getFirst())
        .extractChildWithPath("$.tlsVersion")
        .hasStringContentEqualTo("TLSv1.2")
        .andTheInitialElement()
        .extractChildWithPath("$.cipherSuite")
        .hasStringContentEqualTo("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256");

    assertThat(envMgr.getLocalTigerProxyOrFail().getRbelLogger().getMessageList().get(0))
        .extractChildWithPath("$.sender.bundledServerName")
        .hasStringContentEqualTo("local client")
        .andTheInitialElement()
        .extractChildWithPath("$.receiver.bundledServerName")
        .hasStringContentEqualTo("httpbin");

    waitShortTime();
  }

  @SneakyThrows
  @Test
  @TigerTest(
      tigerYaml =
          """
            tigerProxy:
              skipTrafficEndpointsSubscription: true
              trafficEndpoints:
                - http://localhost:${free.port.12}
            servers:
              httpbin:
                type: httpbin
                serverPort: ${free.port.0}
                healthcheckUrl: http://127.0.0.1:${free.port.0}/status/200
              aggregatingProxy:
                type: tigerProxy
                dependsUpon: reverseProxy1, reverseProxy2
                tigerProxyConfiguration:
                  adminPort: ${free.port.12}
                  proxyPort: ${free.port.13}
                  activateRbelParsing: false
                  rbelBufferSizeInMb: 0
                  trafficEndpoints:
                    - http://localhost:${free.port.14}
                    - http://localhost:${free.port.16}
              reverseProxy1:
                type: tigerProxy
                tigerProxyConfiguration:
                  adminPort: ${free.port.14}
                  proxiedServer: httpbin
                  proxyPort: ${free.port.15}
              reverseProxy2:
                type: tigerProxy
                tigerProxyConfiguration:
                  adminPort: ${free.port.16}
                  proxiedServer: httpbin
                  proxyPort: ${free.port.17}
            """)
  void testWithMultipleUpstreamProxies(TigerTestEnvMgr envMgr) {
    waitShortTime();
    assertThat(envMgr.getLocalTigerProxyOrFail().getRbelLogger().getMessageHistory()).isEmpty();

    assertThat(
            Unirest.get("http://localhost:" + TigerGlobalConfiguration.readString("free.port.15"))
                .asString()
                .getStatus())
        .isEqualTo(200);
    assertThat(
            Unirest.get("http://localhost:" + TigerGlobalConfiguration.readString("free.port.17"))
                .asString()
                .getStatus())
        .isEqualTo(200);

    await()
        .atMost(10, TimeUnit.SECONDS)
        .until(
            () ->
                envMgr.getLocalTigerProxyOrFail().getRbelLogger().getMessageHistory().size() >= 4);
    waitShortTime();
  }

  @SneakyThrows
  @Test
  @TigerTest(
      tigerYaml =
          """
            tigerProxy:
              skipTrafficEndpointsSubscription: true
              trafficEndpoints:
                - http://localhost:${free.port.22}
            servers:
              aggregatingProxy:
                type: tigerProxy
                dependsUpon: reverseProxy
                tigerProxyConfiguration:
                  adminPort: ${free.port.22}
                  proxyPort: ${free.port.23}
                  activateRbelParsing: false
                  rbelBufferSizeInMb: 0
                  trafficEndpoints:
                    - http://localhost:${free.port.24}
              reverseProxy:
                type: tigerProxy
                tigerProxyConfiguration:
                  adminPort: ${free.port.24}
                  proxyPort: ${free.port.25}
                  directReverseProxy:
                    hostname: localhost
                    port: ${free.port.26}
            """)
  void testDirectReverseProxyMeshSetup_withoutResponse(TigerTestEnvMgr envMgr) {
    waitShortTime();
    try (Socket clientSocket =
            new Socket(
                "localhost", TigerGlobalConfiguration.readIntegerOptional("free.port.25").get());
        ServerSocket serverSocket =
            new ServerSocket(TigerGlobalConfiguration.readIntegerOptional("free.port.26").get())) {
      serverSocket.setSoTimeout(2);
      clientSocket.getOutputStream().write("{\"foo\":\"bar\"}".getBytes());
      clientSocket.getOutputStream().flush();
      try {
        serverSocket.accept();
      } catch (SocketTimeoutException ste) {
        log.warn("socket timeout", ste);
      }
      await()
          .atMost(10, TimeUnit.SECONDS)
          .until(
              () ->
                  !envMgr.getLocalTigerProxyOrFail().getRbelLogger().getMessageHistory().isEmpty());
    }
    waitShortTime();
  }

  @SneakyThrows
  @Test
  @TigerTest(
      tigerYaml =
          """
            tigerProxy:
              skipTrafficEndpointsSubscription: true
              adminPort: ${free.port.36}
              trafficEndpoints:
                - http://localhost:${free.port.32}
            servers:
              aggregatingProxy:
                type: tigerProxy
                dependsUpon: reverseProxy
                tigerProxyConfiguration:
                  adminPort: ${free.port.32}
                  proxyPort: ${free.port.33}
                  activateRbelParsing: false
                  rbelBufferSizeInMb: 0
                  trafficEndpoints:
                    - http://localhost:${free.port.34}
              reverseProxy:
                type: tigerProxy
                tigerProxyConfiguration:
                  adminPort: ${free.port.34}
                  proxyPort: ${free.port.35}
                  directReverseProxy:
                    hostname: reverseHostname
                    port: ${free.port.36}
            """)
  void testDirectReverseProxyMeshSetup_withResponse(TigerTestEnvMgr envMgr) {
    waitShortTime();
    try (Socket clientSocket =
        new Socket(
            "127.0.0.1",
            Integer.parseInt(TigerGlobalConfiguration.resolvePlaceholders("${free.port.35}")))) {
      clientSocket.setSoTimeout(1);
      clientSocket.getOutputStream().write("{\"foo\":\"bar\"}".getBytes());
      clientSocket.getOutputStream().flush();
      await()
          .atMost(10, TimeUnit.SECONDS)
          .until(
              () ->
                  !envMgr.getLocalTigerProxyOrFail().getRbelLogger().getMessageHistory().isEmpty());
    }
    waitShortTime();
  }

  @SneakyThrows
  @Test
  @TigerTest(
      tigerYaml =
          """
         tigerProxy:
           skipTrafficEndpointsSubscription: true
           adminPort: ${free.port.36}
           trafficEndpoints:
             - http://localhost:${free.port.34}
         servers:
           reverseProxy:
             type: tigerProxy
             tigerProxyConfiguration:
               adminPort: ${free.port.34}
               proxyPort: ${free.port.35}
               directReverseProxy:
                 hostname: reverseHostname
                 port: ${free.port.50}
        """)
  void testDirectReverseProxyMeshSetup_senderAndReceiverAreCorrect(TigerTestEnvMgr envMgr) {
    waitShortTime();

    try (Socket clientSocket =
        new Socket(
            "127.0.0.1",
            Integer.parseInt(TigerGlobalConfiguration.resolvePlaceholders("${free.port.35}")))) {

      clientSocket.setSoTimeout(1);
      clientSocket.getOutputStream().write("{\"foo\":\"bar\"}".getBytes());
      clientSocket.getOutputStream().flush();
      await()
          .atMost(10, TimeUnit.SECONDS)
          .until(
              () ->
                  envMgr.getLocalTigerProxyOrFail().getRbelLogger().getMessageHistory().size()
                      == 1);

      var senderPort = clientSocket.getLocalPort();
      var receiverPort =
          Integer.parseInt(TigerGlobalConfiguration.resolvePlaceholders("${free.port.50}"));
      var message =
          envMgr.getLocalTigerProxyOrFail().getRbelLogger().getMessageHistory().getFirst();
      assertThat(message)
          .extractChildWithPath("$.receiver")
          .hasStringContentEqualTo("reverseHostname:" + receiverPort)
          .andTheInitialElement()
          .extractChildWithPath("$.sender")
          .asString()
          .isIn("localhost:" + senderPort, "127.0.0.1:" + senderPort);
    }
    waitShortTime();
  }
}
