/*
 * Copyright 2024 gematik GmbH
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
