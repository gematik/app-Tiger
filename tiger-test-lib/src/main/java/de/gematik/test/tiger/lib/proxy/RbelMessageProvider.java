/*
 * Copyright (c) 2021 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

    // TODO how to make it safe for multithreaded test scenarios
    // use multiple proxies, one for each THREAD!
    private final List<RbelElement> messages = new ArrayList<>();

    private long timeoutms = 5000;

    private boolean wait = false;

    @Override
    public void triggerNewReceivedMessage(RbelElement el) {
        messages.add(el);
        wait = false;
    }

    public void waitForMessage() {
        wait = true;
        try  {
            await().atMost(timeoutms, TimeUnit.MILLISECONDS).pollDelay(100, TimeUnit.MILLISECONDS)
                .until(() -> !wait);
        } catch (ConditionTimeoutException cte) {
            throw new TigerLibraryException("Timeout waiting for rbel message", cte);
        }
    }

    public RbelElement pullMessage() {
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

    public void startStep() {
        messages.clear();
        wait = false;
    }

    public void setTimeoutms(long timeout) {
        timeoutms = timeout;
    }
}
