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

import io.cucumber.plugin.event.TestCaseFinished;
import io.cucumber.plugin.event.TestCaseStarted;
import io.cucumber.plugin.event.TestRunFinished;
import io.cucumber.plugin.event.TestRunStarted;
import io.cucumber.plugin.event.TestSourceRead;
import io.cucumber.plugin.event.TestStepFinished;
import io.cucumber.plugin.event.TestStepStarted;
import io.cucumber.plugin.event.WriteEvent;
import java.net.URI;
import lombok.NonNull;

public interface ISerenityReporter {

  static @NonNull ISerenityReporter create() {
    return new SerenityReporterDelegate();
  }

  void handleTestRunStarted(TestRunStarted event);

  void handleTestSourceRead(TestSourceRead event);

  void handleTestCaseStarted(TestCaseStarted event);

  void handleTestStepStarted(TestStepStarted event);

  void handleTestStepFinished(TestStepFinished event);

  void handleTestCaseFinished(TestCaseFinished event);

  void handleTestRunFinished(TestRunFinished event);

  void handleWriteEvent(WriteEvent event);

  IScenarioContext getContext(URI featureURI);
}
