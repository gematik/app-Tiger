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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.server.TigerBuildPropertiesService;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.env.*;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerStatus;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.test.util.ReflectionTestUtils;

@Slf4j
class EnvStatusControllerTest {

  @Test
  @TigerTest(tigerYaml = "localProxyActive: false")
  void displayMessage_shouldPushToClient(final TigerTestEnvMgr envMgr) {
    final EnvStatusController envStatusController =
        new EnvStatusController(envMgr, mock(TigerBuildPropertiesService.class));

    assertThat(envStatusController.getStatus().getFeatureMap()).isEmpty();

    String featureKey = "featureKey";
    String featureDescription = "feature";
    String scenarioKey = "scenarioKey";
    String scenarioDescription = "scenario";
    String stepKey = "0";
    String stepDescription = "step";
    String stepTooltip = "stepTooltip";

    StepUpdate stepUpdate =
        StepUpdate.builder().description(stepDescription).tooltip(stepTooltip).build();
    ScenarioUpdate scenarioUpdate =
        ScenarioUpdate.builder()
            .description(scenarioDescription)
            .steps(convertToLinkedHashMap(stepKey, stepUpdate))
            .build();
    FeatureUpdate featureUpdate =
        FeatureUpdate.builder()
            .description(featureDescription)
            .scenarios(convertToLinkedHashMap(scenarioKey, scenarioUpdate))
            .build();
    envMgr.receiveTestEnvUpdate(
        TigerStatusUpdate.builder()
            .featureMap(convertToLinkedHashMap(featureKey, featureUpdate))
            .build());

    FeatureUpdate feature = envStatusController.getStatus().getFeatureMap().get(featureKey);
    assertThat(feature.getDescription()).isEqualTo(featureDescription);
    ScenarioUpdate scenario = feature.getScenarios().get(scenarioKey);
    assertThat(scenario.getDescription()).isEqualTo(scenarioDescription);
    StepUpdate step = scenario.getSteps().get(stepKey);
    assertThat(step.getDescription()).isEqualTo(stepDescription);
    assertThat(step.getTooltip()).isEqualTo(stepTooltip);
  }

  private static @NotNull <T> LinkedHashMap<String, T> convertToLinkedHashMap(String key, T value) {
    return new LinkedHashMap<>(Map.of(key, value));
  }

  @Test
  @TigerTest(tigerYaml = "localProxyActive: false")
  void mergeStepsOfScenario(final TigerTestEnvMgr envMgr) {
    final EnvStatusController envStatusController =
        new EnvStatusController(envMgr, mock(TigerBuildPropertiesService.class));

    assertThat(envStatusController.getStatus().getFeatureMap()).isEmpty();

    ScenarioUpdate firstScenarioUpdate =
        ScenarioUpdate.builder()
            .description("scenario")
            .steps(
                convertToLinkedHashMap(
                    "0",
                    StepUpdate.builder().description("step0").status(TestResult.PASSED).build()))
            .build();
    FeatureUpdate firstFeatureUpdate =
        FeatureUpdate.builder()
            .description("feature")
            .scenarios(convertToLinkedHashMap("scenario", firstScenarioUpdate))
            .build();
    envMgr.receiveTestEnvUpdate(
        TigerStatusUpdate.builder()
            .featureMap(convertToLinkedHashMap("feature", firstFeatureUpdate))
            .build());

    assertThat(
            envStatusController
                .getStatus()
                .getFeatureMap()
                .get("feature")
                .getScenarios()
                .get("scenario")
                .getSteps()
                .get("0")
                .getTooltip())
        .isNull();

    ScenarioUpdate nextScenarioUpdate =
        ScenarioUpdate.builder()
            .description("scenario")
            .steps(
                Map.of(
                    "0",
                    StepUpdate.builder()
                        .description("step00")
                        .tooltip("tooltip")
                        .status(TestResult.PASSED)
                        .build(),
                    "1",
                    StepUpdate.builder().description("step1").status(TestResult.FAILED).build()))
            .build();
    FeatureUpdate nextFeatureUpdate =
        FeatureUpdate.builder()
            .description("feature")
            .scenarios(convertToLinkedHashMap("scenario", nextScenarioUpdate))
            .build();
    envMgr.receiveTestEnvUpdate(
        TigerStatusUpdate.builder()
            .featureMap(convertToLinkedHashMap("feature", nextFeatureUpdate))
            .build());

    FeatureUpdate feature = envStatusController.getStatus().getFeatureMap().get("feature");
    assertThat(feature.getDescription()).isEqualTo("feature");
    ScenarioUpdate scenario = feature.getScenarios().get("scenario");
    assertThat(scenario.getDescription()).isEqualTo("scenario");
    assertThat(scenario.getStatus()).isEqualTo(TestResult.FAILED);
    StepUpdate step0 = scenario.getSteps().get("0");
    assertThat(step0.getDescription()).isEqualTo("step00");
    assertThat(step0.getStatus()).isEqualTo(TestResult.PASSED);
    assertThat(step0.getTooltip()).isEqualTo("tooltip");
    StepUpdate step1 = scenario.getSteps().get("1");
    assertThat(step1.getDescription()).isEqualTo("step1");
    assertThat(step1.getStatus()).isEqualTo(TestResult.FAILED);
    assertThat(step1.getTooltip()).isNull();
  }

