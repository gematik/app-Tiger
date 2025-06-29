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
package de.gematik.test.tiger.proxy;

import static de.gematik.rbellogger.data.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.facets.timing.RbelMessageTimingFacet;
import de.gematik.test.tiger.common.data.config.tigerproxy.*;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import kong.unirest.core.HttpResponse;
import kong.unirest.core.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@Slf4j
@TestInstance(Lifecycle.PER_CLASS)
@ResetTigerConfiguration
class TestTigerProxyConcurrency extends AbstractTigerProxyTest {

  @Test
  void doALotOfRequests_OrderOfResponsesShouldMatchExactly() {
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerConfigurationRoute.builder()
                        .from("http://foo.bar")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .activateTrafficLogging(false)
            .build());
    List<String> requests = new ArrayList<>();

    final int iterations = 10;
    final int iterationCycles = 100;
    for (int i = 0; i < iterations; i++) {
      log.info("Iteration {}", i);
      for (int j = 0; j < iterationCycles; j++) {
        String id = "" + (i * iterationCycles + j);
        proxyRest.get("http://foo.bar/foobar?foo=" + id).asString();
        requests.add(id);
      }
      awaitMessagesInTigerProxy(iterationCycles * 2);

      for (int j = 0; j < iterationCycles; j++) {
        var id = requests.get(j);
        assertThat(tigerProxy.getRbelMessagesList().get(j * 2))
            .extractChildWithPath("$.path.foo")
            .hasStringContentEqualTo("foo=" + id);
        assertThat(tigerProxy.getRbelMessagesList().get(j * 2 + 1))
            .extractChildWithPath("$.header.fooValue")
            .hasStringContentEqualTo(id);
      }
      tigerProxy.clearAllMessages();
      requests.clear();
    }
  }

  @Test
  void reverseProxy_parsingShouldNotBlockCommunication() {
    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());
    AtomicBoolean messageWasReceivedInTheClient = new AtomicBoolean(false);
    AtomicBoolean allowMessageParsingToComplete = new AtomicBoolean(false);

    final RbelConverterPlugin blockConversionUntilCommunicationIsComplete =
        RbelConverterPlugin.createPlugin(
            (rbelElement, converter) -> {
              log.info("Entering wait");
              await()
                  .pollInterval(1, TimeUnit.MILLISECONDS)
                  .atMost(2, TimeUnit.SECONDS)
                  .until(messageWasReceivedInTheClient::get);
              allowMessageParsingToComplete.set(true);
              log.info("Exiting wait");
            });
    tigerProxy
        .getRbelLogger()
        .getRbelConverter()
        .addConverter(blockConversionUntilCommunicationIsComplete);

    Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar").asString();

    log.info("Message was received in the client...");
    messageWasReceivedInTheClient.set(true);

    await()
        .pollInterval(1, TimeUnit.MILLISECONDS)
        .atMost(2, TimeUnit.SECONDS)
        .until(allowMessageParsingToComplete::get);
  }

  @Test
  @SuppressWarnings("java:S2925")
  void reverseProxy_parsingShouldBlockCommunicationIfConfigured() {
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerConfigurationRoute.builder()
                        .from("/")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .parsingShouldBlockCommunication(true)
            .build());
    AtomicBoolean clientHasWaitedAndNotReceivedMessageYet = new AtomicBoolean(false);
    AtomicBoolean messageParsingHasStarted = new AtomicBoolean(false);

    final RbelConverterPlugin blockConversionUntilCommunicationIsComplete =
        RbelConverterPlugin.createPlugin(
            (el, converter) -> {
              log.info("Entering wait with " + el.getRawStringContent());
              messageParsingHasStarted.set(true);
              await()
                  .atMost(20, TimeUnit.SECONDS)
                  .until(clientHasWaitedAndNotReceivedMessageYet::get);
              log.info("Exiting wait");
            });
    tigerProxy
        .getRbelLogger()
        .getRbelConverter()
        .addConverter(blockConversionUntilCommunicationIsComplete);

    final CompletableFuture<HttpResponse<String>> asyncMessage =
        Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar").asStringAsync();
    await().atMost(20, TimeUnit.SECONDS).until(messageParsingHasStarted::get);

    assertThat(tigerProxy.getRbelMessagesList()).isEmpty();
    Assertions.assertThat(asyncMessage.isDone()).isFalse();

    log.info("Switching clientHasWaitedAndNotReceivedMessageYet...");
    clientHasWaitedAndNotReceivedMessageYet.set(true);
    await().atMost(20, TimeUnit.SECONDS).until(asyncMessage::isDone);
  }

  @Test
  @SuppressWarnings("java:S2925")
  void timestampOfTheMessagesShouldBeBeforeParsing() throws InterruptedException {
    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());
    CompletableFuture<Void> messageWasReceivedInTheClient = new CompletableFuture<>();

    AtomicReference<ZonedDateTime> responseLatestTimestamp = new AtomicReference<>();
    AtomicReference<ZonedDateTime> requestLatestTimestamp = new AtomicReference<>();

    final RbelConverterPlugin blockConversionUntilCommunicationIsComplete =
        RbelConverterPlugin.createPlugin(
            (el, converter) -> {
              if (el.getRawStringContent().contains("HTTP/1.1 666 EVIL")) {
                responseLatestTimestamp.set(ZonedDateTime.now());
              } else {
                requestLatestTimestamp.set(ZonedDateTime.now());
              }
              log.info("Entering wait");
              messageWasReceivedInTheClient.join();
              log.info("Exiting wait");
            });
    tigerProxy
        .getRbelLogger()
        .getRbelConverter()
        .addConverter(blockConversionUntilCommunicationIsComplete);

    Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar").asString();

    log.info("Message was received in the client...");
    // mess up the timestamps (by delaying the parsing)
    Thread.sleep(100);
    messageWasReceivedInTheClient.complete(null);

    awaitMessagesInTigerProxy(2);

    assertThat(tigerProxy.getRbelMessagesList()).hasSize(2);
    assertThat(
            tigerProxy
                .getRbelMessagesList()
                .get(0)
                .getFacet(RbelMessageTimingFacet.class)
                .get()
                .getTransmissionTime())
        .isBefore(requestLatestTimestamp.get());
    assertThat(
            tigerProxy
                .getRbelMessagesList()
                .get(1)
                .getFacet(RbelMessageTimingFacet.class)
                .get()
                .getTransmissionTime())
        .isBefore(responseLatestTimestamp.get());
  }
}
