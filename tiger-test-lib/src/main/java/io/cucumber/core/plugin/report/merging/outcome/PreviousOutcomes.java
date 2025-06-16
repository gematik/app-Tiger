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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.thucydides.model.domain.TestOutcome;

public class PreviousOutcomes implements OutcomesProvider {
  private final List<TestOutcome> outcomes = new ArrayList<>();

  @Override
  public List<TestOutcome> getOutcomes() {
    return Collections.unmodifiableList(outcomes);
  }

  @Override
  public void setOutcomes(List<TestOutcome> updatedOutcomes) {
    this.outcomes.clear();
    this.outcomes.addAll(updatedOutcomes);
  }

  @Override
  public void clearOutcomes() {
    this.outcomes.clear();
  }
}
