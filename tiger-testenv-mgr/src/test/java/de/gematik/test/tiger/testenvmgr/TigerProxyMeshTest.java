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
package de.gematik.test.tiger.testenvmgr;

import static de.gematik.rbellogger.RbelConverterPlugin.createPlugin;
import static de.gematik.rbellogger.data.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConversionPhase;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.ProxyTransmissionHistory;
import de.gematik.rbellogger.data.core.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.data.core.TracingMessagePairFacet;
import de.gematik.rbellogger.facets.http.RbelHttpMessageFacet;
import de.gematik.rbellogger.facets.http.RbelHttpRequestFacet;
import de.gematik.rbellogger.facets.http.RbelHttpResponseFacet;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import de.gematik.test.tiger.testenvmgr.servers.TigerProxyServer;
import de.gematik.test.tiger.testenvmgr.utils.RandomTestUtils;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import kong.unirest.core.HttpResponse;
import kong.unirest.core.Unirest;
import kong.unirest.core.UnirestInstance;
import lombok.Cleanup;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.jcip.annotations.NotThreadSafe;
import org.jetbrains.annotations.NotNull;
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

    final Deque<RbelElement> reverseProxyMessages =
        ((TigerProxyServer) envMgr.getServers().get("reverseProxy"))
            .getTigerProxy()
            .getRbelMessages();
    await()
        .atMost(10, TimeUnit.SECONDS)
        .until(
            () -> {
              log.info(
                  "Local Proxy size: {}, aggregating Proxy size: (), Remote Proxy size {}",
                  envMgr.getLocalTigerProxyOrFail().getRbelMessages().size(),
                  reverseProxyMessages.size());
              return envMgr.getLocalTigerProxyOrFail().getRbelLogger().getMessageHistory().size()
                  >= 2;
            });

    val localTigerMessages =
        envMgr.getLocalTigerProxyOrFail().getRbelLogger().getMessageList().stream()
            .filter(el -> el.hasFacet(RbelHttpMessageFacet.class))
            .peek(el -> log.info(el.printTreeStructure()))
            .toList();
    assertThat(localTigerMessages.get(0))
        .hasStringContentEqualToAtPosition("$.tlsVersion", "TLSv1.2")
        .hasStringContentEqualToAtPosition("$.cipherSuite", "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256")
        .hasStringContentEqualToAtPosition("$.sender.bundledServerName", "local client")
        .hasStringContentEqualToAtPosition("$.receiver.bundledServerName", "httpbin")
        .hasStringContentEqualToAtPosition(
            "$.receiver.port", TigerGlobalConfiguration.readString("free.port.0"));
    assertThat(localTigerMessages.get(1))
        .hasStringContentEqualToAtPosition("$.tlsVersion", "TLSv1.2")
        .hasStringContentEqualToAtPosition("$.cipherSuite", "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256")
        .hasStringContentEqualToAtPosition("$.receiver.bundledServerName", "local client")
        .hasStringContentEqualToAtPosition("$.sender.bundledServerName", "httpbin")
        .hasStringContentEqualToAtPosition(
            "$.sender.port", TigerGlobalConfiguration.readString("free.port.0"));

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
        .atMost(20, TimeUnit.SECONDS)
        .until(
            () -> envMgr.getLocalTigerProxyOrFail().getRbelLogger().getMessageList(),
            l -> l.size() >= 4);
    waitShortTime();
  }

  @SneakyThrows
  @Test
  @TigerTest(
      tigerYaml =
          """
            tigerProxy:
              trafficEndpoints:
                - http://localhost:${free.port.14}
            servers:
              reverseProxy1:
                type: tigerProxy
                tigerProxyConfiguration:
                  adminPort: ${free.port.14}
                  proxyPort: ${free.port.15}
                  proxyRoutes:
                    - from: http://myServer
                      to: http://123.123.123.123:5678
            """)
  void unreachableRouteInUpstreamProxy_downstreamShouldStart(TigerTestEnvMgr envMgr) {
    waitShortTime();
    assertThat(envMgr.getLocalTigerProxyOrFail().getRbelLogger().getMessageHistory()).isEmpty();
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
            () -> envMgr.getLocalTigerProxyOrFail().getRbelLogger().getMessageHistory(),
            l -> !l.isEmpty());
    waitShortTime();
  }

  @SneakyThrows
  @Test
  @TigerTest(
      tigerYaml =
          """
            tigerProxy:
              adminPort: ${free.port.31}
              dependsOn: reverseProxy1, reverseProxy2
              skipTrafficEndpointsSubscription: true
              trafficEndpoints:
                - http://localhost:${free.port.32}
                - http://localhost:${free.port.35}
            servers:
              reverseProxy1:
                type: tigerProxy
                tigerProxyConfiguration:
                  adminPort: ${free.port.32}
                  proxyPort: ${free.port.33}
                  directReverseProxy:
                    hostname: localhost
                    port: ${free.port.31}
              reverseProxy2:
                type: tigerProxy
                tigerProxyConfiguration:
                  adminPort: ${free.port.35}
                  proxyPort: ${free.port.36}
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
            createPlugin(
                (e, c) -> {
                  if (e.hasFacet(RbelHttpRequestFacet.class)) {
                    reverseProxy2FinishedProcessing.join();
                  }
                }));
    envMgr
        .getLocalTigerProxyOrFail()
        .getRbelLogger()
        .getRbelConverter()
        .addConverter(
            createPlugin(
                (m, c) -> {
                  if (m.hasFacet(RbelHttpRequestFacet.class)) {
                    reverseProxy2FinishedProcessing.complete(true);
                  }
                }));
    val pool = Executors.newCachedThreadPool();
    for (String portNr : List.of("33", "36")) {
      val actualPort =
          Integer.parseInt(
              TigerGlobalConfiguration.resolvePlaceholders("${free.port." + portNr + "}"));
      pool.submit(
          () -> Unirest.post("http://localhost:" + actualPort).body(generateContent()).asString());
    }
    pool.awaitTermination(5, TimeUnit.SECONDS);

    envMgr.getLocalTigerProxyOrFail().waitForAllCurrentMessagesToBeParsed();
    RbelLogger rbelLogger = envMgr.getLocalTigerProxyOrFail().getRbelLogger();
    await()
        .atMost(10, TimeUnit.MINUTES)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .until(
            () ->
                rbelLogger.getMessageHistory().stream()
                    .filter(m -> m.hasFacet(RbelHttpRequestFacet.class))
                    .toList(),
            list -> list.size() == 2);
    pool.shutdown();

    envMgr.getLocalTigerProxyOrFail().waitForAllCurrentMessagesToBeParsed();

    var httpMessages =
        rbelLogger.getMessageHistory().stream()
            .filter(m -> m.hasFacet(RbelHttpRequestFacet.class))
            .toList();

    RbelElement msg0 = httpMessages.get(0);
    RbelElement msg1 = httpMessages.get(1);

    assertThat(msg0)
        .extractFacet(ProxyTransmissionHistory.class)
        .matches(h -> h.getTransmissionHops().size() == 1, "Expected 1 hop")
        .matches(
            h -> h.getTransmissionHops().get(0).getProxyName().equals("reverseProxy2"),
            "Expected hop from reverseProxy2");
    assertThat(msg1)
        .extractFacet(ProxyTransmissionHistory.class)
        .matches(h -> h.getTransmissionHops().size() == 1, "Expected 1 hop")
        .matches(
            h -> h.getTransmissionHops().get(0).getProxyName().equals("reverseProxy1"),
            "Expected hop from reverseProxy1");

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

  private static @NotNull String generateContent() {
    StringBuilder content = new StringBuilder("{\"foo\":[0");
    for (int i = 1; i < 1000; i++) {
      content.append(",").append(i);
    }
    content.append("]}\r\n");
    return content.toString();
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
            createPlugin(
                (e, c) -> {
                  try {
                    if (e.hasFacet(RbelTcpIpMessageFacet.class)) {
                      Thread.sleep(random.nextInt(0, maxDelay));
                    }
                  } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                  }
                }));
    final String path = "/anything/";
    @Cleanup final UnirestInstance unirestInstance = Unirest.spawnInstance();

    ExecutorService executorService = Executors.newFixedThreadPool(4);

    IntStream.range(0, numberOfSentMessages)
        .forEach(
            i ->
                executorService.submit(
                    () ->
                        unirestInstance
                            .get(
                                "http://localhost:"
                                    + TigerGlobalConfiguration.readString("free.port.5")
                                    + path
                                    + i)
                            .asString()));
    executorService.shutdown();

    try {
      await()
          .atMost(numberOfSentMessages * maxDelay + 5_000, TimeUnit.MILLISECONDS)
          .until(
              () ->
                  envMgr.getLocalTigerProxyOrFail().getRbelLogger().getMessageHistory().stream()
                          .filter(msg -> msg.hasFacet(RbelHttpMessageFacet.class))
                          .count()
                      == 2 * numberOfSentMessages);
    } catch (Exception e) {
      log.error(
          "Error while waiting for messages to be parsed. Got {} messages, wanted {}",
          envMgr.getLocalTigerProxyOrFail().getRbelLogger().getMessageHistory().size(),
          2 * numberOfSentMessages);
      throw e;
    }

    envMgr.getLocalTigerProxyOrFail().waitForAllCurrentMessagesToBeParsed();

    var messages = envMgr.getLocalTigerProxyOrFail().getRbelLogger().getMessageList();
    var responses = messages.stream().filter(m -> m.hasFacet(RbelHttpResponseFacet.class)).toList();

    for (RbelElement response : responses) {
      var tracingFacet = response.getFacet(TracingMessagePairFacet.class).orElseThrow();

      final Optional<RbelElement> pathElement = tracingFacet.getRequest().findElement("$.path");
      var requestPathFromFacetRequest = pathElement.orElseThrow().getRawStringContent();
      var responsePath = response.findElement("$.body.url").orElseThrow().getRawStringContent();

      assertThat(responsePath)
          .withFailMessage(
              () -> "Pairing is not correct for message with id '" + response.getUuid() + "'")
          .endsWith(requestPathFromFacetRequest);
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
            createPlugin(
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
                }));
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
            () -> envMgr.getLocalTigerProxyOrFail().getRbelLogger().getMessageHistory().size(),
            size -> size == 2 * cycles);

    envMgr.getLocalTigerProxyOrFail().waitForAllCurrentMessagesToBeParsed();

    var messages = envMgr.getLocalTigerProxyOrFail().getRbelLogger().getMessageList();

    var responses = messages.stream().filter(m -> m.hasFacet(RbelHttpResponseFacet.class)).toList();

    for (int i = 0; i < responses.size(); i++) {
      RbelElement r = responses.get(i);
      log.info("Now checking msg {} with uuid {}", i, r.getUuid());
      var tracingFacet =
          r.getFacet(TracingMessagePairFacet.class)
              .orElseThrow(
                  () ->
                      new RuntimeException(
                          "No TracingMessagePairFacet found for message with id '"
                              + r.getUuid()
                              + "' and http: "
                              + r.printHttpDescription()));
      var requestPath =
          tracingFacet.getRequest().findElement("$.path").orElseThrow().getRawStringContent();

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
  void waitingForAlreadyRemovedMessage_shouldNotBlock(TigerTestEnvMgr envMgr) {
    waitShortTime();

    var requestProcessedAndRemovedFromHistory = new CompletableFuture<String>();

    var messages = new LinkedList<RbelElement>();
    envMgr
        .getLocalTigerProxyOrFail()
        .getRbelLogger()
        .getRbelConverter()
        .addConverter(
            new RbelConverterPlugin() {
              @Override
              public RbelConversionPhase getPhase() {
                return RbelConversionPhase.TRANSMISSION;
              }

              @Override
              public void consumeElement(RbelElement e, RbelConversionExecutor converter) {
                if (e.hasFacet(RbelTcpIpMessageFacet.class)) {
                  messages.add(e);
                  converter.removeMessage(e);
                }
                if (e.hasFacet(RbelHttpRequestFacet.class)) {
                  requestProcessedAndRemovedFromHistory.complete(e.getUuid());
                }
              }
            });

    ((TigerProxyServer) envMgr.getServers().get("reverseProxy"))
        .getTigerProxy()
        .getRbelLogger()
        .getRbelConverter()
        .addConverter(
            createPlugin(
                (e, c) -> {
                  if (e.hasFacet(RbelHttpResponseFacet.class)) {
                    requestProcessedAndRemovedFromHistory.join();
                  }
                }));
    final String path = "/anything/";
    @Cleanup final UnirestInstance unirestInstance = Unirest.spawnInstance();
    unirestInstance
        .config()
        .sslContext(
            ((TigerProxyServer) envMgr.getServers().get("reverseProxy"))
                .getTigerProxy()
                .getConfiguredTigerProxySslContext());

    unirestInstance
        .get("https://localhost:" + TigerGlobalConfiguration.readString("free.port.5") + path + 0)
        .asString();

    await().atMost(2, TimeUnit.SECONDS).until(messages::size, size -> size == 2);

    waitShortTime();
  }
}
