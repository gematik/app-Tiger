package de.gematik.test.tiger.mockserver.model;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.*;

@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class KeysAndValues<T extends KeyAndValue, K extends KeysAndValues>
    extends ObjectWithJsonToString {

  private final Map<String, String> map;

  protected KeysAndValues() {
    map = new LinkedHashMap<>();
  }

  protected KeysAndValues(Map<String, String> map) {
    this.map = new LinkedHashMap<>(map);
  }

  public abstract T build(String name, String value);

  public K withEntries(List<T> entries) {
    map.clear();
    if (entries != null) {
      for (T cookie : entries) {
        withEntry(cookie);
      }
    }
    return (K) this;
  }

  public K withEntries(T... entries) {
    if (entries != null) {
      withEntries(Arrays.asList(entries));
    }
    return (K) this;
  }

  public K withEntry(T entry) {
    if (entry != null) {
      map.put(entry.getName(), entry.getValue());
    }
    return (K) this;
  }

  public List<T> getEntries() {
    if (!map.isEmpty()) {
      ArrayList<T> cookies = new ArrayList<>();
      for (String String : map.keySet()) {
        cookies.add(build(String, map.get(String)));
      }
      return cookies;
    } else {
      return Collections.emptyList();
    }
  }

  public Map<String, String> getMap() {
    return map;
  }

  public boolean isEmpty() {
    return map.isEmpty();
  }

  public boolean remove(String name) {
    if (isNotBlank(name)) {
      return map.remove(name) != null;
    }
    return false;
  }

  public abstract K clone();
}
