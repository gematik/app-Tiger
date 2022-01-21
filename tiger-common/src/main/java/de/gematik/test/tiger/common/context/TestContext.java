/*
 * Copyright (c) 2022 gematik GmbH
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

package de.gematik.test.tiger.common.context;

import java.util.HashMap;
import java.util.Map;

public class TestContext extends ThreadSafeDomainContextProvider {

    public TestContext() {

    }

    public TestContext(final String domain) {
        this.domain = domain;
    }

    private static final Map<String, Map<String, Object>> threadedContexts = new HashMap<>();

    @Override
    public Map<String, Object> getContext() {
        return threadedContexts.computeIfAbsent(getId(), threadid -> new HashMap<>());
    }

    @Override
    public Map<String, Object> getContext(final String otherDomain) {
        return threadedContexts.computeIfAbsent(getId(otherDomain), threadid -> new HashMap<>());
    }
}
