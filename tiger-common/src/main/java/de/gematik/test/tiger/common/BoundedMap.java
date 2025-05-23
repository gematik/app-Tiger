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

import com.google.common.collect.EvictingQueue;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Supplier;

/** Ringbuffer with bounded size. */
public class BoundedMap<K, V> {
  private final EvictingQueue<Map.Entry<K, V>> backingQueue;
  private final Map<K, V> backingMap;
  private final int maxSize;

  public BoundedMap(final int size) {
    this.backingQueue = EvictingQueue.create(size);
    this.backingMap = new HashMap<>(size);
    this.maxSize = size;
  }

  public synchronized Optional<V> get(K key) {
    return Optional.ofNullable(backingMap.get(key));
  }

  public synchronized void remove(K key) {
    backingQueue.removeIf(entry -> entry.getKey().equals(key));
    backingMap.remove(key);
  }

  public synchronized V getOrPutDefault(K key, Supplier<V> defaultValue) {
    if (backingMap.containsKey(key)) {
      return backingMap.get(key);
    } else {
      V value = defaultValue.get();
      addNewEntry(key, value);
      return value;
    }
  }

  private void addNewEntry(K key, V value) {
    if (backingQueue.size() == maxSize) {
      final Entry<K, V> deletedEntry = backingQueue.remove();
      backingMap.remove(deletedEntry.getKey());
    }

    backingQueue.add(Map.entry(key, value));
    backingMap.put(key, value);
  }

  public synchronized List<Map.Entry<K, V>> entries() {
    return new ArrayList<>(backingQueue);
  }

  public int size() {
    return backingQueue.size();
  }
}
