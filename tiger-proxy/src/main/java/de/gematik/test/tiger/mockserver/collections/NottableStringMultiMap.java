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

import static de.gematik.test.tiger.mockserver.collections.ImmutableEntry.entry;
import static de.gematik.test.tiger.mockserver.collections.SubSetMatcher.containsSubset;

import com.google.common.annotations.VisibleForTesting;
import de.gematik.test.tiger.mockserver.matchers.MatchDifference;
import de.gematik.test.tiger.mockserver.matchers.RegexStringMatcher;
import de.gematik.test.tiger.mockserver.model.*;
import java.util.*;

/*
 * @author jamesdbloom
 */
public class NottableStringMultiMap extends ObjectWithReflectiveEqualsHashCodeToString {

  private final Map<String, List<String>> backingMap = new LinkedHashMap<>();
  private final RegexStringMatcher regexStringMatcher;
  private final KeyMatchStyle keyMatchStyle;

  public NottableStringMultiMap(
      boolean controlPlaneMatcher,
      KeyMatchStyle keyMatchStyle,
      List<? extends KeyToMultiValue> entries) {
    this.keyMatchStyle = keyMatchStyle;
    regexStringMatcher = new RegexStringMatcher(controlPlaneMatcher);
    for (KeyToMultiValue keyToMultiValue : entries) {
      backingMap.put(keyToMultiValue.getName(), keyToMultiValue.getValues());
    }
  }

  @VisibleForTesting
  public NottableStringMultiMap(
      boolean controlPlaneMatcher,
      KeyMatchStyle keyMatchStyle,
      String[]... keyAndValues) {
    this.keyMatchStyle = keyMatchStyle;
    regexStringMatcher = new RegexStringMatcher(controlPlaneMatcher);
    for (String[] keyAndValue : keyAndValues) {
      if (keyAndValue.length > 0) {
        backingMap.put(
            keyAndValue[0],
            keyAndValue.length > 1
                ? Arrays.asList(keyAndValue).subList(1, keyAndValue.length)
                : Collections.emptyList());
      }
    }
  }

  public KeyMatchStyle getKeyMatchStyle() {
    return keyMatchStyle;
  }

  public boolean containsAll(MatchDifference context, NottableStringMultiMap subset) {
    switch (subset.keyMatchStyle) {
      case SUB_SET:
        {
          boolean isSubset =
              containsSubset(context, regexStringMatcher, subset.entryList(), entryList());
          if (!isSubset && context != null) {
            context.addDifference(
                "multimap subset match failed subset:{}was not a subset of:{}",
                subset.entryList(),
                entryList());
          }
          return isSubset;
        }
      case MATCHING_KEY:
        {
          for (String matcherKey : subset.backingMap.keySet()) {
            List<String> matchedValuesForKey = getAll(matcherKey);
            if (matchedValuesForKey.isEmpty()) {
              if (context != null) {
                context.addDifference(
                    "multimap subset match failed subset:{}did not have expected key:{}",
                    subset,
                    matcherKey);
              }
              return false;
            }

            List<String> matcherValuesForKey = subset.getAll(matcherKey);
            for (String matchedValue : matchedValuesForKey) {
              boolean matchesValue = false;
              for (String matcherValue : matcherValuesForKey) {
                if (regexStringMatcher.matches(context, matcherValue, matchedValue)) {
                  matchesValue = true;
                  break;
                } else {
                  if (context != null) {
                    context.addDifference(
                        "multimap matching key match failed for key:{}",
                        matcherKey);
                  }
                }
              }
              if (!matchesValue) {
                return false;
              }
            }
          }
          return true;
        }
    }
    return false;
  }

  public boolean allKeysNotted() {
    return false;
  }

  public boolean allKeysOptional() {
    return false;
  }

  public boolean isEmpty() {
    return backingMap.isEmpty();
  }

  private List<String> getAll(String key) {
    if (!isEmpty()) {
      List<String> values = new ArrayList<>();
      for (Map.Entry<String, List<String>> entry : backingMap.entrySet()) {
        if (regexStringMatcher.matches(key, entry.getKey())) {
          values.addAll(entry.getValue());
        }
      }
      return values;
    } else {
      return Collections.emptyList();
    }
  }

  private List<ImmutableEntry> entryList() {
    if (!isEmpty()) {
      List<ImmutableEntry> entrySet = new ArrayList<>();
      for (Map.Entry<String, List<String>> entry : backingMap.entrySet()) {
        for (String value : entry.getValue()) {
          entrySet.add(entry(regexStringMatcher, entry.getKey(), value));
        }
      }
      return entrySet;
    } else {
      return Collections.emptyList();
    }
  }
}
