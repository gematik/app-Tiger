package de.gematik.test.tiger.common.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@AllArgsConstructor
@Builder
@Data
public class TigerConfigurationSource {
    public static final int SYSTEM_YAML_ORDER = 90;
    public static final int SYSTEM_ENV_ORDER = 80;
    public static final int SYSTEM_PROPERTIES_ORDER = 70;
    public static final int SYSTEM_CLI_ORDER = 60;
    public static final int SYSTEM_RUNTIME_EXPORT_ORDER = 50;

    private final List<TigerConfigurationKeyString> basePath;
    private final Map<TigerConfigurationKey, String> values;
    private final int order;

    public Map<TigerConfigurationKey, String> applyTemplatesAndAddValuesToMap(List<TigerTemplateSource> loadedTemplates,
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
}
