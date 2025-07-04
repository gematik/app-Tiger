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
package de.gematik.test.tiger.testenvmgr.controller;

import de.gematik.rbellogger.renderer.MessageMetaDataDto;
import de.gematik.test.tiger.server.TigerBuildPropertiesService;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.data.BannerType;
import de.gematik.test.tiger.testenvmgr.data.TigerEnvStatusDto;
import de.gematik.test.tiger.testenvmgr.data.TigerServerStatusDto;
import de.gematik.test.tiger.testenvmgr.env.*;
import io.micrometer.common.util.StringUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/status")
@Slf4j
public class EnvStatusController implements TigerUpdateListener {

  private final TigerEnvStatusDto tigerEnvStatus = new TigerEnvStatusDto();

  private final TigerTestEnvMgr tigerTestEnvMgr;
  private final TigerBuildPropertiesService buildProperties;

  public EnvStatusController(
      final TigerTestEnvMgr tigerTestEnvMgr, TigerBuildPropertiesService buildProperties) {
    this.tigerTestEnvMgr = tigerTestEnvMgr;
    this.tigerTestEnvMgr.registerNewListener(this);
    this.buildProperties = buildProperties;
  }

  @Override
  public synchronized void receiveTestEnvUpdate(final TigerStatusUpdate update) {
    log.trace("receiving update {}", update);
    try {
      receiveTestSuiteUpdate(update.getFeatureMap());

      update.getServerUpdate().forEach(this::receiveServerStatusUpdate);

      if (update.getBannerMessage() != null) {
        tigerEnvStatus.setBannerMessage(update.getBannerMessage());
        tigerEnvStatus.setBannerColor(update.getBannerColor());
        tigerEnvStatus.setBannerType(update.getBannerType());
        tigerEnvStatus.setBannerDetails(update.getBannerDetails());
        tigerEnvStatus.setBannerIsHtml(update.isBannerIsHtml());
      }
      // TODO make sure to check that the index is the expected next number, if not we do have to
      // cache this and wait for the correct message to be received and then process
      // the cached messages in order, currently this is done on the client side

      // This synchronized block is needed to ensure there is no concurrency when for example
      // receiving updates very fast!
      synchronized (tigerEnvStatus) {
        if (update.getIndex() > tigerEnvStatus.getCurrentIndex()) {
          tigerEnvStatus.setCurrentIndex(update.getIndex());
        }
      }
    } catch (Exception e) {
      log.error("Unable to parse update", e);
    }
  }

  private void receiveTestSuiteUpdate(Map<String, FeatureUpdate> update) {
    update.forEach(
        (featureKey, featureUpdate) -> {
          try {
            if (tigerEnvStatus.getFeatureMap().containsKey(featureKey)) {
              FeatureUpdate feature = tigerEnvStatus.getFeatureMap().get(featureKey);
              updateExistingFeature(featureUpdate, feature);
            } else {
              tigerEnvStatus.getFeatureMap().put(featureKey, featureUpdate);
            }
          } catch (Exception e) {
            log.error("Unable to parse update", e);
          }
        });
  }

  private static void updateExistingFeature(FeatureUpdate featureUpdate, FeatureUpdate feature) {
    if (featureUpdate.getStatus() != TestResult.UNUSED) {
      feature.setStatus(featureUpdate.getStatus());
    }
    feature.setDescription(featureUpdate.getDescription());
    fillInScenarioData(featureUpdate, feature);
  }

  private static void fillInScenarioData(FeatureUpdate featureUpdate, FeatureUpdate feature) {
    featureUpdate
        .getScenarios()
        .forEach(
            (scenarioKey, scenarioUpdate) -> {
              if (feature.getScenarios().containsKey(scenarioKey)) {
                ScenarioUpdate scenario = feature.getScenarios().get(scenarioKey);
                updateExistingScenario(scenarioUpdate, scenario);
              } else {
                feature.getScenarios().put(scenarioKey, scenarioUpdate);
              }
            });
  }

  private static void updateExistingScenario(
      ScenarioUpdate scenarioUpdate, ScenarioUpdate scenario) {
    var newStatus = scenarioUpdate.getStatus();
    if (newStatus != TestResult.UNUSED && newStatus != null) {
      updateScenarioStatus(scenario, newStatus);
    }
    if (!StringUtils.isBlank(scenarioUpdate.getFailureMessage())) {
      scenario.setFailureMessage(scenarioUpdate.getFailureMessage());
    }
    if (!StringUtils.isBlank(scenarioUpdate.getDescription())) {
      scenario.setDescription(scenarioUpdate.getDescription());
    }
    if (scenarioUpdate.getExampleKeys() != null) {
      scenario.setExampleKeys(scenarioUpdate.getExampleKeys());
    }
    if (scenarioUpdate.getExampleList() != null) {
      scenario.setExampleList(scenarioUpdate.getExampleList());
    }
    scenario.setVariantIndex(scenarioUpdate.getVariantIndex());
    scenario.setDryRun(scenarioUpdate.isDryRun());
    fillInStepData(scenarioUpdate, scenario);
  }

  private static void fillInStepData(ScenarioUpdate scenarioUpdate, ScenarioUpdate scenario) {
    scenarioUpdate
        .getSteps()
        .forEach(
            (stepKey, stepUpdate) -> {
              if (scenario.getSteps().containsKey(stepKey)) {
                StepUpdate step = scenario.getSteps().get(stepKey);
                updateExistingStep(scenario, stepUpdate, step);
              } else {
                scenario.getSteps().put(stepKey, stepUpdate);
              }
            });
  }

  private static void updateExistingStep(
      ScenarioUpdate scenario, StepUpdate stepUpdate, StepUpdate step) {
    if (stepUpdate.getStatus() != null) {
      fillInStatus(scenario, stepUpdate, step);
    }
    updateStep(stepUpdate, step);
  }

