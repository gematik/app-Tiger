/*
 * Copyright 2024 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.common.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.EqualsAndHashCode;

/** Stores a map of key/value-pairs. */
@EqualsAndHashCode(callSuper = true)
public class BasicTigerConfigurationSource extends AbstractTigerConfigurationSource {

  private final Map<TigerConfigurationKey, String> values;

  @Builder
  public BasicTigerConfigurationSource(
      ConfigurationValuePrecedence precedence, Map<TigerConfigurationKey, String> values) {
    super(precedence);
    this.values = new HashMap<>(values);
  }

  public BasicTigerConfigurationSource(ConfigurationValuePrecedence precedence) {
    super(precedence);
    this.values = new HashMap<>();
  }

  @Override
  public AbstractTigerConfigurationSource copy() {
    return BasicTigerConfigurationSource.builder()
        .precedence(precedence)
        .values(new HashMap<>(values))
        .build();
  }

  /**
   * merges all properties of this source with loadedAndSortedProperties
   *
   * @param loadedAndSortedProperties current set of tiger properties
   * @return a new merged map of properties
   */
  public synchronized Map<TigerConfigurationKey, String> addValuesToMap(
      Map<TigerConfigurationKey, String> loadedAndSortedProperties) {
    Map<TigerConfigurationKey, String> finalValues = new HashMap<>();

    finalValues.putAll(loadedAndSortedProperties);
    finalValues.putAll(values);

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

  @Override
  public void putAll(AbstractTigerConfigurationSource other) {
    if (other instanceof BasicTigerConfigurationSource otherBasicSource) {
      values.putAll(otherBasicSource.values);
    } else {
      throw new IllegalArgumentException("Cannot merge different source types");
    }
  }
}
