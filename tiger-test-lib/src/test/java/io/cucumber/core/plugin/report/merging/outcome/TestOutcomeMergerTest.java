/*
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
 * ******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 *
 */

package io.cucumber.core.plugin.report.merging.outcome;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.BDDAssertions.then;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import lombok.SneakyThrows;
import net.thucydides.model.domain.TestOutcome;
import net.thucydides.model.domain.TestStep;
import net.thucydides.model.reports.OutcomeFormat;
import net.thucydides.model.reports.TestOutcomeLoader;
import org.junit.jupiter.api.Test;

class TestOutcomeMergerTest {

  private static final int NUMBER_OF_TESTOUTCOMES = 2;
  TestOutcomeMerger merger = new TestOutcomeMerger();

  @Test
  @SneakyThrows
  void shouldMergeTwoListsOfNonDataDrivenOutcomes() {

    var scenarioOutcome =
        givenAScenarioOutcomeFromFile("serenity-report-data/runsingle/run-regular-scenario");
    var scenarioOutcomeVariant1 =
        givenAScenarioOutcomeFromFile("serenity-report-data/runsingle/run-scenario-variant-1");
    var scenarioOutcomeVariant2 =
        givenAScenarioOutcomeFromFile("serenity-report-data/runsingle/run-scenario-variant-2");
    var scenarioOutcomeVariant3 =
        givenAScenarioOutcomeFromFile("serenity-report-data/runsingle/run-scenario-variant-3");

    var mergedOutcomes =
        whenMergeOutcomes(
            List.of(
                scenarioOutcome,
                scenarioOutcomeVariant1,
                scenarioOutcomeVariant2,
                scenarioOutcomeVariant3));

    var expectedOutcomes = givenAScenarioOutcomeFromFile("serenity-report-data/runall");

    then(mergedOutcomes).containsExactlyElementsOf(expectedOutcomes);

    for (int i = 0; i < mergedOutcomes.size(); i++) {
      andTestStepsAreSimilar(mergedOutcomes.get(i), expectedOutcomes.get(i));
    }
  }

  private void andTestStepsAreSimilar(TestOutcome actual, TestOutcome expected) {
    assertTestStepsAreTheSameIgnoringTime(actual.getTestSteps(), expected.getTestSteps());
  }

  private void assertTestStepsAreTheSameIgnoringTime(
      List<TestStep> actualSteps, List<TestStep> expectedStep) {
    assertThat(actualSteps).hasSize(expectedStep.size());
    for (int i = 0; i < actualSteps.size(); i++) {
      assertThat(actualSteps.get(i))
          .usingRecursiveComparison()
          // the test files were generated with different test runs, therefore the duration and
          // startTime are different
          // the reportData contains a generated id which also differs from run to run.
          .ignoringFields(
              "duration",
              "startTime",
              "reportData",
              "children.duration",
              "children.reportData",
              "children.startTime")
          .isEqualTo(expectedStep.get(i));
    }
  }

  private List<TestOutcome> whenMergeOutcomes(Collection<List<TestOutcome>> testOutcomesToMerge) {
    List<TestOutcome> mergedOutcomes = new ArrayList<>();
    for (List<TestOutcome> testOutcome : testOutcomesToMerge) {
      mergedOutcomes = merger.mergeOutcomes(mergedOutcomes, testOutcome);
    }
    // the sorting is in the productive code not relevant, because each outcome ends up being saved
    // in a separate file
    // however when reading the fiels with serenity TestOutcomeLoader, they are loaded on order of
    // execution.
    // Sorting it here makes it easier to compare the resulting lists.
    return mergedOutcomes.stream().sorted(Comparator.comparing(TestOutcome::getStartTime)).toList();
  }

  @SneakyThrows
  List<TestOutcome> givenAScenarioOutcomeFromFile(String filePath) {
    URL resourceUrl = getClass().getClassLoader().getResource(filePath);
    File resourceFile = new File(resourceUrl.getFile());

    return (List<TestOutcome>)
        TestOutcomeLoader.loadTestOutcomes()
            .inFormat(OutcomeFormat.JSON)
            .from(resourceFile)
            .getOutcomes();
  }
}
