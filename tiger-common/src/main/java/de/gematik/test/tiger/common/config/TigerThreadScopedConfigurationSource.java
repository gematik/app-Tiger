package de.gematik.test.tiger.common.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;

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
