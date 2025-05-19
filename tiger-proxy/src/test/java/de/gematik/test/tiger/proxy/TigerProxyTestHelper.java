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

import de.gematik.test.tiger.proxy.client.TigerRemoteProxyClient;
import java.text.MessageFormat;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.core.ConditionTimeoutException;

@Slf4j
public class TigerProxyTestHelper {

  public static final int DEFAULT_TIMEOUT_SEC = 20;

  public static void waitUntilMessageListInProxyContainsCountMessages(
      TigerProxy tigerProxy, int expectedMessagesCount) {
    try {
      await()
          .atMost(DEFAULT_TIMEOUT_SEC, TimeUnit.SECONDS)
          .pollDelay(200, TimeUnit.MILLISECONDS)
          .until(
              () ->
                  tigerProxy.getRbelMessages().stream()
                          .filter(msg -> msg.getConversionPhase().isFinished())
                          .count()
                      == expectedMessagesCount);
    } catch (ConditionTimeoutException cte) {
      log.error(
          "Expected {} message(s) in rbel message list but found {}",
          expectedMessagesCount,
          tigerProxy.getRbelMessagesList().size());
      tigerProxy.getRbelMessagesList().forEach(msg -> log.error(msg.printHttpDescription()));
      throw cte;
    }
  }

  public static void waitUntilMessageListInProxyContainsCountMessagesWithTimeout(
      TigerProxy tigerProxy, int expectedMessagesCount, int timeoutSec) {
    try {
      await()
          .atMost(timeoutSec, TimeUnit.SECONDS)
          .pollDelay(200, TimeUnit.MILLISECONDS)
          .until(() -> tigerProxy.getRbelMessagesList().size() == expectedMessagesCount);
    } catch (ConditionTimeoutException cte) {
      log.error(
          "Expected {} message(s) in rbel message list but found {}",
          expectedMessagesCount,
          tigerProxy.getRbelMessagesList().size());
      tigerProxy
          .getRbelLogger()
          .getMessageHistory()
          .forEach(msg -> log.error(msg.printTreeStructure()));
      throw cte;
    }
  }

  public static void waitUntilMessageListInRemoteProxyClientContainsCountMessagesWithTimeout(
      TigerRemoteProxyClient tigerRemoteProxyClient, int expectedMessagesCount, int timeoutSec) {
    try {
      await()
          .atMost(timeoutSec, TimeUnit.SECONDS)
          .pollDelay(200, TimeUnit.MILLISECONDS)
          .until(
              () -> tigerRemoteProxyClient.getRbelMessagesList().size() == expectedMessagesCount);
    } catch (ConditionTimeoutException cte) {
      log.error(
          "Expected {} message(s) in rbel message list but found {}",
          expectedMessagesCount,
          tigerRemoteProxyClient.getRbelMessagesList().size());
      tigerRemoteProxyClient
          .getRbelMessagesList()
          .forEach(
              msg ->
                  log.error(
                      msg.printHttpDescription()
                          + " with facets "
                          + msg.getFacets().stream()
                              .map(o -> o.getClass().getSimpleName())
                              .toList()));
      throw new RuntimeException(
          MessageFormat.format(
              "Expected {0} message(s) in rbel message list but found {1}",
              expectedMessagesCount, tigerRemoteProxyClient.getRbelMessagesList().size()),
          cte);
    }
  }
}
