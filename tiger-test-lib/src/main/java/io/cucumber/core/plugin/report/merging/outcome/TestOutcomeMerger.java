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

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.thucydides.model.domain.DataTable;
import net.thucydides.model.domain.TestOutcome;
import net.thucydides.model.domain.TestResultList;
import net.thucydides.model.domain.TestStep;

public class TestOutcomeMerger {
  public List<TestOutcome> mergeOutcomes(
      List<TestOutcome> previousOutcomes, List<TestOutcome> currentOutcomes) {

    // By using the Collectors.toMap, all elements in the stream are added to the
    // result. If there are two elements with the same id, then the mergeOutcome method is
    // used.
    // Because the stream is sequential, the first elements being added to the result are the
    // previous outcomes.
    Map<String, TestOutcome> orderedMapOfOutcomes =
        Stream.of(previousOutcomes, currentOutcomes)
            .flatMap(List::stream)
            .collect(
                Collectors.toMap(
                    TestOutcome::getId, o -> o, this::mergeOutcome, LinkedHashMap::new));

    return orderedMapOfOutcomes.values().stream().toList();
  }

  private TestOutcome mergeOutcome(TestOutcome first, TestOutcome second) {
    if (first.isDataDriven() && second.isDataDriven()) {

      var mergedScenarioVariants = mergeVariants(first.getTestSteps(), second.getTestSteps());
      var stepCount = 0;
      for (TestStep s : mergedScenarioVariants) {
        stepCount = renumberStepsFromScenarioExample(s, first.getDataTable(), stepCount);
      }
      second.withSteps(mergedScenarioVariants);
      second.getDataTable().setScenarioOutline(second.getDataDrivenSampleScenario());
      mergeResultsIntoDataTable(second.getDataTable(), first.getDataTable());
    }
    return second;
  }

  /** modifies the results of the finalTable by merging the toBeMerged table with it */
  private void mergeResultsIntoDataTable(DataTable finalTable, DataTable toBeMerged) {
    if (finalTable.getRows().size() != toBeMerged.getRows().size()) {
      throw new IllegalStateException("Only DataTables of same size can be merged.");
    }
    for (var i = 0; i < toBeMerged.getRows().size(); i++) {
      var mergedResult =
          TestResultList.overallResultFrom(
              List.of(
                  finalTable.getRows().get(i).getResult(),
                  toBeMerged.getRows().get(i).getResult()));
      finalTable.updateRowResult(i, mergedResult);
    }
  }

  private int getLineNumber(TestStep step) {
    // why does it need to be private? :'(
    try {
      Field lineNumberField = TestStep.class.getDeclaredField("lineNumber");
      lineNumberField.setAccessible(true);
      return (Integer) lineNumberField.get(step);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new IllegalStateException(
          "Could not get line number from test step when building report", e);
    }
  }

  private List<TestStep> mergeVariants(
      List<TestStep> firstVariants, List<TestStep> secondVariants) {
    // When running a ScenarioOutline, the TestOutcome object corresponds to the scenario
    // outline,
    // and the TestSteps inside the TestOutcome correspond to the individual scenario
    // variants.
    // Here we are merging TestSteps which are actually scenario variants.
    Map<String, TestStep> mergedVariants =
        Stream.of(firstVariants, secondVariants)
            .flatMap(List::stream)
            .collect(
                Collectors.toMap(
                    s -> getDescriptionWithoutNumericPrefix(s.getDescription()),
                    Function.identity(),
                    (s1, s2) -> s2,
                    LinkedHashMap::new));
    return mergedVariants.values().stream()
        // If we manually click the scenario variants in a different order, we still want them to
        // appear
        // in the report in the order of the examples table. Therefore we use the line number.
        .sorted(Comparator.comparing(this::getLineNumber))
        .toList();
  }

  private int renumberStepsFromScenarioExample(TestStep s, DataTable examplesTable, int stepCount) {
    for (var r = 0; r < examplesTable.getRows().size(); r++) {
      var row = examplesTable.getRows().get(r);
      if (s.correspondsToLine((int) row.getLineNumber())) {
        s.renumberFrom(stepCount + 1);
        stepCount += (s.getFlattenedSteps().size() + 1);
        s.setDescription(s.getDescription().replaceFirst("\\d+: ", (r + 1) + ": "));
        break;
      }
    }
    return stepCount;
  }

  private String getDescriptionWithoutNumericPrefix(String description) {
    // descriptions for examples in scenario outlines start with a number followed by : ,
    // e.g.: "1: "
    // The number starts counting always from 1. If we manually click to execute the 2nd
    // example, the description will have
    // the number 1 because the test case is being run alone.
    // The rest of the description includes also the data table example line, and is
    // the way we identify which example is being run and cann match them together

    return description.substring((description.indexOf(":") + 2));
  }
}
