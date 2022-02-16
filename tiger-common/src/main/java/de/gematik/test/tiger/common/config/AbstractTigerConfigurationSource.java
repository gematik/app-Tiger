package de.gematik.test.tiger.common.config;

import java.util.List;
import java.util.Map;
import lombok.Getter;

@Getter
public abstract class AbstractTigerConfigurationSource {

    private final TigerConfigurationKey basePath;
    private final SourceType sourceType;

    public AbstractTigerConfigurationSource(SourceType sourceType) {
        this.sourceType = sourceType;
        this.basePath = new TigerConfigurationKey();
    }

    public AbstractTigerConfigurationSource(SourceType sourceType, TigerConfigurationKey basePath) {
        this.sourceType = sourceType;
        this.basePath = basePath;
    }

    public abstract Map<TigerConfigurationKey, String> applyTemplatesAndAddValuesToMap(
        List<TigerTemplateSource> loadedTemplates,
        Map<TigerConfigurationKey, String> loadedAndSortedProperties);

    public abstract Map<TigerConfigurationKey, String> getValues();

    public abstract void putValue(TigerConfigurationKey key, String value);
}
