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
 */

package io.cucumber.core.plugin;

import io.cucumber.messages.types.Scenario;
import io.cucumber.messages.types.Step;
import io.cucumber.plugin.event.TestCase;
import java.net.URI;
import lombok.Getter;
import net.thucydides.model.domain.DataTable;

@SuppressWarnings({"java:S1172", "java:S1874"})
public class ScenarioContextDelegate implements IScenarioContext {

  @Getter private final URI featureURI;

  public ScenarioContextDelegate(URI featureURI, ScenarioContext context) {
    this.featureURI = featureURI;
    this.context = context;
  }

  private final ScenarioContext context;

  @Override
  public Scenario getCurrentScenarioDefinition(String scenarioId) {
    return context.getCurrentScenarioDefinition();
  }

  @Override
  public DataTable getTable(String scenarioId) {
    return context.getTable();
  }

  @Override
  public Step getCurrentStep(TestCase testCase) {
    return context.getCurrentStep();
  }

  @Override
  public Scenario currentScenarioOutline(String scenarioId) {
    return context.currentScenarioOutline();
  }

  @Override
  public boolean isAScenarioOutline(String scenarioId) {
    return context.isAScenarioOutline();
  }
}
