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

package io.cucumber.core.plugin.report.merging;

import io.cucumber.core.plugin.SerenityReporter;
import io.cucumber.core.plugin.report.merging.outcome.OutcomesProvider;
import io.cucumber.core.plugin.report.merging.outcome.PreviousOutcomes;
import io.cucumber.core.plugin.report.merging.outcome.TestOutcomeMerger;
import java.util.List;
import java.util.Objects;
import net.thucydides.model.domain.TestOutcome;

public class MergingSerenityReporter extends SerenityReporter {

  private static OutcomesProvider previousOutcomes = new PreviousOutcomes();

  public static void setOutcomesProvider(OutcomesProvider outcomesProvider) {
    Objects.requireNonNull(outcomesProvider);
    MergingSerenityReporter.previousOutcomes = outcomesProvider;
  }

  public static OutcomesProvider getOutcomesProvider() {
    return previousOutcomes;
  }

  public static void clearOutcomesProvider() {
    previousOutcomes.clearOutcomes();
  }

  private final TestOutcomeMerger outcomeMerger;

  public MergingSerenityReporter(TestOutcomeMerger outcomeMerger) {
    this.outcomeMerger = outcomeMerger;
  }

  @Override
  public List<TestOutcome> getAllTestOutcomes() {
    var currentOutcomes = super.getAllTestOutcomes();
    if (previousOutcomes.getOutcomes().isEmpty()) {
      previousOutcomes.setOutcomes(currentOutcomes);
    } else {
      var mergeOutcomes =
          outcomeMerger.mergeOutcomes(previousOutcomes.getOutcomes(), currentOutcomes);
      previousOutcomes.setOutcomes(mergeOutcomes);
    }
    return previousOutcomes.getOutcomes();
  }
}
