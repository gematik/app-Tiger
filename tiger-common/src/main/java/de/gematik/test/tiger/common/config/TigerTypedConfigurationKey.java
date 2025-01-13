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

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import java.util.function.Function;
import lombok.Getter;
import lombok.SneakyThrows;

/**
 * Ease-of-use solution to retrieve configuration values from the TigerGlobalConfiguration. This
 * object bundles key, type and default value, allowing to centralize all the information associated
 * with a configuration key.
 *
 * @param <T>
 */
public class TigerTypedConfigurationKey<T> {

  @Getter private final TigerConfigurationKey key;
  private final Optional<T> defaultValue;
  private final Function<String, T> typeConstructor;

  public TigerTypedConfigurationKey(String key, Class<T> type) {
    this(key, type, null);
  }

  public TigerTypedConfigurationKey(String key, Class<T> type, T defaultValue) {
    this(new TigerConfigurationKey(key), type, defaultValue);
  }

  public TigerTypedConfigurationKey(TigerConfigurationKey key, Class<T> type) {
    this(key, type, null);
  }

  @SneakyThrows
  public TigerTypedConfigurationKey(TigerConfigurationKey key, Class<T> type, T defaultValue) {
    this.key = key;
    if (type.isArray()) {
      this.typeConstructor =
          s -> {
            String[] split = s.split(",");
            T[] array = (T[]) Array.newInstance(type.getComponentType(), split.length);
            for (int i = 0; i < split.length; i++) {
              try {
                Array.set(
                    array,
                    i,
                    type.componentType().getConstructor(String.class).newInstance(split[i].trim()));
              } catch (InstantiationException
                  | IllegalAccessException
                  | InvocationTargetException
                  | NoSuchMethodException e) {
                throw new TigerConfigurationException(
                    "Exception while retrieving value for key "
                        + key.downsampleKey()
                        + " and type "
                        + type,
                    e);
              }
            }
            return (T) array;
          };
    } else {
      this.typeConstructor =
          (s) -> {
            try {
              return type.getConstructor(String.class).newInstance(s);
            } catch (InstantiationException
                | IllegalAccessException
                | InvocationTargetException
                | NoSuchMethodException e) {
              throw new TigerConfigurationException(
                  "Exception while retrieving value for key "
                      + key.downsampleKey()
                      + " and type "
                      + type,
                  e);
            }
          };
    }
    this.defaultValue = Optional.ofNullable(defaultValue);
  }

  @SneakyThrows
  public Optional<T> getValue() {
    return TigerGlobalConfiguration.readStringOptional(key.downsampleKey()).map(this::getInstance);
  }

  @SneakyThrows
  public Optional<T> getValueWithoutResolving() {
    return TigerGlobalConfiguration.readStringWithoutResolving(key.downsampleKey())
        .map(this::getInstance);
  }

  public T getValueOrDefault() {
    return getValue().or(() -> defaultValue).orElseThrow();
  }

  @SneakyThrows
  private T getInstance(String s) {
    return typeConstructor.apply(s);
  }

  public void putValue(T value) {
    TigerGlobalConfiguration.putValue(key.downsampleKey(), value);
  }

  public void putValue(T value, ConfigurationValuePrecedence precedence) {
    TigerGlobalConfiguration.putValue(key.downsampleKey(), value, precedence);
  }

  public void clearValue() {
    TigerGlobalConfiguration.deleteFromAllSources(this.key);
  }
}
