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

package de.gematik.test.tiger.testenvmgr;

import static de.gematik.rbellogger.data.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.ProxyTransmissionHistory;
import de.gematik.rbellogger.data.facet.RbelHttpRequestFacet;
import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import de.gematik.rbellogger.data.facet.RbelJsonFacet;
import de.gematik.rbellogger.data.facet.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.data.facet.TracingMessagePairFacet;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import de.gematik.test.tiger.testenvmgr.servers.TigerProxyServer;
import de.gematik.test.tiger.testenvmgr.utils.RandomTestUtils;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import kong.unirest.UnirestInstance;
import lombok.Cleanup;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.jcip.annotations.NotThreadSafe;
import org.assertj.core.presentation.Representation;
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

    assertThat(envMgr.getLocalTigerProxyOrFail().getRbelLogger().getMessageList().get(0))
        .extractChildWithPath("$.receiver.port")
        .hasStringContentEqualTo(TigerGlobalConfiguration.readString("free.port.0"));
    assertThat(envMgr.getLocalTigerProxyOrFail().getRbelLogger().getMessageList().get(1))
        .extractChildWithPath("$.sender.port")
        .hasStringContentEqualTo(TigerGlobalConfiguration.readString("free.port.0"));

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
                    hostname: localhost
                    port: ${free.port.36}
            """)
  void testDirectReverseProxyMeshSetup_withResponse(TigerTestEnvMgr envMgr) {
    waitShortTime();
    try (Socket clientSocket =
        new Socket(
            "127.0.0.1",
            Integer.parseInt(TigerGlobalConfiguration.resolvePlaceholders("${free.port.35}")))) {
      clientSocket.setSoTimeout(1);
      clientSocket.getOutputStream().write(("{\"foo\":\"" + "bar".repeat(1000) + "\"}").getBytes());
      clientSocket.getOutputStream().flush();
    }

    await()
        .atMost(10, TimeUnit.SECONDS)
        .until(
            () -> !envMgr.getLocalTigerProxyOrFail().getRbelLogger().getMessageHistory().isEmpty());
    waitShortTime();
  }

  @SneakyThrows
  @Test
  @TigerTest(
      tigerYaml =
          """
            tigerProxy:
              skipTrafficEndpointsSubscription: true
              adminPort: ${free.port.31}
              dependsOn: reverseProxy1, reverseProxy2
              trafficEndpoints:
                - http://localhost:${free.port.32}
                - http://localhost:${free.port.35}
            servers:
              reverseProxy1:
                type: tigerProxy
                tigerProxyConfiguration:
                  adminPort: ${free.port.32}
                  proxyPort: ${free.port.33}
                  activateRbelParsing: false
                  directReverseProxy:
                    hostname: localhost
                    port: ${free.port.31}
              reverseProxy2:
                type: tigerProxy
                tigerProxyConfiguration:
                  adminPort: ${free.port.35}
                  proxyPort: ${free.port.36}
                  activateRbelParsing: false
                  directReverseProxy:
                    hostname: localhost
                    port: ${free.port.31}
            """)
  void testDirectReverseProxyMeshSetup_parallelUpstreamProxies(TigerTestEnvMgr envMgr) {
    waitShortTime();

    // wait for message of reverse proxy 2 to be processed before end
    // of conversion of reverse proxy 1
    CompletableFuture<Boolean> reverseProxy2FinishedProcessing = new CompletableFuture<>();
    ((TigerProxyServer) envMgr.getServers().get("reverseProxy1"))
        .getTigerProxy()
        .getRbelLogger()
        .getRbelConverter()
        .addConverter(
            (e, c) -> {
              reverseProxy2FinishedProcessing.join();
            });
    ((TigerProxyServer) envMgr.getServers().get("reverseProxy2"))
        .getTigerProxy()
        .addRbelMessageListener(
            (m) -> {
              if (!m.getContent().startsWith("HTTP/1.1".getBytes())
                  && m.getContent().endsWith("]}".getBytes())) {
                await().pollDelay(500, TimeUnit.MILLISECONDS).until(() -> true);
                reverseProxy2FinishedProcessing.complete(true);
              }
            });
    for (String portNr : List.of("33", "36")) {
      CompletableFuture.runAsync(
          () -> {
            try (Socket clientSocket =
                new Socket(
                    "127.0.0.1",
                    Integer.parseInt(
                        TigerGlobalConfiguration.resolvePlaceholders(
                            "${free.port." + portNr + "}")))) {
              clientSocket.setSoTimeout(1);
              OutputStream outputStream = clientSocket.getOutputStream();
              outputStream.write("{\"foo\":[0".getBytes());
              for (int i = 1; i < 1000; i++) {
                outputStream.write(("," + i).getBytes());
                outputStream.flush();
              }
              outputStream.write("]}".getBytes());
              outputStream.close();
            } catch (Exception e) {
              log.error("Error while sending message", e);
            }
          });
    }

    RbelLogger rbelLogger = envMgr.getLocalTigerProxyOrFail().getRbelLogger();
    await()
        .atMost(10, TimeUnit.SECONDS)
        .until(
            () ->
                rbelLogger.getMessageHistory().stream()
                        .filter(m -> m.hasFacet(RbelJsonFacet.class))
                        .toList()
                        .size()
                    == 2);

    rbelLogger.getRbelConverter().waitForAllCurrentMessagesToBeParsed();

    var jsonMessages =
        rbelLogger.getMessageHistory().stream()
            .filter(m -> m.hasFacet(RbelJsonFacet.class))
            .toList();

    RbelElement msg0 = jsonMessages.get(0);
    RbelElement msg1 = jsonMessages.get(1);

    assertThat(msg0)
        .extractFacet(ProxyTransmissionHistory.class)
        .matches(
            proxyTransmissionHistory ->
                proxyTransmissionHistory.getTransmissionHops().size() == 1
                    && proxyTransmissionHistory
                        .getTransmissionHops()
                        .get(0)
                        .getProxyName()
                        .equals("reverseProxy2"));
    assertThat(msg1)
        .extractFacet(ProxyTransmissionHistory.class)
        .matches(
            proxyTransmissionHistory ->
                proxyTransmissionHistory.getTransmissionHops().size() == 1
                    && proxyTransmissionHistory
                        .getTransmissionHops()
                        .get(0)
                        .getProxyName()
                        .equals("reverseProxy1"));

    var messages = rbelLogger.getMessageHistory().stream().toList();
    for (int i = 0; i < messages.size() - 1; i++) {
      Long sequenceNumber0 =
          messages.get(i).getFacetOrFail(RbelTcpIpMessageFacet.class).getSequenceNumber();
      Long sequenceNumber1 =
          messages.get(i + 1).getFacetOrFail(RbelTcpIpMessageFacet.class).getSequenceNumber();

      assertThat(sequenceNumber0).isLessThan(sequenceNumber1);
    }

    waitShortTime();
  }

  @SneakyThrows
  @Test
  @TigerTest(
      tigerYaml =
          """
         lib:
             trafficVisualization: true
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
            "localhost",
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

      val senderPort = clientSocket.getLocalPort();
      val receiverPort =
          Integer.parseInt(TigerGlobalConfiguration.resolvePlaceholders("${free.port.50}"));
      val message =
          envMgr.getLocalTigerProxyOrFail().getRbelLogger().getMessageHistory().getFirst();
      assertThat(message)
          .extractChildWithPath("$.receiver")
          .hasStringContentEqualTo("reverseHostname:" + receiverPort)
          .andTheInitialElement()
          .extractChildWithPath("$.sender")
          .asString()
          .matches("((view-|)localhost|127\\.0\\.0\\.1):" + senderPort);

      assertThat(message)
          .extractChildWithPath("$.receiver.bundledServerName")
          .hasStringContentEqualTo("reverseHostname");
    }
    waitShortTime();
  }

  /**
   * This test sends multiple messages in parallel to the "reverseProxy". This will be transmitted
   * to the local tiger proxy. We check that the messages are correctly paired by comparing the
   * request inside the RbelHttpResponseFacet with the request inside the TracingMessagePairFacet.
   *
   * <p>Before TGR-1387, these were not always matching.
   */
  @SneakyThrows
  @Test
  @TigerTest(
      tigerYaml =
          """
    tigerProxy:
      skipTrafficEndpointsSubscription: true
      trafficEndpoints:
        - http://localhost:${free.port.4}
    servers:
      httpbin:
        type: httpbin
        serverPort: ${free.port.0}
        healthcheckUrl: http://127.0.0.1:${free.port.0}/status/200
      reverseProxy:
        type: tigerProxy
        tigerProxyConfiguration:
          adminPort: ${free.port.4}
          proxiedServer: httpbin
          proxyPort: ${free.port.5}
    """)
  void testTracingFacetsAndHttpFacetHaveCorrectRequest(TigerTestEnvMgr envMgr) {
    waitShortTime();
    val numberOfSentMessages = 200;
    val maxDelay = 20;
    final Random random = RandomTestUtils.createRandomGenerator();
    ((TigerProxyServer) envMgr.getServers().get("reverseProxy"))
        .getTigerProxy()
        .getRbelLogger()
        .getRbelConverter()
        .addConverter(
            (e, c) -> {
              try {
                if (e.hasFacet(RbelTcpIpMessageFacet.class)) {
                  Thread.sleep(random.nextInt(0, maxDelay));
                }
              } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
              }
            });
    final String path = "/anything/";
    @Cleanup final UnirestInstance unirestInstance = Unirest.spawnInstance();

    IntStream.range(0, numberOfSentMessages)
        .parallel()
        .forEach(
            i ->
                unirestInstance
                    .get(
                        "http://localhost:"
                            + TigerGlobalConfiguration.readString("free.port.5")
                            + path
                            + i)
                    .asString());

    await()
        .atMost(numberOfSentMessages * maxDelay, TimeUnit.MILLISECONDS)
        .until(
            () ->
                envMgr.getLocalTigerProxyOrFail().getRbelLogger().getMessageHistory().size()
                    == 2 * numberOfSentMessages);

    envMgr.getLocalTigerProxyOrFail().waitForAllCurrentMessagesToBeParsed();

    var messages = envMgr.getLocalTigerProxyOrFail().getRbelLogger().getMessageList();
    var responses = messages.stream().filter(m -> m.hasFacet(RbelHttpResponseFacet.class)).toList();

    for (RbelElement r : responses) {
      var httpFacet = r.getFacet(RbelHttpResponseFacet.class).orElseThrow();
      var tracingFacet = r.getFacet(TracingMessagePairFacet.class).orElseThrow();

      var requestPathFromFacetRequest =
          httpFacet.getRequest().findElement("$.path").orElseThrow().getRawStringContent();
      var responsePath = r.findElement("$.body.url").orElseThrow().getRawStringContent();

      assertThat(tracingFacet.getRequest())
          .withRepresentation(new RbelElementRepresentation())
          .isEqualTo(httpFacet.getRequest());

      assertThat(responsePath).endsWith(requestPathFromFacetRequest);
    }

    messages.stream()
        .filter(m -> log.isDebugEnabled())
        .map(RbelElement::printHttpDescription)
        .forEach(log::debug);

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
        - http://localhost:${free.port.4}
    servers:
      httpbin:
        type: httpbin
        serverPort: ${free.port.0}
        healthcheckUrl: http://127.0.0.1:${free.port.0}/status/200
      reverseProxy:
        type: tigerProxy
        tigerProxyConfiguration:
          adminPort: ${free.port.4}
          proxiedServer: httpbin
          proxyPort: ${free.port.5}
    """)
  void delayedParsing_sequenceShouldStillMatch(TigerTestEnvMgr envMgr) {

    waitShortTime();
    val maxDelay = 20;
    final Random random = RandomTestUtils.createRandomGenerator();
    ((TigerProxyServer) envMgr.getServers().get("reverseProxy"))
        .getTigerProxy()
        .getRbelLogger()
        .getRbelConverter()
        .addConverter(
            (e, c) -> {
              if (e.hasFacet(RbelHttpRequestFacet.class)) {
                try {
                  final int millis = random.nextInt(0, maxDelay);
                  log.info("Delaying for {} ms", millis);
                  Thread.sleep(millis);
                } catch (InterruptedException ex) {
                  throw new RuntimeException(ex);
                }
              }
            });
    final String path = "/anything/";
    @Cleanup final UnirestInstance unirestInstance = Unirest.spawnInstance();
    unirestInstance
        .config()
        .sslContext(
            ((TigerProxyServer) envMgr.getServers().get("reverseProxy"))
                .getTigerProxy()
                .getConfiguredTigerProxySslContext());

    int cycles = 200;
    IntStream.range(0, cycles)
        .forEach(
            i ->
                unirestInstance
                    .get(
                        "https://localhost:"
                            + TigerGlobalConfiguration.readString("free.port.5")
                            + path
                            + i)
                    .asString());

    await()
        .atMost(cycles * maxDelay, TimeUnit.MILLISECONDS)
        .until(
            () ->
                envMgr.getLocalTigerProxyOrFail().getRbelLogger().getMessageHistory().size()
                    == 2 * cycles);

    envMgr.getLocalTigerProxyOrFail().waitForAllCurrentMessagesToBeParsed();

    var messages = envMgr.getLocalTigerProxyOrFail().getRbelLogger().getMessageList();

    var responses = messages.stream().filter(m -> m.hasFacet(RbelHttpResponseFacet.class)).toList();

    for (int i = 0; i < responses.size(); i++) {
      RbelElement r = responses.get(i);
      var httpFacet = r.getFacet(RbelHttpResponseFacet.class).orElseThrow();
      var tracingFacet = r.getFacet(TracingMessagePairFacet.class).orElseThrow();
      assertThat(tracingFacet.getRequest())
          .withRepresentation(new RbelElementRepresentation())
          .isEqualTo(httpFacet.getRequest());

      var requestPath =
          httpFacet.getRequest().findElement("$.path").orElseThrow().getRawStringContent();
      assertThat(requestPath).contains("/anything/" + i);
      var responsePath = r.findElement("$.body.url").orElseThrow().getRawStringContent();

      assertThat(responsePath).endsWith(requestPath);
    }

    for (int i = 0; i < cycles; i++) {
      log.info("Processing message {}", i);
      int reqIndex = i * 2;
      int respIndex = reqIndex + 1;
      var req = messages.get(reqIndex);
      var resp = messages.get(respIndex);

      assertThat(req.getFacetOrFail(RbelTcpIpMessageFacet.class).getSequenceNumber())
          .isEqualTo(reqIndex);
      assertThat(resp.getFacetOrFail(RbelTcpIpMessageFacet.class).getSequenceNumber())
          .isEqualTo(respIndex);

      assertThat(req)
          .extractFacet(ProxyTransmissionHistory.class)
          .isEqualTo(new ProxyTransmissionHistory("reverseProxy", List.of((long) reqIndex), null));
      assertThat(resp)
          .extractFacet(ProxyTransmissionHistory.class)
          .isEqualTo(new ProxyTransmissionHistory("reverseProxy", List.of((long) respIndex), null));
    }

    waitShortTime();
  }

  static class RbelElementRepresentation implements Representation {

    @Override
    public String toStringOf(Object object) {
      if (object instanceof RbelElement rbelElement) {
        return rbelElement.printTreeStructureWithoutColors();
      } else {
        return object.toString();
      }
    }
  }
}
