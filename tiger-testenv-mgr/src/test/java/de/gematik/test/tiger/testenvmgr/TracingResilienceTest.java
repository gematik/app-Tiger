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

import static de.gematik.test.tiger.common.config.TigerGlobalConfiguration.readIntegerOptional;
import static de.gematik.test.tiger.common.config.TigerGlobalConfiguration.resolvePlaceholders;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Awaitility.waitAtMost;
import static org.junit.jupiter.api.Assertions.fail;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.util.TigerSerializationUtil;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.TigerProxyApplication;
import de.gematik.test.tiger.proxy.data.TigerDownloadedMessageFacet;
import de.gematik.test.tiger.testenvmgr.config.tigerproxy_standalone.CfgStandaloneProxy;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import kong.unirest.core.Unirest;
import kong.unirest.core.UnirestInstance;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.awaitility.core.ConditionTimeoutException;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

@Slf4j
@Tag("de.gematik.test.tiger.common.LongRunnerTest")
class TracingResilienceTest {

  private static final int MASTER_ROUNDS = 1000;
  private static final int MESSAGES_PER_ROUND = 2;
  private static final String EMPTY_MESSAGE_STRING = "                             ";
  private ConfigurableApplicationContext aggregatingProxyContext;
  private TigerProxy receivingProxy;

  private int aggregatingAdminPort;

