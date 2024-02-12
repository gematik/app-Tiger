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

package de.gematik.test.tiger.mockserver.cache;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/*
 * @author jamesdbloom
 */
public class LRUCache<K, V> {

  private static boolean allCachesEnabled = true;
  private static int maxSizeOverride = 0;
  private static final Set<LRUCache<?, ?>> allCaches =
      Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
  private final long ttlInMillis;
  private final int maxSize;
  private final ConcurrentHashMap<K, Entry<V>> map;
  private final ConcurrentLinkedQueue<K> queue;

  public LRUCache(final int maxSize, long ttlInMillis) {
    this.maxSize = maxSize;
    this.map = new ConcurrentHashMap<>(maxSize);
    this.queue = new ConcurrentLinkedQueue<>();
    this.ttlInMillis = ttlInMillis;
    LRUCache.allCaches.add(this);
  }

  public void put(K key, final V value) {
    put(key, value, ttlInMillis);
  }

  public void put(K key, final V value, long ttl) {
    if (allCachesEnabled && key != null) {
      if (map.containsKey(key)) {
        // ensure the queue is in FIFO order
        queue.remove(key);
      }
      while (queue.size() >= maxSize || maxSizeOverride > 0 && queue.size() >= maxSizeOverride) {
        K oldestKey = queue.poll();
        if (null != oldestKey) {
          map.remove(oldestKey);
        }
      }
      queue.add(key);
      map.put(key, new Entry<>(ttl, expiryInMillis(ttl), value));
    }
  }

  private long expiryInMillis(long ttl) {
    return System.currentTimeMillis() + ttl;
  }

  public V get(K key) {
    if (allCachesEnabled && key != null) {
      if (map.containsKey(key)) {
        // remove from the queue and add it again in FIFO queue
        queue.remove(key);
        queue.add(key);
      }

      Entry<V> entry = map.get(key);
      if (entry != null) {
        if (entry.getExpiryInMillis() > System.currentTimeMillis()) {
          return entry.updateExpiryInMillis(expiryInMillis(entry.getTtlInMillis())).getValue();
        } else {
          delete(key);
        }
      }
    }
    return null;
  }

  public void delete(K key) {
    if (allCachesEnabled && key != null) {
      if (map.containsKey(key)) {
        map.remove(key);
        queue.remove(key);
      }
    }
  }
}
