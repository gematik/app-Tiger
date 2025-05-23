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

import io.cucumber.plugin.event.TestCaseFinished;
import io.cucumber.plugin.event.TestCaseStarted;
import io.cucumber.plugin.event.TestRunFinished;
import io.cucumber.plugin.event.TestRunStarted;
import io.cucumber.plugin.event.TestSourceRead;
import io.cucumber.plugin.event.TestStepFinished;
import io.cucumber.plugin.event.TestStepStarted;
import io.cucumber.plugin.event.WriteEvent;
import java.net.URI;
import lombok.experimental.Delegate;

public class SerenityReporterParallelDelegate implements ISerenityReporter {

  @Delegate private final SerenityReporterParallel reporter = new SerenityReporterParallel();

  public IScenarioContext getContext(URI featureURI) {
    return new ScenarioContextParallelDelegate(featureURI, reporter.getContext(featureURI));
  }

  @Override
  public void handleTestRunStarted(TestRunStarted event) {
    reporter.handleTestRunStarted(event);
  }

  @Override
  public void handleTestSourceRead(TestSourceRead event) {
    reporter.handleTestSourceRead(event);
  }

  @Override
  public void handleTestCaseStarted(TestCaseStarted event) {
    reporter.handleTestCaseStarted(event);
  }

  @Override
  public void handleTestStepStarted(TestStepStarted event) {
    reporter.handleTestStepStarted(event);
  }

  @Override
  public void handleTestStepFinished(TestStepFinished event) {
    reporter.handleTestStepFinished(event);
  }

  @Override
  public void handleTestCaseFinished(TestCaseFinished event) {
    reporter.handleTestCaseFinished(event);
  }

  @Override
  public void handleTestRunFinished(TestRunFinished event) {
    reporter.handleTestRunFinished(event);
  }

  @Override
  public void handleWriteEvent(WriteEvent event) {
    reporter.handleWrite(event);
  }
}