  @Test
  @TigerTest(tigerYaml = "localProxyActive: false")
  void checkBannerMessages(final TigerTestEnvMgr envMgr) {
    final EnvStatusController envStatusController =
        new EnvStatusController(envMgr, mock(TigerBuildPropertiesService.class));

    assertThat(envStatusController.getStatus().getFeatureMap()).isEmpty();

    envMgr.receiveTestEnvUpdate(
        TigerStatusUpdate.builder().bannerColor("green").bannerMessage("bannertest").build());

    assertThat(envStatusController.getStatus().getBannerMessage()).isEqualTo("bannertest");
    assertThat(envStatusController.getStatus().getBannerColor()).isEqualTo("green");
  }

  @Test
  @TigerTest(
      tigerYaml =
          """
                localProxyActive: false
                servers:
                  winstoneServer:
                    type: externalJar
                    source:
                      - local:target/winstone.jar
                    healthcheckUrl: http://127.0.0.1:${free.port.0}
                    healthcheckReturnCode: 200
                    externalJarOptions:
                      arguments:
                        - --httpPort=${free.port.0}
                        - --webroot=.
                """,
      skipEnvironmentSetup = true)
  void verifyServerStatusDuringStartup(final TigerTestEnvMgr envMgr) {
    try {
      final AtomicBoolean downloadShouldProceed = new AtomicBoolean(false);

      final DownloadManager mockDownloadManager = mock(DownloadManager.class);
      ReflectionTestUtils.setField(envMgr, "downloadManager", mockDownloadManager);
      when(mockDownloadManager.downloadJarAndReturnFile(any(), any(), any()))
          .thenAnswer(
              (Answer<File>)
                  invocation -> {
                    await().until(downloadShouldProceed::get);
                    return new File("target/winstone.jar");
                  });

      final EnvStatusController envStatusController =
          envMgr.getListeners().stream()
              .filter(EnvStatusController.class::isInstance)
              .map(EnvStatusController.class::cast)
              .findAny()
              .orElseThrow();

      new Thread(envMgr::setUpEnvironment).start();

      await()
          .until(
              () ->
                  envStatusController.getStatus().getServers().containsKey("winstoneServer")
                      && envStatusController
                              .getStatus()
                              .getServers()
                              .get("winstoneServer")
                              .getStatus()
                          == TigerServerStatus.STARTING);

      assertThat(envStatusController.getStatus().getServers().get("winstoneServer"))
          .hasFieldOrPropertyWithValue("name", "winstoneServer")
          .hasFieldOrPropertyWithValue("status", TigerServerStatus.STARTING);
      await()
          .until(
              () ->
                  envStatusController
                      .getStatus()
                      .getServers()
                      .get("winstoneServer")
                      .getStatusMessage()
                      .matches("Starting external jar instance winstoneServer in folder .*"));

      downloadShouldProceed.set(true);

      await()
          .until(
              () ->
                  envStatusController.getStatus().getServers().get("winstoneServer").getStatus()
                      == TigerServerStatus.RUNNING);

      assertThat(envStatusController.getStatus().getServers().get("winstoneServer"))
          .hasFieldOrPropertyWithValue("name", "winstoneServer")
          .hasFieldOrPropertyWithValue("status", TigerServerStatus.RUNNING)
          // TODO TGR-491 message are not always in correct order
          //  .hasFieldOrPropertyWithValue("statusMessage", "winstoneServer READY")
          .hasFieldOrPropertyWithValue(
              "baseUrl",
              TigerGlobalConfiguration.resolvePlaceholders("http://127.0.0.1:${free.port.0}"));
    } finally {
      envMgr.shutDown();
    }
  }

  @Test
  @TigerTest(tigerYaml = "localProxyActive: true", skipEnvironmentSetup = true)
  void test_webUiUrlShouldBeSet(final TigerTestEnvMgr envMgr) {
    final EnvStatusController envStatusController =
        new EnvStatusController(envMgr, mock(TigerBuildPropertiesService.class));

    assertThat(envMgr.getLocalTigerProxyOptional()).isEmpty();
    envMgr.setUpEnvironment();
    assertThat(envMgr.getLocalTigerProxyOptional()).isNotEmpty();
    await("Check env status controller has received the proxy web ui url with in 4 seconds")
        .pollDelay(200, TimeUnit.MILLISECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .atMost(4, TimeUnit.SECONDS)
        .until(
            () ->
                !StringUtils.isEmpty(
                    envStatusController
                        .getStatus()
                        .getServers()
                        .get(TigerTestEnvMgr.LOCAL_TIGER_PROXY_TYPE)
                        .getBaseUrl()));
  }
}
