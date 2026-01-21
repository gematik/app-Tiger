/*
 * Copyright 2021-2026 gematik GmbH
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
 * ******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 *
 */

package de.gematik.test.tiger.glue;

import static org.awaitility.Awaitility.await;

import de.gematik.test.tiger.LocalProxyRbelMessageListener;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.lib.rbel.RbelMessageRetriever;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import kong.unirest.core.Unirest;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.Test;

@Slf4j
class RBelValidatorGlueTest {

  @TigerTest(
      tigerYaml =
"""
config_ports:
    httpbin1:
        admin: ${free.port.0}
        proxy: ${free.port.1}
        serverPort:  ${free.port.2}
    httpbin2:
        admin: ${free.port.3}
        proxy: ${free.port.4}
        serverPort: ${free.port.5}
tigerProxy:
    trafficEndpoints:
        - http://localhost:${tiger.config_ports.httpbin1.admin}
        - http://localhost:${tiger.config_ports.httpbin2.admin}
    downloadInitialTrafficFromEndpoints: true
servers:
    httpbin1:
        type: httpbin
        serverPort: ${tiger.config_ports.httpbin1.serverPort}
        healthCheckUrl: http://localhost:${tiger.config_ports.httpbin1.serverPort}/status/200
    httpbin2:
        type: httpbin
        serverPort: ${tiger.config_ports.httpbin2.serverPort}
        healthCheckUrl: http://localhost:${tiger.config_ports.httpbin2.serverPort}/status/200
    remoteProxy1:
        type: tigerProxy
        tigerProxyConfiguration:
            adminPort: ${tiger.config_ports.httpbin1.admin}
            proxyPort: ${tiger.config_ports.httpbin1.proxy}
            directReverseProxy:
              hostname: localhost
              port: ${tiger.config_ports.httpbin1.serverPort}
    remoteProxy2:
        type: tigerProxy
        tigerProxyConfiguration:
            adminPort: ${tiger.config_ports.httpbin2.admin}
            proxyPort: ${tiger.config_ports.httpbin2.proxy}
            directReverseProxy:
              hostname: localhost
              port: ${tiger.config_ports.httpbin2.serverPort}
""")
  @Test
  void testFindMessagesForConnections(TigerTestEnvMgr tigerTestEnvMgr) {
    // We have two httpbin servers behind two tiger reverse proxies.
    // We send messages to the proxy port.
    // The reverse proxies, forward the messages to the httpbin server ports

    val httpbin1ProxyPort =
        Integer.parseInt(
            TigerGlobalConfiguration.resolvePlaceholders("${tiger.config_ports.httpbin1.proxy}"));
    val httpbin1ServerPort =
        Integer.parseInt(
            TigerGlobalConfiguration.resolvePlaceholders(
                "${tiger.config_ports.httpbin1.serverPort}"));
    val httpbin2ProxyPort =
        Integer.parseInt(
            TigerGlobalConfiguration.resolvePlaceholders("${tiger.config_ports.httpbin2.proxy}"));
    val httpbin2ServerPort =
        Integer.parseInt(
            TigerGlobalConfiguration.resolvePlaceholders(
                "${tiger.config_ports.httpbin2.serverPort}"));

    val rbelValidatorGlue =
        new RBelValidatorGlue(
            new RbelMessageRetriever(
                tigerTestEnvMgr,
                tigerTestEnvMgr.getLocalTigerProxyOrFail(),
                new LocalProxyRbelMessageListener(tigerTestEnvMgr.getLocalTigerProxyOrFail())));

    try (val unirest = Unirest.spawnInstance()) {
      // Send two messages to each
      var responseHttpBin1First =
          unirest
              .get("http://localhost:" + httpbin1ProxyPort + "/anything/toHttpBin1_first")
              .asString();
      var responseHttpBin2First =
          unirest
              .get("http://localhost:" + httpbin2ProxyPort + "/anything/toHttpBin2_first")
              .asString();
      var responseHttpBin1Second =
          unirest
              .get("http://localhost:" + httpbin1ProxyPort + "/anything/toHttpBin1_second")
              .asString();
      var responseHttpBin2Second =
          unirest
              .get("http://localhost:" + httpbin2ProxyPort + "/anything/toHttpBin2_second")
              .asString();
      Stream.of(
              responseHttpBin1First,
              responseHttpBin2First,
              responseHttpBin1Second,
              responseHttpBin2Second)
          .forEach(r -> log.info("Response from httpbin: {}", r.getStatus()));
      waitForMessages(tigerTestEnvMgr.getLocalTigerProxyOrFail(), 8);

      tigerTestEnvMgr
          .getLocalTigerProxyOrFail()
          .getRbelLogger()
          .getMessageHistory()
          .forEach(m -> log.info("Message {}: {}", m.getUuid(), m.printTreeStructure()));
      // Finding the messages for the first connection (to httpbin1)
      rbelValidatorGlue.findRequestToPathWithHostAndPort(
          "localhost", String.valueOf(httpbin1ServerPort));
      rbelValidatorGlue.currentRequestMessageAtMatchesDocString(
          "$.path", "/anything/toHttpBin1_first");
      rbelValidatorGlue.findNextMessageOnSameConnection();
      rbelValidatorGlue.currentRequestMessageAtMatchesDocString(
          "$.path", "/anything/toHttpBin1_second");

      // Finding the messages for the second connection (to httpbin2)
      rbelValidatorGlue.findRequestToPathWithHostAndPort(
          "localhost", String.valueOf(httpbin2ServerPort));
      rbelValidatorGlue.currentRequestMessageAtMatchesDocString(
          "$.path", "/anything/toHttpBin2_first");
      rbelValidatorGlue.findNextMessageOnSameConnection();
      rbelValidatorGlue.currentRequestMessageAtMatchesDocString(
          "$.path", "/anything/toHttpBin2_second");
    }
  }

  void waitForMessages(TigerProxy proxy, int expectedMessageCount) {
    try {
      await()
          .atMost(20, TimeUnit.SECONDS)
          .until(
              () ->
                  proxy.getRbelLogger().getMessageHistory().stream()
                          .filter(el -> el.getConversionPhase().isFinished())
                          .count()
                      >= expectedMessageCount);
    } catch (ConditionTimeoutException e) {
      log.error("Timed out waiting for tiger to receive {} messages", expectedMessageCount);
      proxy
          .getRbelLogger()
          .getMessageHistory()
          .forEach(
              el ->
                  log.error("Message {}: {}", el.getUuid(), el.printTreeStructureWithoutColors()));
      throw e;
    }
  }
}
