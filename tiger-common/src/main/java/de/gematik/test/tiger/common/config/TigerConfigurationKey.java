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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;

@Data
@EqualsAndHashCode(callSuper = true)
public class TigerConfigurationKey extends ArrayList<TigerConfigurationKeyString> {

  private static final String ALL_SNAKE_AND_UPPERCASE_REGEX = "([A-Z0-9]+_)*[A-Z0-9]+";

  public TigerConfigurationKey(TigerConfigurationKey baseKeys) {
    super(baseKeys);
  }

  public TigerConfigurationKey(String... baseKeys) {
    super(splitKeys(baseKeys));
  }

  public TigerConfigurationKey(String[] baseKeys, String... additionalKeys) {
    super(splitKeys(baseKeys, additionalKeys));
  }

  public TigerConfigurationKey(TigerConfigurationKey baseKey, String key) {
    super(ListUtils.union(baseKey, List.of(new TigerConfigurationKeyString(key))));
  }

  public TigerConfigurationKey(List<TigerConfigurationKeyString> list) {
    super(list);
  }

  public TigerConfigurationKey() {
    super();
  }

  private static List<TigerConfigurationKeyString> splitKeys(
      String[] baseKeys, String[] additionalKeys) {
    final List<TigerConfigurationKeyString> keys = new ArrayList<>(splitKeys(baseKeys));
    keys.addAll(splitKeys(additionalKeys));
    return keys;
  }

  private static List<TigerConfigurationKeyString> splitKey(String key) {
    if (key.matches(ALL_SNAKE_AND_UPPERCASE_REGEX)) {
      return Stream.of(key.split("_"))
          .map(String::toLowerCase)
          .map(TigerConfigurationKeyString::wrapAsKey)
          .toList();
    }
    return Stream.of(key.split("\\.")).map(TigerConfigurationKeyString::wrapAsKey).toList();
  }

  private static List<TigerConfigurationKeyString> splitKeys(String... keys) {
    return Stream.of(keys)
        .map(TigerConfigurationKey::splitKey)
        .flatMap(List::stream)
        .filter(TigerConfigurationKeyString::isNotEmptyKey)
        .toList();
  }

  public String downsampleKey() {
    return stream().map(TigerConfigurationKeyString::asLowerCase).collect(Collectors.joining("."));
  }

  public String downsampleKeyCaseSensitive() {
    return stream().map(TigerConfigurationKeyString::asString).collect(Collectors.joining("."));
  }

  public boolean isBelow(TigerConfigurationKey reference) {
    if (reference == null || reference.size() >= size()) {
      return false;
    }
    for (int i = 0; i < reference.size(); i++) {
      final TigerConfigurationKeyString key = reference.get(i);
      if (StringUtils.isNotEmpty(key.asString()) && !key.equals(get(i))) {
        return false;
      }
    }
    return true;
  }

  public boolean isDirectlyBelow(TigerConfigurationKey reference) {
    if (reference == null || reference.size() + 1 != size()) {
      return false;
    }
    for (int i = 0; i < reference.size(); i++) {
      if (!reference.get(i).equals(get(i))) {
        return false;
      }
    }
    return true;
  }

  public TigerConfigurationKey subtractFromBeginning(TigerConfigurationKey reference) {
    if (reference == null) {
      throw new IllegalArgumentException("Could not subtract null reference");
    }
    if (reference.size() >= size()) {
      throw new IllegalArgumentException("Could not subtract longer reference from value");
    }
    return new TigerConfigurationKey(subList(reference.size(), size()));
  }

  public TigerConfigurationKey createWithNewSubkey(String subkey) {
    final TigerConfigurationKey result = new TigerConfigurationKey(this);
    result.add(subkey);
    return result;
  }

  public boolean containsKey(String key) {
    return downsampleKey().matches(key);
  }

  public void add(String key) {
    addAll(splitKeys(key));
  }

  public Optional<TigerConfigurationKey> getParentNode() {
    if (!isEmpty()) {
      return Optional.of(new TigerConfigurationKey(subList(0, size() - 1)));
    } else {
      return Optional.empty();
    }
  }

  @Override
  public String toString() {
    return downsampleKeyCaseSensitive();
  }
}
