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

/** Ringbuffer with bounded size. */
public class RingBufferHashSet<V> {

  private final RingBufferHashMap<V, Boolean> map;

  public RingBufferHashSet(final int size) {
    this.map = new RingBufferHashMap<>(size);
  }

  public synchronized boolean add(V value) {
    map.put(value, true);
    return true;
  }

  public synchronized boolean contains(V value) {
    return map.containsKey(value);
  }

  public synchronized void remove(V value) {
    map.remove(value);
  }

  public synchronized void clear() {
    map.clear();
  }

  public String toString() {
    return map.keys().toString();
  }
}
