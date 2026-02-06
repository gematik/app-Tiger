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
package de.gematik.test.tiger;

import com.google.common.annotations.VisibleForTesting;
import de.gematik.rbellogger.RbelMessageHistory;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.util.IRbelMessageListener;
import de.gematik.rbellogger.util.RbelMessagesSupplier;
import de.gematik.test.tiger.lib.TigerDirector;
import de.gematik.test.tiger.lib.rbel.MockHistoryFacade;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
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
public class LocalProxyRbelMessageListener implements IRbelMessageListener {

  private final RbelMessagesSupplier messagesSupplier;
  private RbelElement lastDeletedElement = null;

  /**
   * list of messages received from local Tiger Proxy and used to create the RBelLog HTML page and
   * SerenityBDD test report evidence. This list is internal and not accessible to validation steps
   * or Tiger users
   */
  private final List<RbelElement> rbelMessages = new ArrayList<>();

  /** list of messages received from local Tiger Proxy per step, to be forwarded to workflow UI */
  private final List<RbelElement> stepRbelMessages =
      Collections.synchronizedList(new ArrayList<>());

  public List<RbelElement> getStepRbelMessages() {
    synchronized (stepRbelMessages) {
      return List.copyOf(stepRbelMessages);
    }
  }

  public void removeStepRbelMessages(List<RbelElement> elements) {
    stepRbelMessages.removeAll(elements);
  }

  private static LocalProxyRbelMessageListener instance;

  public static synchronized LocalProxyRbelMessageListener getInstance() {
    initialize();
    return instance;
  }

  public static void initialize() {
    if (instance == null) {
      instance = new LocalProxyRbelMessageListener();
    }
  }

  @VisibleForTesting
  public static void setTestingInstance(LocalProxyRbelMessageListener instanceForTesting) {
    LocalProxyRbelMessageListener.instance = instanceForTesting;
  }

  @VisibleForTesting
  public static void clearTestingInstance() {
    LocalProxyRbelMessageListener.instance = null;
  }

  public LocalProxyRbelMessageListener(RbelMessagesSupplier messagesSupplier) {
    this.messagesSupplier = messagesSupplier;
    messagesSupplier.addRbelMessageListener(this);
  }

  public LocalProxyRbelMessageListener() {
    this(
        TigerDirector.getTigerTestEnvMgr()
            .getLocalTigerProxyOptional()
            .map(RbelMessagesSupplier.class::cast)
            .orElse(new DoNothingSupplier()));
  }

  @Override
  public void triggerNewReceivedMessage(RbelElement e) {
    rbelMessages.add(e);
    stepRbelMessages.add(e);
  }

  public void clearMessages() {
    rbelMessages.clear();
  }

  public synchronized void clearAllMessages() {
    clearValidatableRbelMessages();
    clearMessages();
    stepRbelMessages.clear();
  }

  public List<RbelElement> getMessages() {
    return Collections.unmodifiableList(rbelMessages);
  }

  /** clears the validatable messages list* */
  public void clearValidatableRbelMessages() {
    // we dont actually delete anything, we just remember the last element
    // when this method is called.
    var messageHistory = messagesSupplier.getMessageHistory();
    if (messageHistory.isEmpty()) {
      lastDeletedElement = null;
    } else {
      lastDeletedElement = messageHistory.getLast();
    }
  }

  /**
   * Map of sequence numbers to messages received via local Tiger Proxy. It is used by the TGR
   * validation steps. The list is not cleared at the end of / start of new scenarios!
   */
  public RbelMessageHistory.Facade getValidatableMessages() {
    // we make a new unmodifiable list that is read directly from the tiger proxy messageHistory
    // but without the elements that should habe been deleted by the clearValidatableRbelMessages()
    // call.
    return Optional.ofNullable(lastDeletedElement)
        .map(
            element ->
                new MockHistoryFacade(
                    messagesSupplier.getMessageHistory().getMessagesAfter(element, false).stream()
                        .collect(
                            Collectors.toMap(
                                e -> e.getSequenceNumber().get(),
                                e -> e,
                                (e1, e2) -> e1,
                                TreeMap::new))))
        .map(RbelMessageHistory.Facade.class::cast)
        .orElseGet(messagesSupplier::getMessageHistory);
  }
}
