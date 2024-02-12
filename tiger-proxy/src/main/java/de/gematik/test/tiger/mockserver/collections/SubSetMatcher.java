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

import de.gematik.test.tiger.mockserver.matchers.MatchDifference;
import de.gematik.test.tiger.mockserver.matchers.RegexStringMatcher;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/*
 * @author jamesdbloom
 */
public class SubSetMatcher {

  static boolean containsSubset(
      MatchDifference context,
      RegexStringMatcher regexStringMatcher,
      List<ImmutableEntry> subset,
      List<ImmutableEntry> superset) {
    boolean result = true;
    Set<Integer> matchingIndexes = new HashSet<>();
    for (ImmutableEntry subsetItem : subset) {
      Set<Integer> subsetItemMatchingIndexes =
          matchesIndexes(context, regexStringMatcher, subsetItem, superset);
      if ((subsetItemMatchingIndexes.isEmpty())) {
        result = false;
        break;
      }
      matchingIndexes.addAll(subsetItemMatchingIndexes);
    }

    if (result) {
      long subsetNonOptionalSize = subset.size();
      // this prevents multiple items in the subset from being matched by a single item in the
      // superset
      result = matchingIndexes.size() >= subsetNonOptionalSize;
    }
    return result;
  }

  private static Set<Integer> matchesIndexes(
      MatchDifference context,
      RegexStringMatcher regexStringMatcher,
      ImmutableEntry matcherItem,
      List<ImmutableEntry> matchedList) {
    Set<Integer> matchingIndexes = new HashSet<>();
    for (int i = 0; i < matchedList.size(); i++) {
      ImmutableEntry matchedItem = matchedList.get(i);
      boolean keyMatches =
          regexStringMatcher.matches(context, matcherItem.getKey(), matchedItem.getKey());
      boolean valueMatches =
          regexStringMatcher.matches(context, matcherItem.getValue(), matchedItem.getValue());
      if (keyMatches && valueMatches) {
        matchingIndexes.add(i);
      }
    }
    return matchingIndexes;
  }

  private static boolean containsKey(
      RegexStringMatcher regexStringMatcher,
      ImmutableEntry matcherItem,
      List<ImmutableEntry> matchedList) {
    for (ImmutableEntry matchedItem : matchedList) {
      if (regexStringMatcher.matches(matcherItem.getKey(), matchedItem.getKey())) {
        return true;
      }
    }
    return false;
  }
}
