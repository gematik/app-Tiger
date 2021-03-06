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

package de.gematik.test.tiger.common.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;

/**
 * Configuration-source which is bound to a certain thread. The configuration will be invisible from outside the thread
 * in which it has been set. Bear in mind that any unintentional threading (Thread-Pools etc.) will also have the same
 * effect.
 */
public class TigerThreadScopedConfigurationSource extends AbstractTigerConfigurationSource {

    private final Map<Long, Map<TigerConfigurationKey, String>> threadIdToValuesMap;

    public TigerThreadScopedConfigurationSource() {
        super(SourceType.THREAD_CONTEXT);
        this.threadIdToValuesMap = new ConcurrentHashMap<>();
    }

    public Map<TigerConfigurationKey, String> applyTemplatesAndAddValuesToMap(
        List<TigerTemplateSource> loadedTemplates,
        Map<TigerConfigurationKey, String> loadedAndSortedProperties) {
        Map<TigerConfigurationKey, String> finalValues = new HashMap<>();

        finalValues.putAll(loadedAndSortedProperties);
        finalValues.putAll(getValues());

        final List<List<TigerConfigurationKeyString>> appliedTemplates = loadedTemplates.stream()
            .map(template -> template.applyToAllApplicable(this, finalValues))
            .flatMap(List::stream)
            .collect(Collectors.toList());
        appliedTemplates.forEach(key -> finalValues.remove(key));

        return finalValues;
    }

    @Override
    public Map<TigerConfigurationKey, String> getValues() {
        final long threadId = Thread.currentThread().getId();
        if (threadIdToValuesMap.containsKey(threadId)) {
            return threadIdToValuesMap.get(threadId);
        } else {
            return Map.of();
        }
    }

    @Override
    public void putValue(TigerConfigurationKey key, String value) {
        final long threadId = Thread.currentThread().getId();
        synchronized (threadIdToValuesMap) {
            if (!threadIdToValuesMap.containsKey(threadId)) {
                threadIdToValuesMap.put(threadId, new ConcurrentHashMap<>());
            }

            threadIdToValuesMap
                .get(threadId)
                .put(key, value);
        }
    }
}
