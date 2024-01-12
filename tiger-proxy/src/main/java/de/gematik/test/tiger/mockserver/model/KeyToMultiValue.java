package de.gematik.test.tiger.mockserver.model;

import java.util.*;
import lombok.Data;

@Data
public class KeyToMultiValue extends ObjectWithJsonToString {
  private final String name;
  private final List<String> values;
  private Integer hashCode;

  KeyToMultiValue(final String name, final String... values) {
    if (name == null) {
      throw new IllegalArgumentException("key must not be null");
    }
    this.name = name;
    if (values == null || values.length == 0) {
      this.values = Collections.singletonList(".*");
    } else if (values.length == 1) {
      this.values = Collections.singletonList(values[0]);
    } else {
      this.values = new LinkedList<>();
      for (String value : values) {
        this.values.add(value);
      }
    }
  }

  KeyToMultiValue(final String name, final Collection<String> values) {
    this.name = name;
    if (values == null || values.isEmpty()) {
      this.values = Collections.singletonList(".*");
    } else {
      this.values = new LinkedList<>(values);
    }
    this.hashCode = Objects.hash(this.name, this.values);
  }

  public String getName() {
    return name;
  }

  public List<String> getValues() {
    return values;
  }

  public void replaceValues(List<String> values) {
    if (this.values != values) {
      this.values.clear();
      this.values.addAll(values);
    }
  }

  private void addValue(final String value) {
    if (values != null && !values.contains(value)) {
      values.add(value);
    }
    this.hashCode = Objects.hash(name, values);
  }

  private void addValues(final List<String> values) {
    if (this.values != null) {
      for (String value : values) {
        if (!this.values.contains(value)) {
          this.values.add(value);
        }
      }
    }
  }

  public void addValues(final String... values) {
    addValues(Arrays.asList(values));
  }
}
