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
package de.gematik.test.tiger.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** Ringbuffer with bounded size. */
public class RingBufferHashMap<K, V> {
  private final LinkedHashMap<K, V> map = new LinkedHashMap<>();
  private final int size;

  public RingBufferHashMap(final int size) {
    this.size = size;
  }

  public synchronized Optional<V> get(K key) {
    return Optional.ofNullable(map.get(key));
  }

  public synchronized void remove(K key) {
    map.remove(key);
  }

  public synchronized V getOrPutDefault(K key, Supplier<V> defaultValue) {
    try {
      return map.computeIfAbsent(key, k -> defaultValue.get());
    } finally {
      deleteOldestEntryOnOverflow();
    }
  }

  private synchronized void deleteOldestEntryOnOverflow() {
    if (map.size() > size) {
      var iterator = map.entrySet().iterator();
      iterator.next();
      iterator.remove();
    }
  }

  public synchronized List<Map.Entry<K, V>> entries() {
    return new ArrayList<>(map.entrySet());
  }

  public int size() {
    return map.size();
  }

  public boolean containsKey(K key) {
    return map.containsKey(key);
  }

  public synchronized void put(K key, V value) {
    map.put(key, value);
    deleteOldestEntryOnOverflow();
  }

  public void clear() {
    map.clear();
  }
}
