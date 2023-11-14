/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.proxy;

import static org.awaitility.Awaitility.await;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.test.tiger.lib.TigerLibraryException;
import de.gematik.test.tiger.proxy.IRbelMessageListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.core.ConditionTimeoutException;

@Slf4j
public class RbelMessageProvider implements IRbelMessageListener {

  private final List<RbelElement> messages = new ArrayList<>();

  private long timeoutms = 5000;

  private boolean wait = false;

  @Override
  public void triggerNewReceivedMessage(RbelElement el) {
    messages.add(el);
    wait = false;
  }

  public synchronized void waitForMessage() {
    wait = true;
    try {
      await()
          .atMost(timeoutms, TimeUnit.MILLISECONDS)
          .pollDelay(100, TimeUnit.MILLISECONDS)
          .until(() -> !wait);
    } catch (ConditionTimeoutException cte) {
      throw new TigerLibraryException("Timeout waiting for rbel message", cte);
    }
  }

  public synchronized RbelElement pullMessage() {
    if (messages.isEmpty()) {
      waitForMessage();
    }
    RbelElement el = messages.get(0);
    messages.remove(0);
    return el;
  }

  public List<RbelElement> getMessages() {
    return Collections.unmodifiableList(messages);
  }

  public void clearMessageQueue() {
    messages.clear();
    wait = false;
  }

  public void setTimeoutms(long timeout) {
    timeoutms = timeout;
  }
}
