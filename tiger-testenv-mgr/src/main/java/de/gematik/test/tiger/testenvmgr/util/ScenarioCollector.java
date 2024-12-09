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
 *
 */

package de.gematik.test.tiger.testenvmgr.util;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ScenarioCollector {

  @SuppressWarnings(
      "java:S1319") // i want a SequencedSet (java >= 21) and LinkedHashSet is the next
  // best thing
  public static LinkedHashSet<TestIdentifier> collectScenarios(TestPlan testPlan) {
    var roots = testPlan.getRoots();

    return roots.stream()
        .map(r -> collectScenarios(testPlan, r))
        .flatMap(Set::stream)
        .filter(
            t ->
                t.getUniqueIdObject().getSegments().stream()
                    .anyMatch(s -> s.getType().equals("engine") && s.getValue().equals("cucumber")))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  @SuppressWarnings(
      "java:S1319") // i want a SequencedSet (java >= 21) and LinkedHashSet is the next
  // best thing
  private static LinkedHashSet<TestIdentifier> collectScenarios(
      TestPlan testplan, TestIdentifier root) {
    var result = new LinkedHashSet<TestIdentifier>();
    if (root.isTest()) {
      result.add(root);
      return result;
    }
    for (TestIdentifier child : testplan.getChildren(root.getUniqueIdObject())) {
      if (child.isContainer()) {
        result.addAll(collectScenarios(testplan, child));
      } else {
        result.add(child);
      }
    }
    return result;
  }
}
