/*
 *
 * Copyright 2021-2025 gematik GmbH
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
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.test.tiger.testenvmgr.util;

import static java.lang.String.join;

import de.gematik.test.tiger.testenvmgr.api.model.mapper.TigerTestIdentifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
  public static LinkedHashSet<TigerTestIdentifier> collectTigerScenarios(TestPlan testPlan) {
    var roots = testPlan.getRoots();

    return roots.stream()
        .map(r -> collectTigerScenarios(testPlan, r))
        .flatMap(Set::stream)
        .filter(
            t ->
                t.getTestIdentifier().getUniqueIdObject().getSegments().stream()
                    .anyMatch(s -> s.getType().equals("engine") && s.getValue().equals("cucumber")))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  @SuppressWarnings(
      "java:S1319") // i want a SequencedSet (java >= 21) and LinkedHashSet is the next
  // best thing
  private static LinkedHashSet<TigerTestIdentifier> collectTigerScenarios(
      TestPlan testplan, TestIdentifier root) {
    var result = new LinkedHashSet<TigerTestIdentifier>();
    if (root.isTest()) {

      result.add(new TigerTestIdentifier(root, root.getDisplayName()));
      return result;
    }
    testplan
        .getChildren(root.getUniqueIdObject())
        .forEach(
            child -> {
              if (child.isContainer()) {
                result.addAll(collectTigerScenarios(testplan, child));
              } else {
                TestDescription description = describeTest(testplan, child);
                result.add(new TigerTestIdentifier(child, description));
              }
            });
    return result;
  }

  // method copied from
  // org.junit.platform.launcher.listeners.MutableTestExecutionSummary.describeTest
  private static TestDescription describeTest(TestPlan testPlan, TestIdentifier testIdentifier) {
    List<String> descriptionParts = new ArrayList<>();
    collectTestDescription(testPlan, testIdentifier, descriptionParts);

    return new TestDescription(descriptionParts);
  }

  private static void collectTestDescription(
      TestPlan testPlan, TestIdentifier identifier, List<String> descriptionParts) {
    descriptionParts.add(0, identifier.getDisplayName());
    testPlan
        .getParent(identifier)
        .ifPresent(parent -> collectTestDescription(testPlan, parent, descriptionParts));
  }

  public record TestDescription(List<String> descriptionParts) {

    public String getEngine() {
      return descriptionParts.get(0);
    }

    public String getFeatureName() {
      return descriptionParts.get(1);
    }

    public String getScenarioName() {
      return descriptionParts.get(descriptionParts.size() - 1);
    }

    public String fullDescription() {
      return join(":", descriptionParts);
    }
  }
}
