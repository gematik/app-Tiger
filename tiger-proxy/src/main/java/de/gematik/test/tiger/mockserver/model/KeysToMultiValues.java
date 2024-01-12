package de.gematik.test.tiger.mockserver.model;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import java.util.*;
import org.apache.commons.lang3.ArrayUtils;

@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class KeysToMultiValues<T extends KeyToMultiValue, K extends KeysToMultiValues>
    extends ObjectWithJsonToString {

  private KeyMatchStyle keyMatchStyle = KeyMatchStyle.SUB_SET;

  private final Multimap<String, String> multimap;
  private final K k = (K) this;

  protected KeysToMultiValues() {
    multimap = LinkedHashMultimap.create();
  }

  protected KeysToMultiValues(Multimap<String, String> multimap) {
    this.multimap = LinkedHashMultimap.create(multimap);
  }

  public abstract T build(final String name, final Collection<String> values);

  protected abstract void isModified();

  public KeyMatchStyle getKeyMatchStyle() {
    return keyMatchStyle;
  }

  @SuppressWarnings("UnusedReturnValue")
  public KeysToMultiValues<T, K> withKeyMatchStyle(KeyMatchStyle keyMatchStyle) {
    this.keyMatchStyle = keyMatchStyle;
    return this;
  }

  public K withEntries(final Map<String, List<String>> entries) {
    isModified();
    multimap.clear();
    for (String name : entries.keySet()) {
      for (String value : entries.get(name)) {
        withEntry(name, value);
      }
    }
    return k;
  }

  public K withEntries(final List<T> entries) {
    isModified();
    multimap.clear();
    if (entries != null) {
      for (T entry : entries) {
        withEntry(entry);
      }
    }
    return k;
  }

  @SafeVarargs
  public final K withEntries(final T... entries) {
    if (ArrayUtils.isNotEmpty(entries)) {
      withEntries(Arrays.asList(entries));
    }
    return k;
  }

  public K withEntry(final T entry) {
    if (entry != null) {
      isModified();
      if (entry.getValues().isEmpty()) {
        multimap.put(entry.getName(), null);
      } else {
        multimap.putAll(entry.getName(), entry.getValues());
      }
    }
    return k;
  }

  public K withEntry(final String name, final String... values) {
    isModified();
    if (values == null || values.length == 0) {
      multimap.put(name, "");
    } else {
      multimap.putAll(name, () -> Arrays.stream(values).iterator());
    }
    return k;
  }

  public K withEntry(final String name, final List<String> values) {
    isModified();
    if (values == null || values.isEmpty()) {
      multimap.put(name, null);
    } else {
      multimap.putAll(name, values);
    }
    return k;
  }

  public boolean remove(final String name) {
    boolean exists = false;
    if (name != null) {
      isModified();
      for (String key : multimap.keySet().toArray(new String[0])) {
        if (key.equalsIgnoreCase(name)) {
          multimap.removeAll(key);
          exists = true;
        }
      }
    }
    return exists;
  }

  @SuppressWarnings("UnusedReturnValue")
  public K replaceEntry(final T entry) {
    if (entry != null) {
      isModified();
      remove(entry.getName());
      multimap.putAll(entry.getName(), entry.getValues());
    }
    return k;
  }

  @SuppressWarnings("UnusedReturnValue")
  public K replaceEntryIfExists(final T entry) {
    if (entry != null) {
      isModified();
      if (remove(entry.getName())) {
        multimap.putAll(entry.getName(), entry.getValues());
      }
    }
    return k;
  }

  public List<T> getEntries() {
    if (!isEmpty()) {
      ArrayList<T> headers = new ArrayList<>();
      for (String String : multimap.keySet().toArray(new String[0])) {
        headers.add(build(String, multimap.get(String)));
      }
      return headers;
    } else {
      return Collections.emptyList();
    }
  }

  public Set<String> keySet() {
    return multimap.keySet();
  }

  public Multimap<String, String> getMultimap() {
    return multimap;
  }

  public List<String> getValues(final String name) {
    if (!isEmpty() && name != null) {
      List<String> values = new ArrayList<>();
      for (String key : multimap.keySet().toArray(new String[0])) {
        if (key != null && key.equalsIgnoreCase(name)) {
          values.addAll(multimap.get(key));
        }
      }
      return values;
    } else {
      return Collections.emptyList();
    }
  }

  String getFirstValue(final String name) {
    if (!isEmpty()) {
      for (String key : multimap.keySet().toArray(new String[0])) {
        if (key != null && key.equalsIgnoreCase(name)) {
          Collection<String> Strings = multimap.get(key);
          if (!Strings.isEmpty()) {
            String next = Strings.iterator().next();
            if (next != null) {
              return next;
            }
          }
        }
      }
    }
    return "";
  }

  public boolean containsEntry(final String name) {
    if (!isEmpty()) {
      for (String key : multimap.keySet().toArray(new String[0])) {
        if (key != null && key.equalsIgnoreCase(name)) {
          return true;
        }
      }
    }
    return false;
  }

  public boolean containsEntry(final String name, final String value) {
    if (!isEmpty() && name != null && value != null) {
      for (String entryKey : multimap.keySet().toArray(new String[0])) {
        if (entryKey != null && entryKey.equalsIgnoreCase(name)) {
          Collection<String> Strings = multimap.get(entryKey);
          if (Strings != null) {
            for (String entryValue : Strings.toArray(new String[0])) {
              if (value.equalsIgnoreCase(entryValue)) {
                return true;
              }
            }
          }
        }
      }
    }
    return false;
  }

  public boolean isEmpty() {
    return multimap.isEmpty();
  }

  public abstract K clone();

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof KeysToMultiValues)) {
      return false;
    }
    KeysToMultiValues<?, ?> that = (KeysToMultiValues<?, ?>) o;
    return Objects.equals(multimap, that.multimap);
  }

  @Override
  public int hashCode() {
    return Objects.hash(multimap);
  }
}
