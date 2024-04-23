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
