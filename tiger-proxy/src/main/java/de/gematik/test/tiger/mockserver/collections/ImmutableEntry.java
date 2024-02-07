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

  public boolean isOptional() {
    return false;
  }

  public boolean isNotOptional() {
    return !isOptional();
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
    return Objects.hash(key, value);
  }

  public static <T> boolean listsEqual(List<T> matcher, List<T> matched) {
    boolean matches = false;
    if (matcher.size() == matched.size()) {
      Set<Integer> matchedIndexes = new HashSet<>();
      Set<Integer> matcherIndexes = new HashSet<>();
      for (int i = 0; i < matcher.size(); i++) {
        T matcherItem = matcher.get(i);
        for (int j = 0; j < matched.size(); j++) {
          T matchedItem = matched.get(j);
          if (matcherItem != null && matcherItem.equals(matchedItem)) {
            matchedIndexes.add(j);
            matcherIndexes.add(i);
          }
        }
      }
      matches = matchedIndexes.size() == matched.size() && matcherIndexes.size() == matcher.size();
    }
    return matches;
  }

  public static boolean listsEqualWithOptionals(
      RegexStringMatcher regexStringMatcher,
      List<ImmutableEntry> matcher,
      List<ImmutableEntry> matched) {
    Set<Integer> matchingMatchedIndexes = new HashSet<>();
    Set<Integer> matchingMatcherIndexes = new HashSet<>();
    Set<String> matcherKeys = new HashSet<>();
    matcher.forEach(matcherItem -> matcherKeys.add(matcherItem.getKey()));
    Set<String> matchedKeys = new HashSet<>();
    matched.forEach(matchedItem -> matchedKeys.add(matchedItem.getKey()));
    for (int i = 0; i < matcher.size(); i++) {
      ImmutableEntry matcherItem = matcher.get(i);
      if (matcherItem != null) {
        for (int j = 0; j < matched.size(); j++) {
          ImmutableEntry matchedItem = matched.get(j);
          if (matchedItem != null) {
            if (matcherItem.equals(matchedItem)) {
              matchingMatchedIndexes.add(j);
              matchingMatcherIndexes.add(i);
            } else if (!contains(regexStringMatcher, matchedKeys, matcherItem.getKey())) {
              matchingMatchedIndexes.add(j);
              matchingMatcherIndexes.add(i);
            } else if (!contains(regexStringMatcher, matcherKeys, matchedItem.getKey())) {
              matchingMatchedIndexes.add(j);
              matchingMatcherIndexes.add(i);
            }
          }
        }
      }
    }
    return matchingMatchedIndexes.size() == matched.size()
        && matchingMatcherIndexes.size() == matcher.size();
  }

  private static boolean contains(
      RegexStringMatcher regexStringMatcher, Set<String> matchedKeys, String matcherItem) {
    boolean result = false;
    for (String matchedKey : matchedKeys) {
      if (regexStringMatcher.matches(matchedKey, matcherItem)) {
        return true;
      }
    }
    return result;
  }
}
