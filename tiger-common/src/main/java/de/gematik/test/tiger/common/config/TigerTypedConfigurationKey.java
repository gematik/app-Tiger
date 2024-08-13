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

import java.lang.reflect.Constructor;
import java.util.Optional;
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
  private final Constructor<T> typeConstructor;

  public TigerTypedConfigurationKey(String key, Class<T> type) {
    this(new TigerConfigurationKey(key), type);
  }

  @SneakyThrows
  public TigerTypedConfigurationKey(TigerConfigurationKey key, Class<T> type) {
    this.key = key;
    typeConstructor = type.getConstructor(String.class);
    this.defaultValue = Optional.empty();
  }

  public TigerTypedConfigurationKey(String key, Class<T> type, T defaultValue) {
    this(new TigerConfigurationKey(key), type, defaultValue);
  }

  @SneakyThrows
  public TigerTypedConfigurationKey(TigerConfigurationKey key, Class<T> type, T defaultValue) {
    this.key = key;
    typeConstructor = type.getConstructor(String.class);
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
    return typeConstructor.newInstance(s);
  }

  public void putValue(T value) {
    TigerGlobalConfiguration.putValue(key.downsampleKey(), value);
  }

  public void putValue(T value, SourceType sourceType) {
    TigerGlobalConfiguration.putValue(key.downsampleKey(), value, sourceType);
  }

  public void clearValue(){
    TigerGlobalConfiguration.deleteFromAllSources(this.key);
  }
}
