/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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
