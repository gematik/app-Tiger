/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.config;

import lombok.Builder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Stores a map of key/value-pairs.
 */
public class BasicTigerConfigurationSource extends AbstractTigerConfigurationSource {

    private final Map<TigerConfigurationKey, String> values;

    @Builder
    public BasicTigerConfigurationSource(SourceType sourceType, TigerConfigurationKey basePath,
        Map<TigerConfigurationKey, String> values) {
        super(sourceType, basePath);
        this.values = values;
    }

    public BasicTigerConfigurationSource(SourceType sourceType) {
        super(sourceType);
        this.values = new HashMap<>();
    }

    public Map<TigerConfigurationKey, String> applyTemplatesAndAddValuesToMap(
        List<TigerTemplateSource> loadedTemplates,
        Map<TigerConfigurationKey, String> loadedAndSortedProperties) {
        Map<TigerConfigurationKey, String> finalValues = new HashMap<>();

        finalValues.putAll(loadedAndSortedProperties);
        finalValues.putAll(values);

        final List<TigerConfigurationKey> appliedTemplates = loadedTemplates.stream()
            .map(template -> template.applyToAllApplicable(this, finalValues))
            .flatMap(List::stream)
            .collect(Collectors.toList());
        appliedTemplates.forEach(key -> finalValues.remove(key));

        return finalValues;
    }

    @Override
    public Map<TigerConfigurationKey, String> getValues() {
        return values;
    }

    @Override
    public void putValue(TigerConfigurationKey key, String value) {
        values.put(key, value);
    }

    @Override
    public void removeValue(TigerConfigurationKey key) {
        values.remove(key);
    }
}
