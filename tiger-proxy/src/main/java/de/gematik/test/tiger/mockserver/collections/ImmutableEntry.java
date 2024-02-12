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

package de.gematik.test.tiger.mockserver.collections;

import de.gematik.test.tiger.mockserver.matchers.RegexStringMatcher;
import java.util.*;
import org.apache.commons.lang3.tuple.Pair;

/*
 * @author jamesdbloom
 */
public class ImmutableEntry extends Pair<String, String> implements Map.Entry<String, String> {
  private final RegexStringMatcher regexStringMatcher;
  private final String key;
  private final String value;

  public static ImmutableEntry entry(
      RegexStringMatcher regexStringMatcher, String key, String value) {
    return new ImmutableEntry(regexStringMatcher, key, value);
  }

  ImmutableEntry(RegexStringMatcher regexStringMatcher, String key, String value) {
    this.regexStringMatcher = regexStringMatcher;
    this.key = key;
    this.value = value;
  }

  @Override
  public String getLeft() {
    return key;
  }

  @Override
  public String getRight() {
    return value;
  }

  @Override
  public String setValue(String value) {
    throw new UnsupportedOperationException("ImmutableEntry is immutable");
  }

  @Override
  public String toString() {
    return "(" + key + ": " + value + ")";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ImmutableEntry that = (ImmutableEntry) o;
    return regexStringMatcher.matches(key, that.key)
            && regexStringMatcher.matches(value, that.value)
        || (!regexStringMatcher.matches(key, that.key));
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (regexStringMatcher != null ? regexStringMatcher.hashCode() : 0);
    result = 31 * result + (key != null ? key.hashCode() : 0);
    result = 31 * result + (value != null ? value.hashCode() : 0);
    return result;
  }
}
