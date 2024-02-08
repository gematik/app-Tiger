/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import static org.awaitility.Awaitility.await;

import de.gematik.rbellogger.data.facet.RbelParsingNotCompleteFacet;
import de.gematik.test.tiger.proxy.client.TigerRemoteProxyClient;
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
                          .filter(msg -> !msg.hasFacet(RbelParsingNotCompleteFacet.class))
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
      tigerProxy.getRbelMessagesList().forEach(msg -> log.error(msg.printHttpDescription()));
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
          .forEach(msg -> log.error(msg.printHttpDescription()));
      throw cte;
    }
  }
}
