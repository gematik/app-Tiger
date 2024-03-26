/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.config;

import java.util.List;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/** Base class that stores key/value-pairs from a source. */
@Getter
@EqualsAndHashCode
public abstract class AbstractTigerConfigurationSource
    implements Comparable<AbstractTigerConfigurationSource> {

  protected final SourceType sourceType;

  AbstractTigerConfigurationSource(SourceType sourceType) {
    this.sourceType = sourceType;
  }

  public abstract AbstractTigerConfigurationSource copy();

  public abstract Map<TigerConfigurationKey, String> applyTemplatesAndAddValuesToMap(
      List<TigerTemplateSource> loadedTemplates,
      Map<TigerConfigurationKey, String> loadedAndSortedProperties);

  public abstract Map<TigerConfigurationKey, String> getValues();

  public abstract void putValue(TigerConfigurationKey key, String value);

  public abstract void removeValue(TigerConfigurationKey key);

  public abstract boolean containsKey(TigerConfigurationKey key);

  public abstract String getValue(TigerConfigurationKey key);

  @Override
  public int compareTo(AbstractTigerConfigurationSource other) {
    if (other == null) {
      throw new NullPointerException();
    }
    return Integer.compare(sourceType.getPrecedence(), other.getSourceType().getPrecedence());
  }

  public abstract void putAll(AbstractTigerConfigurationSource other);
}
