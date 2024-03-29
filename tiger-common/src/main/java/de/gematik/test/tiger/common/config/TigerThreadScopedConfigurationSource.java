/*
 * Copyright (c) 2024 gematik GmbH
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
import java.util.function.Function;
import java.util.function.LongConsumer;
import lombok.EqualsAndHashCode;

/**
 * Configuration-source which is bound to a certain thread. The configuration will be invisible from
 * outside the thread in which it has been set. Bear in mind that any unintentional threading
 * (Thread-Pools etc.) will also have the same effect.
 */
@EqualsAndHashCode(callSuper = true)
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

    loadedTemplates.stream()
        .map(template -> template.applyToApplicablesAndReturnAppliedTemplateKeys(this, finalValues))
        .flatMap(List::stream)
        .forEach(finalValues::remove);

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
    executeWithCurrentThreadMap(threadId -> threadIdToValuesMap.get(threadId).put(key, value));
  }

  @Override
  public void removeValue(TigerConfigurationKey key) {
    executeWithCurrentThreadMap(threadId -> threadIdToValuesMap.get(threadId).remove(key));
  }

  @Override
  public boolean containsKey(TigerConfigurationKey key) {
    return retrieveFromCurrentThreadMap(m -> m.containsKey(key));
  }

  @Override
  public String getValue(TigerConfigurationKey key) {
    return retrieveFromCurrentThreadMap(m -> m.get(key));
  }

  private <T> T retrieveFromCurrentThreadMap(
      Function<Map<TigerConfigurationKey, String>, T> retriever) {
    final long threadId = Thread.currentThread().getId();
    synchronized (threadIdToValuesMap) {
      threadIdToValuesMap.computeIfAbsent(threadId, thid -> new ConcurrentHashMap<>());
      return retriever.apply(threadIdToValuesMap.get(threadId));
    }
  }

  private void executeWithCurrentThreadMap(LongConsumer threadIdConsumer) {
    final long threadId = Thread.currentThread().getId();
    synchronized (threadIdToValuesMap) {
      threadIdToValuesMap.computeIfAbsent(threadId, thid -> new ConcurrentHashMap<>());
      threadIdConsumer.accept(threadId);
    }
  }
}
