/*
 *
 * Copyright 2021-2025 gematik GmbH
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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.test.tiger.common.config;

import static de.gematik.test.tiger.common.config.TigerConfigurationKeyString.wrapAsKey;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BinaryNode;
import java.util.*;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.val;
import org.apache.commons.lang3.ClassUtils;

/** Stores a map of key/value-pairs. */
@Getter
@EqualsAndHashCode
public class TigerConfigurationSource implements Comparable<TigerConfigurationSource> {

  protected final ConfigurationValuePrecedence precedence;
  private final Map<TigerConfigurationKey, String> values;
  private final TigerConfigurationLoader configurationLoader;

  @Builder
  public TigerConfigurationSource(
      ConfigurationValuePrecedence precedence,
      Map<TigerConfigurationKey, String> values,
      TigerConfigurationLoader configurationLoader) {
    this.precedence = precedence;
    this.configurationLoader = configurationLoader;
    this.values = new HashMap<>();
    if (values != null) {
      this.values.putAll(values);
    }
  }

  public TigerConfigurationSource(
      ConfigurationValuePrecedence precedence, TigerConfigurationLoader configurationLoader) {
    this.precedence = precedence;
    this.values = new HashMap<>();
    this.configurationLoader = configurationLoader;
  }

  public TigerConfigurationSource copy() {
    return TigerConfigurationSource.builder()
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

  public synchronized Map<TigerConfigurationKey, String> getValues() {
    return Collections.unmodifiableMap(values);
  }

  public synchronized void putValue(TigerConfigurationKey baseKey, Object value) {
    if (value == null) {
      return;
    }

    if (value instanceof Map<?, ?> asMap) {
      asMap.forEach(
          (subKey, entryValue) -> {
            var combinedKey = new TigerConfigurationKey(baseKey);
            combinedKey.add(subKey.toString());
            putValue(combinedKey, entryValue);
          });
    } else if (value instanceof List<?> asList) {
      int counter = 0;
      for (Object entry : asList) {
        TigerConfigurationKey newList = new TigerConfigurationKey(baseKey);
        newList.add(wrapAsKey(Integer.toString(counter++)));
        putValue(newList, entry);
      }
    } else if (ClassUtils.isPrimitiveOrWrapper(value.getClass())
        || value instanceof String
        || value instanceof Enum<?>) {
      values.put(baseKey, value.toString());
    } else if (value instanceof JsonNode jsonNode) {
      if (jsonNode.isObject()) {
        for (val field : jsonNode.properties()) {
          putValue(new TigerConfigurationKey(baseKey, field.getKey()), field.getValue());
        }
      } else if (jsonNode.isArray()) {
        for (int i = 0; i < jsonNode.size(); i++) {
          putValue(new TigerConfigurationKey(baseKey, Integer.toString(i)), jsonNode.get(i));
        }
      } else if (jsonNode instanceof BinaryNode binaryNode) {
        putValue(
            new TigerConfigurationKey(baseKey),
            Base64.getEncoder().encodeToString(binaryNode.binaryValue()));
      } else if (jsonNode.isTextual()) {
        putValue(new TigerConfigurationKey(baseKey), jsonNode.textValue());
      } else if (jsonNode.isValueNode() && !jsonNode.isNull()) {
        putValue(new TigerConfigurationKey(baseKey), jsonNode.toString());
      }
    } else {
      val treeView = configurationLoader.getObjectMapper().valueToTree(value);
      putValue(new TigerConfigurationKey(baseKey), treeView);
    }
  }

  public synchronized void removeValue(TigerConfigurationKey key) {
    values.remove(key);
  }

  public synchronized boolean containsKey(TigerConfigurationKey key) {
    return values.containsKey(key);
  }

  public synchronized String getValue(TigerConfigurationKey key) {
    return values.get(key);
  }

  public void putAll(TigerConfigurationSource other) {
    values.putAll(other.values);
  }

  public int compareTo(TigerConfigurationSource other) {
    if (other == null) {
      throw new NullPointerException();
    }
    return Integer.compare(precedence.getValue(), other.getPrecedence().getValue());
  }
}
