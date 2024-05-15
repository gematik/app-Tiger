/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.test.tiger.lib.TigerDirector;
import de.gematik.test.tiger.lib.proxy.RbelMessageProvider;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * This class links the local Tiger Proxy with the glue code / test suite.
 *
 * <p>Provides and Manages a NON thread safe RbelMessageProvider with three lists. One for reuse by
 * Tiger validation steps, one for messages being associated with the current test step, used by the
 * Workflow UI and one internal for later usage.
 *
 * <p><b>ATTENTION!</b> As of now Tiger does not support collecting Rbel messages in a "thread safe"
 * way, so that messages sent in parallel test execution scenarios are tracked. If you do run Tiger
 * in parallel test execution, you must deal with concurrency of RBel messages yourself.
 */
@SuppressWarnings("unused")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class LocalProxyRbelMessageListener {

  private static RbelElement lastDeletedElement = null;

  /**
   * list of messages received from local Tiger Proxy and used to create the RBelLog HTML page and
   * SerenityBDD test report evidence. This list is internal and not accessible to validation steps
   * or Tiger users
   */
  private static final List<RbelElement> rbelMessages = new ArrayList<>();

  /** list of messages received from local Tiger Proxy per step, to be forwarded to workflow UI */
  @Getter private static final List<RbelElement> stepRbelMessages = new ArrayList<>();

  /**
   * simple implementation of the RBelMessageProvider collecting all messages in two separate lists.
   */
  public static final RbelMessageProvider rbelMessageListener =
      new RbelMessageProvider() {
        @Override
        public void triggerNewReceivedMessage(RbelElement e) {
          rbelMessages.add(e);
          stepRbelMessages.add(e);
        }
      };

  public static void clearMessages() {
    rbelMessages.clear();
  }

  public static List<RbelElement> getMessages() {
    return Collections.unmodifiableList(rbelMessages);
  }

  /** clears the validatable messages list* */
  public static void clearValidatableRbelMessages() {
    // we dont actually delete anything, we just remember the last element
    // when this method is called.
    var messageHistory = getMessageHistoryFromTigerProxy();
    if (messageHistory.isEmpty()) {
      lastDeletedElement = null;
    } else {
      lastDeletedElement = messageHistory.getLast();
    }
  }

  /**
   * List of messages received via local Tiger Proxy. It is used by the TGR validation steps. The
   * list is not cleared at the end of / start of new scenarios!
   */
  public static Deque<RbelElement> getValidatableRbelMessages() {
    // we make a new unmodifiable list that is read directly from the tiger proxy messageHistory
    // but without the elements that should habe been deleted by the clearValidatableRbelMessages()
    // call.
    return getMessageHistoryFromTigerProxy().stream()
        .dropWhile(e -> lastDeletedElement != null && e != lastDeletedElement)
        .collect(Collectors.toCollection(ArrayDeque::new));
  }

  private static Deque<RbelElement> getMessageHistoryFromTigerProxy() {
    return TigerDirector.getTigerTestEnvMgr().getLocalTigerProxyOrFail().getRbelMessages();
  }
}
