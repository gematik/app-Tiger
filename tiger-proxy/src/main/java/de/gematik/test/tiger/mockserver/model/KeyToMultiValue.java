/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.model;

import java.util.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/*
 * @author jamesdbloom
 */
@EqualsAndHashCode(callSuper = false)
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
      this.values.addAll(Arrays.asList(values));
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
}
