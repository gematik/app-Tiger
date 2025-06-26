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
package de.gematik.test.tiger.lib.proxy;

import static org.awaitility.Awaitility.await;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.util.IRbelMessageListener;
import de.gematik.test.tiger.lib.TigerLibraryException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.core.ConditionTimeoutException;

@Deprecated(since = "3.0.6", forRemoval = true)
@Slf4j
public class RbelMessageProvider implements IRbelMessageListener {

  private final List<RbelElement> messages = new ArrayList<>();

  @Setter private long timeoutms = 5000;

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
}
