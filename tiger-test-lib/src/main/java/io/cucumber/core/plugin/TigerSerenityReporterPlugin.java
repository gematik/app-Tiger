/*
 * Copyright (c) 2024 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.cucumber.core.plugin;

import io.cucumber.core.plugin.report.SerenityReporterCallbacks;
import io.cucumber.plugin.event.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.thucydides.model.webdriver.Configuration;

/** will be replacing teh TigerCucumberListener once Serenity PR is released */
@Slf4j
public class TigerSerenityReporterPlugin extends SerenityReporter {

  @Getter private SerenityReporterCallbacks reporterCallbacks = new SerenityReporterCallbacks();

  public TigerSerenityReporterPlugin() {
    super();
  }

  public TigerSerenityReporterPlugin(Configuration systemConfiguration) {
    super(systemConfiguration);
  }

  public ScenarioContextDelegate getScenarioContextDelegate() {
    return new ScenarioContextDelegate(getContext());
  }

  @Override
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

  @Override
  protected void handleTestRunStarted(TestRunStarted event) {
    reporterCallbacks.handleTestRunStarted(event, getScenarioContextDelegate());
    super.handleTestRunStarted(event);
  }

  @Override
  protected void handleTestSourceRead(TestSourceRead event) {
    super.handleTestSourceRead(event);
    log.info("Feature file started: {}", event.getUri());
    reporterCallbacks.handleTestSourceRead(event);
  }

  @Override
  protected void handleTestCaseStarted(TestCaseStarted event) {
    super.handleTestCaseStarted(event);
    reporterCallbacks.handleTestCaseStarted(event, getScenarioContextDelegate());
  }

  @Override
  protected void handleTestStepStarted(TestStepStarted event) {
    super.handleTestStepStarted(event);
    reporterCallbacks.handleTestStepStarted(event, getScenarioContextDelegate());
  }

  @Override
  protected void handleTestStepFinished(TestStepFinished event) {
    reporterCallbacks.handleTestStepFinished(event, getScenarioContextDelegate());
    super.handleTestStepFinished(event);
  }

  @Override
  protected void handleTestCaseFinished(TestCaseFinished event) {
    reporterCallbacks.handleTestCaseFinished(event, getScenarioContextDelegate());

    super.handleTestCaseFinished(event);
  }

  @Override
  protected void handleTestRunFinished(TestRunFinished event) {
    reporterCallbacks.handleTestRunFinished(event, getScenarioContextDelegate());
    super.handleTestRunFinished(event);
  }

  protected void handleWriteEvent(WriteEvent event) {
    super.handleWrite(event);
  }
}
