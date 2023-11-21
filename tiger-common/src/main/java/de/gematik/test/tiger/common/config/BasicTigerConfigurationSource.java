/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.EqualsAndHashCode;

/** Stores a map of key/value-pairs. */
@EqualsAndHashCode(callSuper = true)
public class BasicTigerConfigurationSource extends AbstractTigerConfigurationSource {

  private final Map<TigerConfigurationKey, String> values;

  @Builder
  public BasicTigerConfigurationSource(
      SourceType sourceType,
      TigerConfigurationKey basePath,
      Map<TigerConfigurationKey, String> values) {
    super(sourceType, basePath);
    this.values = values;
  }

  public BasicTigerConfigurationSource(SourceType sourceType) {
    super(sourceType);
    this.values = new HashMap<>();
  }

  public synchronized Map<TigerConfigurationKey, String> applyTemplatesAndAddValuesToMap(
      List<TigerTemplateSource> loadedTemplates,
      Map<TigerConfigurationKey, String> loadedAndSortedProperties) {
    Map<TigerConfigurationKey, String> finalValues = new HashMap<>();

    finalValues.putAll(loadedAndSortedProperties);
    finalValues.putAll(values);

    final List<TigerConfigurationKey> appliedTemplates =
        loadedTemplates.stream()
            .map(template -> template.applyToAllApplicable(this, finalValues))
            .flatMap(List::stream)
            .toList();
    appliedTemplates.forEach(finalValues::remove);

    return finalValues;
  }

  @Override
  public synchronized Map<TigerConfigurationKey, String> getValues() {
    return Collections.unmodifiableMap(values);
  }

  @Override
  public synchronized void putValue(TigerConfigurationKey key, String value) {
    values.put(key, value);
  }

  @Override
  public synchronized void removeValue(TigerConfigurationKey key) {
    values.remove(key);
  }

  @Override
  public synchronized boolean containsKey(TigerConfigurationKey key) {
    return values.containsKey(key);
  }

  @Override
  public synchronized String getValue(TigerConfigurationKey key) {
    return values.get(key);
  }
}
