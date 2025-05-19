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

import static org.awaitility.Awaitility.await;

import de.gematik.rbellogger.RbelConversionPhase;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.test.tiger.common.data.config.tigerproxy.*;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@Slf4j
@TestInstance(Lifecycle.PER_CLASS)
@ResetTigerConfiguration
class Pop3PerformanceTest extends AbstractTigerProxyTest {

  @SneakyThrows
  @Test
  void forwardProxy_shouldUseSameTcpConnection() {
    log.info("Start test");
    spawnTigerProxyWithDefaultRoutesAndWith(
        new TigerProxyConfiguration().setActivateRbelParsingFor(List.of("pop3", "smtp", "mime")));
    log.info("TigerProxy started");
    val localTigerProxy =
        new TigerProxy(
            new TigerProxyConfiguration()
                .setActivateRbelParsingFor(List.of("pop3", "smtp", "mime"))
                .setFileSaveInfo(
                    TigerFileSaveInfo.builder()
                        .sourceFile("src/test/resources/pop3Performance.tgr")
                        .build())
                .setTrafficEndpoints(List.of("localhost:" + tigerProxy.getAdminPort())));
    log.info("Local TigerProxy started");
    //    RbelFileReaderCapturer.builder()
    //        .rbelFile("src/test/resources/pop3Performance.tgr")
    //        .rbelConverter(tigerProxy.getRbelLogger().getRbelConverter())
    //        .build()
    //        .initialize();
    await()
        .atMost(5, TimeUnit.MINUTES)
        .pollInterval(1, TimeUnit.SECONDS)
        .until(
            () -> {
              localTigerProxy.waitForAllCurrentMessagesToBeParsed();
              log.info(
                  "Currently having {} messages locally",
                  localTigerProxy.getRbelLogger().getMessageHistory().size());
              return localTigerProxy.getRbelLogger().getMessageHistory().stream()
                      .filter(el -> el.getConversionPhase() == RbelConversionPhase.COMPLETED)
                      .count()
                  >= 104;
            });
    localTigerProxy.waitForAllCurrentMessagesToBeParsed();
    log.info(
        "All messages parsed, now having {} messages locally",
        localTigerProxy.getRbelLogger().getMessageHistory().size());
    Optional.of(localTigerProxy)
        .map(TigerProxy::getRbelLogger)
        .map(RbelLogger::getMessageHistory)
        .map(List::copyOf)
        .orElseGet(Collections::emptyList);
    //        .forEach(RbelConverter::waitUntilFullyProcessed);
    // TODO signal completion
    // element.removeFacetsOfType();
    log.info("All messages processed");
  }
}
