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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.spring_utils.TigerBuildPropertiesService;
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

    envMgr.receiveTestEnvUpdate(
        TigerStatusUpdate.builder()
            .featureMap(
                new LinkedHashMap<>(
                    Map.of(
                        "feature",
                        FeatureUpdate.builder()
                            .description("feature")
                            .scenarios(
                                new LinkedHashMap<>(
                                    Map.of(
                                        "scenario",
                                        ScenarioUpdate.builder()
                                            .description("scenario")
                                            .steps(
                                                new LinkedHashMap<>(
                                                    Map.of(
                                                        "0",
                                                        StepUpdate.builder()
                                                            .description("step")
                                                            .build())))
                                            .build())))
                            .build())))
            .build());

    assertThat(envStatusController.getStatus().getFeatureMap().get("feature").getDescription())
        .isEqualTo("feature");
    assertThat(
            envStatusController
                .getStatus()
                .getFeatureMap()
                .get("feature")
                .getScenarios()
                .get("scenario")
                .getDescription())
        .isEqualTo("scenario");
    assertThat(
            envStatusController
                .getStatus()
                .getFeatureMap()
                .get("feature")
                .getScenarios()
                .get("scenario")
                .getSteps()
                .get("0")
                .getDescription())
        .isEqualTo("step");
  }

  @Test
  @TigerTest(tigerYaml = "localProxyActive: false")
  void mergeStepsOfScenario(final TigerTestEnvMgr envMgr) {
    final EnvStatusController envStatusController =
        new EnvStatusController(envMgr, mock(TigerBuildPropertiesService.class));

    assertThat(envStatusController.getStatus().getFeatureMap()).isEmpty();

    envMgr.receiveTestEnvUpdate(
        TigerStatusUpdate.builder()
            .featureMap(
                new LinkedHashMap<>(
                    Map.of(
                        "feature",
                        FeatureUpdate.builder()
                            .description("feature")
                            .scenarios(
                                new LinkedHashMap<>(
                                    Map.of(
                                        "scenario",
                                        ScenarioUpdate.builder()
                                            .description("scenario")
                                            .steps(
                                                new LinkedHashMap<>(
                                                    Map.of(
                                                        "0",
                                                        StepUpdate.builder()
                                                            .description("step0")
                                                            .build())))
                                            .build())))
                            .build())))
            .build());
    envMgr.receiveTestEnvUpdate(
        TigerStatusUpdate.builder()
            .featureMap(
                new LinkedHashMap<>(
                    Map.of(
                        "feature",
                        FeatureUpdate.builder()
                            .description("feature")
                            .scenarios(
                                new LinkedHashMap<>(
                                    Map.of(
                                        "scenario",
                                        ScenarioUpdate.builder()
                                            .description("scenario")
                                            .steps(
                                                new LinkedHashMap<>(
                                                    Map.of(
                                                        "0",
                                                        StepUpdate.builder()
                                                            .description("step00")
                                                            .status(TestResult.PASSED)
                                                            .build(),
                                                        "1",
                                                        StepUpdate.builder()
                                                            .description("step1")
                                                            .build())))
                                            .build())))
                            .build())))
            .build());

    assertThat(envStatusController.getStatus().getFeatureMap().get("feature").getDescription())
        .isEqualTo("feature");
    assertThat(
            envStatusController
                .getStatus()
                .getFeatureMap()
                .get("feature")
                .getScenarios()
                .get("scenario")
                .getDescription())
        .isEqualTo("scenario");
    assertThat(
            envStatusController
                .getStatus()
                .getFeatureMap()
                .get("feature")
                .getScenarios()
                .get("scenario")
                .getSteps()
                .get("0")
                .getDescription())
        .isEqualTo("step00");
    assertThat(
            envStatusController
                .getStatus()
                .getFeatureMap()
                .get("feature")
                .getScenarios()
                .get("scenario")
                .getSteps()
                .get("0")
                .getStatus())
        .isEqualTo(TestResult.PASSED);
    assertThat(
            envStatusController
                .getStatus()
                .getFeatureMap()
                .get("feature")
                .getScenarios()
                .get("scenario")
                .getSteps()
                .get("1")
                .getDescription())
        .isEqualTo("step1");
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
          "localProxyActive: false\n"
              + "servers:\n"
              + "  winstoneServer:\n"
              + "    type: externalJar\n"
              + "    source:\n"
              + "      - local:target/winstone.jar\n"
              + "    healthcheckUrl: http://127.0.0.1:${free.port.0}\n"
              + "    healthcheckReturnCode: 200\n"
              + "    externalJarOptions:\n"
              + "      arguments:\n"
              + "        - --httpPort=${free.port.0}\n"
              + "        - --webroot=.\n",
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
