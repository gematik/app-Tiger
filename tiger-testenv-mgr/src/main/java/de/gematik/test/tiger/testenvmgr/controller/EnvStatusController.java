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

package de.gematik.test.tiger.testenvmgr.controller;

import de.gematik.test.tiger.spring_utils.TigerBuildPropertiesService;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.data.BannerType;
import de.gematik.test.tiger.testenvmgr.data.TigerEnvStatusDto;
import de.gematik.test.tiger.testenvmgr.data.TigerServerStatusDto;
import de.gematik.test.tiger.testenvmgr.env.*;
import java.util.ArrayList;
import java.util.Map;
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
        (key, value) -> {
          try {
            if (tigerEnvStatus.getFeatureMap().containsKey(key)) {
              FeatureUpdate feature = tigerEnvStatus.getFeatureMap().get(key);
              if (value.getStatus() != TestResult.UNUSED) {
                feature.setStatus(value.getStatus());
              }
              feature.setDescription(value.getDescription());
              fillInScenarioData(value, feature);
            } else {
              tigerEnvStatus.getFeatureMap().put(key, value);
            }
          } catch (Exception e) {
            log.error("Unable to parse update", e);
          }
        });
  }

  private static void fillInScenarioData(FeatureUpdate value, FeatureUpdate feature) {
    value
        .getScenarios()
        .forEach(
            (skey, svalue) -> {
              if (feature.getScenarios().containsKey(skey)) {
                ScenarioUpdate scenario = feature.getScenarios().get(skey);
                if (svalue.getStatus() != TestResult.UNUSED) {
                  scenario.setStatus(svalue.getStatus());
                }
                scenario.setDescription(svalue.getDescription());
                scenario.setExampleKeys(svalue.getExampleKeys());
                scenario.setExampleList(svalue.getExampleList());
                scenario.setVariantIndex(svalue.getVariantIndex());
                fillInStepData(svalue, scenario);
              } else {
                feature.getScenarios().put(skey, svalue);
              }
            });
  }

  private static void fillInStepData(ScenarioUpdate svalue, ScenarioUpdate scenario) {
    svalue
        .getSteps()
        .forEach(
            (stkey, stvalue) -> {
              if (scenario.getSteps().containsKey(stkey)) {
                StepUpdate step = scenario.getSteps().get(stkey);
                if (stvalue.getStatus() != TestResult.UNUSED) {
                  step.setStatus(stvalue.getStatus());
                }
                step.setDescription(stvalue.getDescription());
                step.setStepIndex(stvalue.getStepIndex());
                if (stvalue.getRbelMetaData() != null) {
                  if (step.getRbelMetaData() == null) {
                    step.setRbelMetaData(new ArrayList<>());
                  }
                  step.getRbelMetaData().addAll(stvalue.getRbelMetaData());
                }
              } else {
                scenario.getSteps().put(stkey, stvalue);
              }
            });
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
    tigerTestEnvMgr.receivedConfirmationFromWorkflowUi(false);
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
    log.trace("Fetch requests the tiger version {}", buildProperties.tigerVersionAsString());
    return buildProperties.tigerVersionAsString();
  }

  @GetMapping(path = "/build")
  public String getBuildDate() {
    log.trace("Fetch requests the build date ", buildProperties.tigerBuildDateAsString());
    return buildProperties.tigerBuildDateAsString();
  }
}