  private static void updateStep(StepUpdate stepUpdate, StepUpdate step) {
    if (stepUpdate.getStatus() != TestResult.FAILED) {
      step.setFailureMessage(null);
      step.setFailureStacktrace(null);
    } else {
      if (!StringUtils.isBlank(stepUpdate.getFailureMessage())) {
        step.setFailureMessage(stepUpdate.getFailureMessage());
      }
      if (!StringUtils.isBlank(stepUpdate.getFailureStacktrace())) {
        step.setFailureStacktrace(stepUpdate.getFailureStacktrace());
      }
    }
    if (!StringUtils.isBlank(stepUpdate.getDescription())) {
      step.setDescription(stepUpdate.getDescription());
    }
    if (!StringUtils.isBlank(stepUpdate.getTooltip())) {
      step.setTooltip(stepUpdate.getTooltip());
    }
    step.setStepIndex(stepUpdate.getStepIndex());
    fillInMetaData(stepUpdate, step);
    fillInStepData(stepUpdate, step);
  }

  private static void fillInStepData(StepUpdate stepUpdate, StepUpdate step) {
    if (stepUpdate.getStatus() == TestResult.PENDING) {
      step.getSubSteps().clear();
      return;
    }
    AtomicInteger stepIndex = new AtomicInteger(0);
    stepUpdate
        .getSubSteps()
        .forEach(
            subStepUpdate -> {
              if (stepIndex.get() < step.getSubSteps().size()) {
                StepUpdate subStep = step.getSubSteps().get(stepIndex.get());
                if (subStepUpdate.getStatus() != null) {
                  subStep.setStatus(subStepUpdate.getStatus());
                }
                updateStep(subStepUpdate, subStep);
              } else {
                step.getSubSteps().add(subStepUpdate);
              }
              stepIndex.incrementAndGet();
            });
  }

  private static void fillInMetaData(StepUpdate stepUpdate, StepUpdate step) {
    List<MessageMetaDataDto> newMetaData = stepUpdate.getRbelMetaData();
    if (newMetaData != null) {
      if (step.getRbelMetaData() == null) {
        step.setRbelMetaData(new ArrayList<>());
      }
      step.getRbelMetaData().addAll(newMetaData);
    }
  }

  private static void fillInStatus(
      ScenarioUpdate scenario, StepUpdate stepUpdate, StepUpdate step) {
    TestResult newStatus = stepUpdate.getStatus();
    if (newStatus != TestResult.UNUSED && newStatus != null) {
      if (step.getStatus() == null || step.getStatus().ordinal() > newStatus.ordinal()) {
        step.setStatus(newStatus);
      }
      updateScenarioStatus(scenario, newStatus);
    }
  }

  private static void updateScenarioStatus(ScenarioUpdate scenario, TestResult newStatus) {
    if (scenario.getStatus() == null || scenario.getStatus().ordinal() > newStatus.ordinal()) {
      scenario.setStatus(newStatus);
    }
  }

  private synchronized void receiveServerStatusUpdate(
      final String serverName, final TigerServerStatusUpdate statusUpdate) {
    log.trace("Status update for server {}", serverName);
    final TigerServerStatusDto serverStatus =
        tigerEnvStatus.getServers().getOrDefault(serverName, new TigerServerStatusDto());
    serverStatus.setName(serverName);
    if (statusUpdate.getStatus() != null) {
      serverStatus.setStatus(statusUpdate.getStatus());
    }
    if (statusUpdate.getType() != null) {
      serverStatus.setType(statusUpdate.getType());
    }
    if (statusUpdate.getBaseUrl() != null) {
      serverStatus.setBaseUrl(statusUpdate.getBaseUrl());
    }
    if (statusUpdate.getStatusMessage() != null) {
      serverStatus.setStatusMessage(statusUpdate.getStatusMessage());
      serverStatus.getStatusUpdates().add(statusUpdate.getStatusMessage());
    }
    tigerEnvStatus.getServers().put(serverName, serverStatus);
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public TigerEnvStatusDto getStatus() {
    log.trace("Fetch request to getStatus() received");
    tigerTestEnvMgr.setWorkflowUiSentFetch(true);
    log.trace("Sending test env status {}", tigerEnvStatus);
    return tigerEnvStatus;
  }

  @GetMapping(path = "/confirmShutdown")
  public void getConfirmShutdown() {
    log.trace("Received shutdown confirmation");
    tigerTestEnvMgr.receivedQuitConfirmationFromWorkflowUi();
  }

  @GetMapping(path = "/continueExecution")
  public void getConfirmContinueExecution() {
    log.trace("Received confirmation to continue test execution");
    tigerTestEnvMgr.receivedConfirmationFromWorkflowUi(false);
  }

  @GetMapping(path = "/failExecution")
  public void getConfirmToFailExecution() {
    log.trace("Received confirmation for failing test step");
    tigerTestEnvMgr.receivedConfirmationFromWorkflowUi(true);
    TigerStatusUpdate update =
        TigerStatusUpdate.builder()
            .bannerMessage("Failing test run")
            .bannerType(BannerType.MESSAGE)
            .bannerColor("red")
            .build();
    tigerTestEnvMgr.receiveTestEnvUpdate(update);
  }

  @GetMapping(path = "/version")
  public String getTigerVersion() {
    String version = buildProperties.tigerVersionAsString();
    log.trace("Fetch requests the tiger version {}", version);
    return version;
  }

  @GetMapping(path = "/build")
  public String getBuildDate() {
    String buildDate = buildProperties.tigerBuildDateAsString();
    log.trace("Fetch requests the build date {}", buildDate);
    return buildDate;
  }
}