  /** Receiving <-- Aggregating <-- Sending (local) */
  @Test
  @TigerTest(
      tigerYaml =
          """
          tigerProxy:
            adminPort: ${free.port.12}
            proxyPort: ${free.port.22}
            rbelBufferSizeInMb: 50
            name: Sending proxy
          """,
      skipEnvironmentSetup = true)
  void generateTrafficAndBounceViaRemoteProxy(TigerTestEnvMgr testEnvMgr) throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      aggregatingAdminPort = socket.getLocalPort();
    }

    // Sending Proxy
    testEnvMgr.setUpEnvironment();
    // Aggregating Proxy
    bootTigerProxy(aggregatingAdminPort);
    // Receiving Proxy
    startReceivingProxy();

    final UnirestInstance instance = Unirest.spawnInstance();
    instance.config().proxy("127.0.0.1", readIntegerOptional("free.port.22").orElseThrow());
    instance.config().followRedirects(false);

    for (int i = 0; i < MASTER_ROUNDS; i++) {
      randomlyCrashAggregatingProxy();
      randomlyRebootAggregatingProxy(aggregatingAdminPort);
      // this line makes it easier to catch up: we remove the race condition of new traffic coming
      // in WHILE aggregating and receiving proxy are trying to catch up.
      giveAggregatingProxyTimeToCatchUpIfRunning(testEnvMgr, i);
      for (int j = 0; j < MESSAGES_PER_ROUND; j++) {
        var randomMarker = "messageNumber" + (i * MESSAGES_PER_ROUND + j);
        log.info("Sending message {}", randomMarker);
        instance
            .get(
                TigerGlobalConfiguration.resolvePlaceholders(
                    "http://localhost:${free.port.12}/" + randomMarker))
            .asEmpty();
      }
      giveAggregatingProxyTimeToCatchUpIfRunning(testEnvMgr, i + 1);
      log.info(
          "Sent {} msgs, sending-proxy has {} msgs, receiving-proxy has {} msgs",
          (i + 1) * MESSAGES_PER_ROUND * 2,
          testEnvMgr.getLocalTigerProxyOrFail().getRbelMessages().size(),
          getReceivingTigerProxyMessages().size());
    }

    if (aggregatingProxyContext == null) {
      bootTigerProxy(aggregatingAdminPort);
    }

    await()
        .atMost(20, TimeUnit.SECONDS)
        .pollInterval(1, TimeUnit.SECONDS)
        .until(
            () -> {
              log.info(
                  "We sent {} message, intercepted {}, aggregating {}, receiving {}",
                  MASTER_ROUNDS * MESSAGES_PER_ROUND * 2,
                  testEnvMgr.getLocalTigerProxyOrFail().getRbelMessages().size(),
                  aggregatingProxyContext.getBean(TigerProxy.class).getRbelMessages().size(),
                  getReceivingTigerProxyMessages().size());
              return MASTER_ROUNDS * MESSAGES_PER_ROUND * 2
                  == getReceivingTigerProxyMessages().size();
            });
  }

  private void startReceivingProxy() {
    log.info(
        "Starting Receiving Proxy (Ports {} & {})...",
        readIntegerOptional("free.port.10").orElseThrow(),
        readIntegerOptional("free.port.20").orElseThrow());
    receivingProxy =
        new TigerProxy(
            TigerProxyConfiguration.builder()
                .adminPort(readIntegerOptional("free.port.10").orElseThrow())
                .proxyPort(readIntegerOptional("free.port.20").orElseThrow())
                .trafficEndpoints(List.of("http://localhost:" + aggregatingAdminPort))
                .downloadInitialTrafficFromEndpoints(true)
                .connectionTimeoutInSeconds(100)
                .skipTrafficEndpointsSubscription(false)
                .name("Receiving proxy")
                .build());
    receivingProxy.subscribeToTrafficEndpoints();
    log.info("Started Receiving Proxy");
  }

  private void giveAggregatingProxyTimeToCatchUpIfRunning(TigerTestEnvMgr testEnvMgr, int round) {
    if (aggregatingProxyContext != null) {
      try {
        waitAtMost(2, TimeUnit.SECONDS)
            .until(
                () ->
                    testEnvMgr.getLocalTigerProxyOrFail().getRbelMessages().stream().count()
                        == getReceivingTigerProxyMessages().stream().count());
      } catch (ConditionTimeoutException e) {
        log.error("/////////////////////////////////////////////////////////////////////////////");
        testEnvMgr.getLocalTigerProxyOrFail().getRbelMessagesList().stream()
            .map(el -> el.getUuid() + " -> " + el.printShortDescription())
            .forEach(log::info);
        log.error(
            "We sent {} message, intercepted {}, aggregating {}, receiving {}",
            round * MESSAGES_PER_ROUND * 2,
            testEnvMgr.getLocalTigerProxyOrFail().getRbelMessages().stream().count(),
            aggregatingProxyContext.getBean(TigerProxy.class).getRbelMessages().stream().count(),
            getReceivingTigerProxyMessages().stream().count());
        log.error("/////////////////////////////////////////////////////////////////////////////");
        final int toBeSkippedMessages =
            Math.max(0, testEnvMgr.getLocalTigerProxyOrFail().getRbelMessagesList().size() - 200);
        final List<RbelElement> sendingMsgs =
            getLastRequestPaths(
                testEnvMgr.getLocalTigerProxyOrFail().getRbelMessagesList(), toBeSkippedMessages);
        final List<RbelElement> aggregatingMsgs =
            getLastRequestPaths(
                aggregatingProxyContext.getBean(TigerProxy.class).getRbelMessagesList(),
                toBeSkippedMessages);
        final List<RbelElement> receivingMsgs =
            getLastRequestPaths(receivingProxy.getRbelMessagesList(), toBeSkippedMessages);
        log.error("////sending, aggregating receiving messages: ////");
        for (int i = 0; i < sendingMsgs.size(); i++) {
          if (makeReadable(sendingMsgs.get(i)).getLeft().contains("msg")) {
            var sent = softQueryList(sendingMsgs, i).map(this::makeReadable);
            var aggregated = softQueryList(aggregatingMsgs, i).map(this::makeReadable);
            var received = softQueryList(receivingMsgs, i).map(this::makeReadable);
            if (!(sent.isPresent()
                && aggregated.isPresent()
                && received.isPresent()
                && sent.get().getLeft().equals(aggregated.get().getLeft())
                && aggregated.get().getLeft().equals(received.get().getLeft()))) {
              log.error(
                  "{} {}, {} {}, {} {}",
                  sent.map(Pair::getLeft).orElse(EMPTY_MESSAGE_STRING),
                  sent.map(Pair::getRight).orElse(EMPTY_MESSAGE_STRING),
                  aggregated.map(Pair::getLeft).orElse(EMPTY_MESSAGE_STRING),
                  aggregated.map(Pair::getRight).orElse(EMPTY_MESSAGE_STRING),
                  received.map(Pair::getLeft).orElse(EMPTY_MESSAGE_STRING),
                  received.map(Pair::getRight).orElse(EMPTY_MESSAGE_STRING));
            }
          }
        }
        log.error("/////////////////////////////////////////////////////////////////////////////");
        fail(
            "Proxies did not transmit the messages correctly through the mesh setup. Inspect the"
                + " lines above to find out what happened!");
      }
    }
  }

  private Optional<RbelElement> softQueryList(List<RbelElement> list, int i) {
    if (list.size() < i + 1) {
      return Optional.empty();
    }
    return Optional.of(list.get(i));
  }

  private Pair<String, String> makeReadable(RbelElement message) {
    String downloadedMarker = "PUSH";
    if (message.hasFacet(TigerDownloadedMessageFacet.class)) {
      downloadedMarker = "DOWN";
    }
    final String httpRequestString =
        Optional.ofNullable(message)
            .map(msg -> msg.getRawStringContent().lines().findFirst())
            .filter(Optional::isPresent)
            .map(Optional::get)
            .stream()
            .map(str -> str.split(" "))
            .flatMap(ar -> Stream.of(ar).skip(1).limit(1))
            .filter(s -> s.startsWith("/"))
            .findFirst()
            .orElse("")
            .replace("/messageNumber", "msg");
    final String shortUuid =
        Optional.ofNullable(message).map(RbelElement::getUuid).orElse("").split("-")[0];
    return Pair.of(httpRequestString + " (" + shortUuid + ")", downloadedMarker);
  }

  @NotNull
  private List<RbelElement> getLastRequestPaths(List<RbelElement> rbelMessages, int skip) {
    return rbelMessages.stream().skip(skip).collect(Collectors.toList());
  }

  private void randomlyRebootAggregatingProxy(int aggregatingAdminPort) {
    if (aggregatingProxyContext == null && RandomUtils.nextInt(0, 20) < 1) {
      bootTigerProxy(aggregatingAdminPort);
    }
  }

  private void randomlyCrashAggregatingProxy() {
    if (aggregatingProxyContext != null && RandomUtils.nextInt(0, 30) < 1) {
      log.info("Stopping aggregating proxy...");
      aggregatingProxyContext.getBean(TigerProxy.class).close();
      aggregatingProxyContext.stop();
      aggregatingProxyContext.close();
      aggregatingProxyContext = null;
    }
  }

  private Deque<RbelElement> getReceivingTigerProxyMessages() {
    return receivingProxy.getRbelLogger().getMessageHistory();
  }

  private void bootTigerProxy(int aggregatingAdminPort) {
    log.info("Waiting for port {} to be available...", aggregatingAdminPort);
    await()
        .until(
            () -> {
              try (ServerSocket ignored = new ServerSocket(aggregatingAdminPort)) {
                return true;
              } catch (Exception e) {
                return false;
              }
            });

    if (receivingProxy != null) {
      log.info(
          "About to reboot aggregating proxy, currently known messages in receivingProxy: {}",
          receivingProxy.getRbelMessagesList().stream()
              .map(msg -> msg.getUuid() + " (" + msg.printShortDescription() + ")")
              .collect(Collectors.joining("\n")));
    }

    log.info("Starting Aggregating Proxy...");
    CfgStandaloneProxy standaloneCfg = new CfgStandaloneProxy();
    standaloneCfg.setTigerProxy(
        TigerProxyConfiguration.builder()
            .adminPort(aggregatingAdminPort)
            .trafficEndpoints(List.of(resolvePlaceholders("http://localhost:${free.port.12}")))
            .downloadInitialTrafficFromEndpoints(true)
            .rbelBufferSizeInMb(100)
            .activateRbelParsing(false)
            .connectionTimeoutInSeconds(100)
            .name("Aggregating proxy")
            .build());

    aggregatingProxyContext =
        new SpringApplicationBuilder(TigerProxyApplication.class)
            .bannerMode(Mode.OFF)
            .properties(new HashMap<>(TigerSerializationUtil.toMap(standaloneCfg)))
            .web(WebApplicationType.SERVLET)
            .run();

    log.info("Started Aggregating Proxy");
  }
}
