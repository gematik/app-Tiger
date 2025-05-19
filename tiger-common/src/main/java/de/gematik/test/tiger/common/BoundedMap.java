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
