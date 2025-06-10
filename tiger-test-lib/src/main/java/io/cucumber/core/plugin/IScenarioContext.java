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
package io.cucumber.core.plugin;

import io.cucumber.messages.types.Scenario;
import io.cucumber.messages.types.Step;
import io.cucumber.plugin.event.TestCase;
import java.net.URI;
import net.thucydides.core.steps.StepEventBus;
import net.thucydides.model.domain.DataTable;

public interface IScenarioContext {
  Scenario getCurrentScenarioDefinition(String scenarioId);

  DataTable getTable(String scenarioId);

  Step getCurrentStep(TestCase testCase);

  Scenario currentScenarioOutline(String scenarioId);

  boolean isAScenarioOutline(String scenarioId);

  URI getFeatureURI();

  StepEventBus stepEventBus();
}
