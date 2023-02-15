/*
 * Copyright (c) 2023 gematik GmbH
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

package de.gematik.test.tiger;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.test.tiger.lib.proxy.RbelMessageProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * This class links the local Tiger Proxy with the glue code / test suite.
 * <p>
 * Provides and Manages a NON thread safe RbelMessageProvider with three lists. One for reuse by Tiger validation steps,
 * one for messages being associated with the current test step, used by the Workflow UI and one internal for later usage.
 * <p>
 * <b>ATTENTION!</b> As of now Tiger does not support collecting Rbel messages in a "thread safe" way,
 * so that messages sent in parallel test execution scenarios are tracked. If you do run Tiger in parallel test execution, you must deal
 * with concurrency of RBel messages yourself.
 */
@SuppressWarnings("unused")
@Slf4j
public class LocalProxyRbelMessageListener {

    /**
     * List of messages received via local Tiger Proxy. You may clear/manipulate this list if you know what you do. It is used by the TGR
     * validation steps. The list is not cleared at the end of / start of new scenarios!
     * TODO add test to ensure this statement
     */
    @Getter
    public static final List<RbelElement> validatableRbelMessages = new ArrayList<>();

    /**
     * list of messages received from local Tiger Proxy and used to create the RBelLog HTML page and SerenityBDD test report evidence. This
     * list is internal and not accessible to validation steps or Tiger users
     */
    private static final List<RbelElement> rbelMessages = new ArrayList<>();

    /**
     * list of messages received from local Tiger Proxy per step, to be forwarded to workflow UI
     */
    private static final List<RbelElement> stepRbelMessages = new ArrayList<>();

    /**
     * simple implementation of the RBelMessageProvider collecting all messages in two separate lists.
     */
    public static final RbelMessageProvider rbelMessageListener = new RbelMessageProvider() {
        @Override
        public void triggerNewReceivedMessage(RbelElement e) {
            rbelMessages.add(e);
            validatableRbelMessages.add(e);
            stepRbelMessages.add(e);
        }
    };

    static void clearMessages() {
        rbelMessages.clear();
    }

    public static List<RbelElement> getMessages() {
        return Collections.unmodifiableList(rbelMessages);
    }

    public static List<RbelElement> getStepRbelMessages() {
        return stepRbelMessages;
    }
}


