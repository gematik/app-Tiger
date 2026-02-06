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
package de.gematik.rbellogger.util;

import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.test.tiger.common.config.TigerConfigurationKey;
import de.gematik.test.tiger.common.config.TigerConfigurationLoader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(callSuper = false)
class TigerConfigurationRbelObject extends RbelPathAble {
  private final StaticTigerConfiguration configuration;
  private final TigerConfigurationKey key;

  public TigerConfigurationRbelObject(TigerConfigurationLoader configuration) {
    this(new StaticTigerConfiguration(configuration), new TigerConfigurationKey(""));
  }

  @Override
  public String toString() {
    return "[" + key.downsampleKey() + "]";
  }

  @Override
  public Optional<RbelPathAble> getFirst(String key) {
    return getAll(key).stream().map(RbelPathAble.class::cast).findFirst();
  }

  @Override
  public TigerConfigurationRbelObject getParentNode() {
    return new TigerConfigurationKey(key)
        .getParentNode()
        .map(newKey -> new TigerConfigurationRbelObject(configuration, newKey))
        .orElse(null);
  }

  @Override
  public List<TigerConfigurationRbelObject> getAll(String subkey) {
    final TigerConfigurationKey targetKey = this.key.createWithNewSubkey(subkey);
    return configuration.keySet().stream()
        .filter(e -> e.isBelow(targetKey) || e.equals(targetKey))
        .map(
            e -> {
              if (e.isDirectlyBelow(this.key)) {
                return new TigerConfigurationRbelObject(this.configuration, e);
              } else {
                return new TigerConfigurationRbelObject(
                    this.configuration,
                    new TigerConfigurationKey(e.subList(0, this.key.size() + 1)));
              }
            })
        .distinct()
        .toList();
  }

  @Override
  public Stream<TigerConfigurationRbelObject> getChildNodesStream() {
    return super.getChildNodesStream();
  }

  @Override
  public List<TigerConfigurationRbelObject> getChildNodes() {
    return super.getChildNodes();
  }

  @Override
  public RbelMultiMap<TigerConfigurationRbelObject> getChildNodesWithKey() {
    return super.getChildNodesWithKey();
  }

  @Override
  public Stream<Map.Entry<String, TigerConfigurationRbelObject>> getChildNodesWithKeyStream() {
    return configuration.entrySet().stream()
        .filter(e -> e.getKey().isBelow(key))
        .map(
            e -> {
              if (e.getKey().isDirectlyBelow(key)) {
                return new TigerConfigurationRbelObject(this.configuration, e.getKey());
              } else {
                return new TigerConfigurationRbelObject(
                    this.configuration,
                    new TigerConfigurationKey(e.getKey().subList(0, key.size() + 1)));
              }
            })
        .distinct()
        .map(e -> Pair.of(e.getKey().get(), e));
  }

  @Override
  public Optional<String> getKey() {
    if (key.isEmpty()) {
      return Optional.empty();
    } else {
      return Optional.of(key.get(key.size() - 1).asString());
    }
  }

  @Override
  public String findNodePath() {
    return key.downsampleKeyCaseSensitive();
  }

  @Override
  public String getRawStringContent() {
    return configuration.get(key);
  }

  @Override
  public List<TigerConfigurationRbelObject> findRbelPathMembers(String rbelPath) {
    return new RbelPathExecutor<>(this, rbelPath).execute();
  }

  private static class StaticTigerConfiguration extends HashMap<TigerConfigurationKey, String> {

    public StaticTigerConfiguration(TigerConfigurationLoader configuration) {
      super(configuration.retrieveMapUnresolved());
    }
  }
}
