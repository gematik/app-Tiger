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

package de.gematik.test.tiger.common.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Builder;

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
}
