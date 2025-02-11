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

import de.gematik.test.tiger.exceptions.FailMessageOverrider;
import io.cucumber.core.plugin.report.SerenityReporterCallbacks;
import io.cucumber.core.runner.TestCaseDelegate;
import io.cucumber.plugin.EventListener;
import io.cucumber.plugin.event.*;
import java.net.URI;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/** will be replacing teh TigerCucumberListener once Serenity PR is released */
@Slf4j
public class TigerSerenityReporterPlugin implements EventListener {

  private final ISerenityReporter serenityReporter = ISerenityReporter.create();

  @Getter
  private final SerenityReporterCallbacks reporterCallbacks = new SerenityReporterCallbacks();

  public IScenarioContext getContext(URI featureURI) {
    return serenityReporter.getContext(featureURI);
  }

  public void setEventPublisher(EventPublisher publisher) {
    publisher.registerHandlerFor(TestRunStarted.class, this::handleTestRunStarted);
    publisher.registerHandlerFor(TestSourceRead.class, this::handleTestSourceRead);
    publisher.registerHandlerFor(TestCaseStarted.class, this::handleTestCaseStarted);
    publisher.registerHandlerFor(TestStepStarted.class, this::handleTestStepStarted);
    publisher.registerHandlerFor(TestStepFinished.class, this::handleTestStepFinished);
    publisher.registerHandlerFor(TestCaseFinished.class, this::handleTestCaseFinished);
    publisher.registerHandlerFor(TestRunFinished.class, this::handleTestRunFinished);
    publisher.registerHandlerFor(WriteEvent.class, this::handleWriteEvent);
  }

  public void handleTestRunStarted(TestRunStarted event) {
    reporterCallbacks.handleTestRunStarted(event);
    serenityReporter.handleTestRunStarted(event);
  }

  public void handleTestSourceRead(TestSourceRead event) {
    serenityReporter.handleTestSourceRead(event);
    log.info("Feature file started: {}", event.getUri());
    reporterCallbacks.handleTestSourceRead(event);
  }

  public void handleTestCaseStarted(TestCaseStarted event) {
    serenityReporter.handleTestCaseStarted(event);
    reporterCallbacks.handleTestCaseStarted(event, getContext(event.getTestCase().getUri()));
  }

  public void handleTestStepStarted(TestStepStarted event) {
    serenityReporter.handleTestStepStarted(event);
    reporterCallbacks.handleTestStepStarted(event, getContext(event.getTestCase().getUri()));
  }

  public void handleTestStepFinished(TestStepFinished event) {
    FailMessageOverrider.overrideFailureMessage(event);
    if (TestCaseDelegate.of(event.getTestCase()).isDryRun()) {
      event =
          new TestStepFinished(
              event.getInstant(),
              event.getTestCase(),
              event.getTestStep(),
              new Result(
                  Status.SKIPPED, event.getResult().getDuration(), event.getResult().getError()));
    }
    reporterCallbacks.handleTestStepFinished(event, getContext(event.getTestCase().getUri()));
    serenityReporter.handleTestStepFinished(event);
  }

  public void handleTestCaseFinished(TestCaseFinished event) {
    if (TestCaseDelegate.of(event.getTestCase()).isDryRun()) {
      event =
          new TestCaseFinished(
              event.getInstant(),
              event.getTestCase(),
              new Result(
                  Status.SKIPPED, event.getResult().getDuration(), event.getResult().getError()));
    }
    reporterCallbacks.handleTestCaseFinished(event, getContext(event.getTestCase().getUri()));
    serenityReporter.handleTestCaseFinished(event);
  }

  public void handleTestRunFinished(TestRunFinished event) {
    reporterCallbacks.handleTestRunFinished(event);
    serenityReporter.handleTestRunFinished(event);
  }

  public void handleWriteEvent(WriteEvent event) {
    serenityReporter.handleWriteEvent(event);
  }
}
