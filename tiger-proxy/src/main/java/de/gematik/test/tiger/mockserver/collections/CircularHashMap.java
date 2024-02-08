/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.collections;

import java.util.LinkedHashMap;
import java.util.Map;

/*
 * @author jamesdbloom
 */
public class CircularHashMap<K, V> extends LinkedHashMap<K, V> {
  private final int maxSize;

  public CircularHashMap(int maxSize) {
    this.maxSize = maxSize;
  }

  @Override
  protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
    return size() > maxSize;
  }
}
