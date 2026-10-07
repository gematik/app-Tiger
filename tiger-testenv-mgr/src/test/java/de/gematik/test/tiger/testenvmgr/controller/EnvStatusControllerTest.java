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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.renderer.MessageMetaDataDto;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.server.TigerBuildPropertiesService;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.data.BannerType;
import de.gematik.test.tiger.testenvmgr.data.TestSuiteLifecycle;
import de.gematik.test.tiger.testenvmgr.env.*;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerStatus;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
    String featureSourcePath = "featureSourcePath";
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
            .sourcePath(featureSourcePath)
            .scenarios(convertToLinkedHashMap(scenarioKey, scenarioUpdate))
            .build();
    envMgr.receiveTestEnvUpdate(
        TigerStatusUpdate.builder()
            .featureMap(convertToLinkedHashMap(featureKey, featureUpdate))
            .build());

    FeatureUpdate feature = envStatusController.getStatus().getFeatureMap().get(featureKey);
    assertThat(feature.getDescription()).isEqualTo(featureDescription);
    assertThat(feature.getSourcePath()).isEqualTo(featureSourcePath);
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
            .sourcePath("featureSourcePath")
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
            .sourcePath("featureSourcePath")
            .description("feature")
            .scenarios(convertToLinkedHashMap("scenario", nextScenarioUpdate))
            .build();
    envMgr.receiveTestEnvUpdate(
        TigerStatusUpdate.builder()
            .featureMap(convertToLinkedHashMap("feature", nextFeatureUpdate))
            .build());

    FeatureUpdate feature = envStatusController.getStatus().getFeatureMap().get("feature");
    assertThat(feature.getDescription()).isEqualTo("feature");
    assertThat(feature.getSourcePath()).isEqualTo("featureSourcePath");
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
            httpbinServer:
              type: externalJar
              source:
                - local:target/tiger-httpbin.jar
              healthcheckUrl: http://127.0.0.1:${free.port.0}
              healthcheckReturnCode: 200
              externalJarOptions:
                arguments:
                  - -port=${free.port.0}
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
                    return new File("target/tiger-httpbin.jar");
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
                  envStatusController.getStatus().getServers().containsKey("httpbinServer")
                      && envStatusController
                              .getStatus()
                              .getServers()
                              .get("httpbinServer")
                              .getStatus()
                          == TigerServerStatus.STARTING);

      assertThat(envStatusController.getStatus().getServers().get("httpbinServer"))
          .hasFieldOrPropertyWithValue("name", "httpbinServer")
          .hasFieldOrPropertyWithValue("status", TigerServerStatus.STARTING);
      await()
          .until(
              () ->
                  envStatusController
                      .getStatus()
                      .getServers()
                      .get("httpbinServer")
                      .getStatusMessage()
                      .matches("Starting external jar instance httpbinServer in folder .*"));

      downloadShouldProceed.set(true);

      await()
          .until(
              () ->
                  envStatusController.getStatus().getServers().get("httpbinServer").getStatus()
                      == TigerServerStatus.RUNNING);

      assertThat(envStatusController.getStatus().getServers().get("httpbinServer"))
          .hasFieldOrPropertyWithValue("name", "httpbinServer")
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

  @Test
  @TigerTest
  void removeMessage_shouldCauseUpdate(TigerTestEnvMgr envMgr)
      throws ExecutionException, InterruptedException, TimeoutException {

    // register message UUID remove handler
    envMgr.initializeLocalProxyCallbacks();

    var updateFuture = getTigerStatusUpdate(envMgr);

    var converter = envMgr.getLocalTigerProxyOrFail().getRbelLogger().getRbelConverter();

    var message = converter.parseMessage("{'foo':'bar'}".getBytes(), new RbelMessageMetadata());

    converter.removeMessage(message);

    var update = updateFuture.get(1, TimeUnit.SECONDS);
    assertThat(update.getRemovedMessageUuids()).containsExactly(message.getUuid());
  }

  private static @NotNull CompletableFuture<TigerStatusUpdate> getTigerStatusUpdate(
      TigerTestEnvMgr envMgr) {
    envMgr.getListeners().clear();
    var updateFuture = new CompletableFuture<TigerStatusUpdate>();
    envMgr.registerNewListener(
        update -> {
          if (update.getRemovedMessageUuids() != null) {
            updateFuture.complete(update);
          }
        });
    return updateFuture;
  }

  @Test
  @TigerTest
  void clearMessages_shouldCauseUpdate(TigerTestEnvMgr envMgr)
      throws ExecutionException, InterruptedException, TimeoutException {

    // register message UUID remove handler
    envMgr.initializeLocalProxyCallbacks();

    var updateFuture = getTigerStatusUpdate(envMgr);

    var converter = envMgr.getLocalTigerProxyOrFail().getRbelLogger().getRbelConverter();

    var message = converter.parseMessage("{'foo':'bar'}".getBytes(), new RbelMessageMetadata());

    converter.clearAllMessages();

    var update = updateFuture.get(1, TimeUnit.SECONDS);

    assertThat(update.getRemovedMessageUuids()).containsExactly(message.getUuid());
  }

  @Test
  @TigerTest
  void removeMessageWithoutHandler_shouldNotCauseUpdate(TigerTestEnvMgr envMgr) {

    var updateFuture = getTigerStatusUpdate(envMgr);

    var converter = envMgr.getLocalTigerProxyOrFail().getRbelLogger().getRbelConverter();

    converter.getKnownMessageUuids().clearRemovedMessageUuidsHandlers();

    var message = converter.parseMessage("{'foo':'bar'}".getBytes(), new RbelMessageMetadata());

    converter.removeMessage(message);

    assertThatThrownBy(() -> updateFuture.get(500, TimeUnit.MILLISECONDS))
        .isInstanceOf(TimeoutException.class);
  }

  @Test
  @TigerTest
  void clearMessagesWithoutHandler_shouldNotCauseUpdate(TigerTestEnvMgr envMgr) {

    var updateFuture = getTigerStatusUpdate(envMgr);

    var converter = envMgr.getLocalTigerProxyOrFail().getRbelLogger().getRbelConverter();

    converter.getKnownMessageUuids().clearRemovedMessageUuidsHandlers();

    var message = converter.parseMessage("{'foo':'bar'}".getBytes(), new RbelMessageMetadata());

    converter.clearAllMessages();

    assertThatThrownBy(() -> updateFuture.get(500, TimeUnit.MILLISECONDS))
        .isInstanceOf(TimeoutException.class);
  }

  @Test
  @TigerTest
  void clearMessagesWhenHistoryEmtpy_shouldNotCauseUpdate(TigerTestEnvMgr envMgr) {

    var converter = envMgr.getLocalTigerProxyOrFail().getRbelLogger().getRbelConverter();

    converter.clearAllMessages();

    envMgr.initializeLocalProxyCallbacks();

    var updateFuture = getTigerStatusUpdate(envMgr);

    converter.clearAllMessages();

    assertThatThrownBy(() -> updateFuture.get(500, TimeUnit.MILLISECONDS))
        .isInstanceOf(TimeoutException.class);
  }

  @Test
  void receiveTestEnvUpdate_mergesNewStepsIntoStoredImmutableMap() {
    EnvStatusController controller =
        controllerWithScenario(
            scenarioWithSteps(
                Map.of("existing", StepUpdate.builder().description("old description").build())));

    ScenarioUpdate update =
        ScenarioUpdate.builder()
            .steps(
                Map.of(
                    "existing", StepUpdate.builder().tooltip("new tooltip").build(),
                    "new", StepUpdate.builder().description("new step").build()))
            .build();
    controller.receiveTestEnvUpdate(statusUpdate(update));

    Map<String, StepUpdate> steps = scenarioFrom(controller).getSteps();
    assertThat(steps).containsKeys("existing", "new");
    assertThat(steps.get("existing").getDescription()).isEqualTo("old description");
    assertThat(steps.get("existing").getTooltip()).isEqualTo("new tooltip");
    assertThat(steps.get("new").getDescription()).isEqualTo("new step");
  }

  @Test
  void receiveTestEnvUpdate_keepsExistingScenarioValuesWhenUpdateOmitsThem() {
    ScenarioUpdate existing =
        ScenarioUpdate.builder()
            .description("description")
            .failureMessage("failure")
            .status(TestResult.FAILED)
            .exampleKeys(List.of("key"))
            .exampleList(Map.of("column", "value"))
            .tags(List.of("tag"))
            .build();
    EnvStatusController controller = controllerWithScenario(existing);

    controller.receiveTestEnvUpdate(
        statusUpdate(
            ScenarioUpdate.builder()
                .description("")
                .failureMessage("")
                .status(TestResult.UNUSED)
                .exampleKeys(null)
                .exampleList(null)
                .tags(null)
                .variantIndex(3)
                .isDryRun(true)
                .build()));

    ScenarioUpdate actual = scenarioFrom(controller);
    assertThat(actual.getDescription()).isEqualTo("description");
    assertThat(actual.getFailureMessage()).isEqualTo("failure");
    assertThat(actual.getStatus()).isEqualTo(TestResult.FAILED);
    assertThat(actual.getExampleKeys()).containsExactly("key");
    assertThat(actual.getExampleList()).containsEntry("column", "value");
    assertThat(actual.getTags()).containsExactly("tag");
    assertThat(actual.getVariantIndex()).isEqualTo(3);
    assertThat(actual.isDryRun()).isTrue();
  }

  @Test
  void receiveTestEnvUpdate_updatesScenarioStatusAndFailureMessage() {
    EnvStatusController controller =
        controllerWithScenario(ScenarioUpdate.builder().status(TestResult.EXECUTING).build());

    controller.receiveTestEnvUpdate(
        statusUpdate(
            ScenarioUpdate.builder()
                .status(TestResult.FAILED)
                .failureMessage("scenario failed")
                .build()));

    ScenarioUpdate actual = scenarioFrom(controller);
    assertThat(actual.getStatus()).isEqualTo(TestResult.FAILED);
    assertThat(actual.getFailureMessage()).isEqualTo("scenario failed");
  }

  @Test
  void receiveTestEnvUpdate_mergesFailureMetadataAndNestedSteps() {
    StepUpdate failedStep =
        StepUpdate.builder()
            .status(TestResult.FAILED)
            .failureMessage("old failure")
            .failureStacktrace("old stack")
            .rbelMetaData(null)
            .subSteps(
                new ArrayList<>(
                    List.of(
                        StepUpdate.builder()
                            .status(TestResult.FAILED)
                            .description("old child")
                            .build())))
            .build();
    EnvStatusController controller =
        controllerWithScenario(scenarioWithSteps(Map.of("step", failedStep)));

    StepUpdate update =
        StepUpdate.builder()
            .status(TestResult.FAILED)
            .failureMessage("new failure")
            .failureStacktrace("new stack")
            .rbelMetaData(List.of(MessageMetaDataDto.builder().uuid("message").build()))
            .subSteps(
                List.of(
                    StepUpdate.builder()
                        .status(TestResult.PASSED)
                        .description("updated child")
                        .build(),
                    StepUpdate.builder().description("new child").build()))
            .build();
    controller.receiveTestEnvUpdate(statusUpdate(scenarioWithSteps(Map.of("step", update))));

    StepUpdate actual = scenarioFrom(controller).getSteps().get("step");
    assertThat(actual.getFailureMessage()).isEqualTo("new failure");
    assertThat(actual.getFailureStacktrace()).isEqualTo("new stack");
    assertThat(actual.getRbelMetaData())
        .extracting(MessageMetaDataDto::getUuid)
        .containsExactly("message");
    assertThat(actual.getSubSteps())
        .extracting(StepUpdate::getDescription)
        .containsExactly("updated child", "new child");
    assertThat(actual.getSubSteps().get(0).getStatus()).isEqualTo(TestResult.PASSED);
  }

  @Test
  void receiveTestEnvUpdate_addsScenarioToExistingFeature() {
    EnvStatusController controller =
        controllerWithScenario(ScenarioUpdate.builder().description("existing").build());
    FeatureUpdate featureUpdate =
        FeatureUpdate.builder()
            .scenarios(
                linkedMap("new-scenario", ScenarioUpdate.builder().description("new").build()))
            .build();

    controller.receiveTestEnvUpdate(
        TigerStatusUpdate.builder().featureMap(linkedMap("feature", featureUpdate)).build());

    Map<String, ScenarioUpdate> scenarios =
        controller.getStatus().getFeatureMap().get("feature").getScenarios();
    assertThat(scenarios).containsKeys("scenario", "new-scenario");
    assertThat(scenarios.get("new-scenario").getDescription()).isEqualTo("new");
  }

  @Test
  void receiveTestEnvUpdate_keepsFailureDetailsWhenIncomingFailureFieldsAreBlank() {
    StepUpdate failedStep =
        StepUpdate.builder()
            .status(TestResult.FAILED)
            .failureMessage("failure")
            .failureStacktrace("stack")
            .mismatchNotes(java.util.Set.of())
            .build();
    EnvStatusController controller =
        controllerWithScenario(scenarioWithSteps(Map.of("step", failedStep)));
    StepUpdate blankFailureUpdate =
        StepUpdate.builder()
            .status(TestResult.FAILED)
            .failureMessage("")
            .failureStacktrace(" ")
            .mismatchNotes(null)
            .rbelMetaData(null)
            .build();

    controller.receiveTestEnvUpdate(
        statusUpdate(scenarioWithSteps(Map.of("step", blankFailureUpdate))));

    StepUpdate actual = scenarioFrom(controller).getSteps().get("step");
    assertThat(actual.getFailureMessage()).isEqualTo("failure");
    assertThat(actual.getFailureStacktrace()).isEqualTo("stack");
    assertThat(actual.getMismatchNotes()).isEmpty();
  }

  @Test
  void receiveTestEnvUpdate_updatesStepDescriptionWhenIncomingStatusIsNull() {
    StepUpdate existing =
        StepUpdate.builder().status(TestResult.PASSED).description("before").build();
    EnvStatusController controller =
        controllerWithScenario(scenarioWithSteps(Map.of("step", existing)));
    StepUpdate descriptionOnlyUpdate =
        StepUpdate.builder().description("after").status(null).rbelMetaData(null).build();

    controller.receiveTestEnvUpdate(
        statusUpdate(scenarioWithSteps(Map.of("step", descriptionOnlyUpdate))));

    StepUpdate actual = scenarioFrom(controller).getSteps().get("step");
    assertThat(actual.getDescription()).isEqualTo("after");
    assertThat(actual.getStatus()).isEqualTo(TestResult.PASSED);
  }

  @Test
  void receiveTestEnvUpdate_clearsFailureAndSubstepsForPassingAndPendingUpdates() {
    StepUpdate passingStep =
        StepUpdate.builder()
            .status(TestResult.FAILED)
            .failureMessage("failure")
            .failureStacktrace("stack")
            .subSteps(new ArrayList<>(List.of(StepUpdate.builder().description("remove").build())))
            .build();
    StepUpdate pendingStep =
        StepUpdate.builder()
            .status(TestResult.EXECUTING)
            .subSteps(new ArrayList<>(List.of(StepUpdate.builder().description("remove").build())))
            .build();
    EnvStatusController controller =
        controllerWithScenario(
            scenarioWithSteps(Map.of("passing", passingStep, "pending", pendingStep)));

    controller.receiveTestEnvUpdate(
        statusUpdate(
            scenarioWithSteps(
                Map.of(
                    "passing", StepUpdate.builder().status(TestResult.PASSED).build(),
                    "pending", StepUpdate.builder().status(TestResult.PENDING).build()))));

    ScenarioUpdate actual = scenarioFrom(controller);
    assertThat(actual.getSteps().get("passing").getFailureMessage()).isNull();
    assertThat(actual.getSteps().get("passing").getFailureStacktrace()).isNull();
    assertThat(actual.getSteps().get("pending").getSubSteps()).isEmpty();
  }

  @Test
  void receiveTestEnvUpdate_updatesServerBannerLifecycleAndOnlyAdvancesIndex() {
    EnvStatusController controller =
        new EnvStatusController(
            mock(TigerTestEnvMgr.class), mock(TigerBuildPropertiesService.class));
    TigerServerStatusUpdate starting =
        TigerServerStatusUpdate.builder()
            .status(TigerServerStatus.RUNNING)
            .type("docker")
            .baseUrl("http://localhost")
            .statusMessage("started")
            .build();
    TigerStatusUpdate first =
        TigerStatusUpdate.builder()
            .serverUpdate(linkedMap("server", starting))
            .bannerMessage("ready")
            .bannerColor("green")
            .bannerType(BannerType.MESSAGE)
            .bannerDetails(new TigerStatusUpdate.BannerDetails("details"))
            .bannerIsHtml(true)
            .testSuiteLifecycle(TestSuiteLifecycle.EXECUTING_TESTS)
            .build();
    first.setIndex(10);
    controller.receiveTestEnvUpdate(first);

    TigerStatusUpdate refresh =
        TigerStatusUpdate.builder()
            .serverUpdate(
                linkedMap(
                    "server", TigerServerStatusUpdate.builder().statusMessage("healthy").build()))
            .build();
    refresh.setIndex(9);
    controller.receiveTestEnvUpdate(refresh);

    var status = controller.getStatus();
    assertThat(status.getCurrentIndex()).isEqualTo(10);
    assertThat(status.getTestSuiteLifecycle()).isEqualTo(TestSuiteLifecycle.EXECUTING_TESTS);
    assertThat(status.getBannerMessage()).isEqualTo("ready");
    assertThat(status.getBannerColor()).isEqualTo("green");
    assertThat(status.getBannerDetails().getDetailedMessage()).isEqualTo("details");
    assertThat(status.isBannerIsHtml()).isTrue();
    assertThat(status.getServers().get("server"))
        .hasFieldOrPropertyWithValue("status", TigerServerStatus.RUNNING)
        .hasFieldOrPropertyWithValue("type", "docker")
        .hasFieldOrPropertyWithValue("baseUrl", "http://localhost")
        .hasFieldOrPropertyWithValue("statusMessage", "healthy")
        .hasFieldOrPropertyWithValue("statusUpdates", List.of("started", "healthy"));
  }

  @Test
  void receiveTestEnvUpdate_marksMatchingMessageMetadataAsRemoved() {
    StepUpdate step =
        StepUpdate.builder()
            .rbelMetaData(
                List.of(
                    MessageMetaDataDto.builder().uuid("remove-me").build(),
                    MessageMetaDataDto.builder().uuid("keep-me").build()))
            .build();
    EnvStatusController controller =
        controllerWithScenario(scenarioWithSteps(Map.of("step", step)));

    controller.receiveTestEnvUpdate(
        TigerStatusUpdate.builder().removedMessageUuids(List.of("remove-me")).build());

    List<MessageMetaDataDto> metadata =
        scenarioFrom(controller).getSteps().get("step").getRbelMetaData();
    assertThat(metadata).extracting(MessageMetaDataDto::isRemoved).containsExactly(true, false);
  }

  @Test
  void receiveTestEnvUpdate_skipsInvalidFeatureAndContinuesWithNext() {
    TigerTestEnvMgr envMgr = mock(TigerTestEnvMgr.class);
    EnvStatusController controller =
        new EnvStatusController(envMgr, mock(TigerBuildPropertiesService.class));
    FeatureUpdate validFeature =
        FeatureUpdate.builder()
            .scenarios(linkedMap("scenario", ScenarioUpdate.builder().build()))
            .build();
    controller.receiveTestEnvUpdate(
        TigerStatusUpdate.builder().featureMap(linkedMap("feature", validFeature)).build());

    FeatureUpdate invalidFeature = FeatureUpdate.builder().status(TestResult.PASSED).build();
    FeatureUpdate additionalFeature = FeatureUpdate.builder().build();
    LinkedHashMap<String, FeatureUpdate> featureUpdates = linkedMap("feature", invalidFeature);
    featureUpdates.put("additional", additionalFeature);
    TigerStatusUpdate partialUpdate =
        TigerStatusUpdate.builder()
            .featureMap(featureUpdates)
            .removedMessageUuids(List.of())
            .build();
    controller.receiveTestEnvUpdate(partialUpdate);

    assertThat(controller.getStatus().getFeatureMap()).containsKey("additional");
    assertThat(controller.getStatus().getFeatureMap().get("feature").getStatus())
        .isEqualTo(TestResult.PASSED);
    assertThat(controller.getStatus().getCurrentIndex()).isEqualTo(partialUpdate.getIndex());
  }

  @Test
  void receiveTestEnvUpdate_ignoresUpdateWithNullFeatureMap() {
    EnvStatusController controller =
        new EnvStatusController(
            mock(TigerTestEnvMgr.class), mock(TigerBuildPropertiesService.class));
    TigerStatusUpdate update = mock(TigerStatusUpdate.class);
    when(update.getFeatureMap()).thenReturn(null);

    controller.receiveTestEnvUpdate(update);
    assertThat(controller.getStatus().getFeatureMap()).isEmpty();
  }

  @Test
  void getStatus_marksThatWorkflowUiFetchedStatus() {
    TigerTestEnvMgr envMgr = mock(TigerTestEnvMgr.class);
    EnvStatusController controller =
        new EnvStatusController(envMgr, mock(TigerBuildPropertiesService.class));

    assertThat(controller.getStatus()).isNotNull();
    verify(envMgr).setWorkflowUiSentFetch(true);
  }

  @Test
  void confirmationEndpointsDelegateToEnvironmentManager() {
    TigerTestEnvMgr envMgr = mock(TigerTestEnvMgr.class);
    EnvStatusController controller =
        new EnvStatusController(envMgr, mock(TigerBuildPropertiesService.class));

    controller.getConfirmShutdown();
    controller.getConfirmContinueExecution();
    controller.getConfirmToFailExecution();

    verify(envMgr).receivedQuitConfirmationFromWorkflowUi();
    verify(envMgr).receivedConfirmationFromWorkflowUi(false);
    verify(envMgr).receivedConfirmationFromWorkflowUi(true);
    verifyFailBannerWasSent(envMgr);
  }

  @Test
  void buildEndpointsReturnBuildProperties() {
    TigerTestEnvMgr envMgr = mock(TigerTestEnvMgr.class);
    TigerBuildPropertiesService buildProperties = mock(TigerBuildPropertiesService.class);
    when(buildProperties.tigerVersionAsString()).thenReturn("version");
    when(buildProperties.tigerBuildDateAsString()).thenReturn("build-date");
    EnvStatusController controller = new EnvStatusController(envMgr, buildProperties);

    assertThat(controller.getTigerVersion()).isEqualTo("version");
    assertThat(controller.getBuildDate()).isEqualTo("build-date");
  }

  private static EnvStatusController controllerWithScenario(ScenarioUpdate scenario) {
    EnvStatusController controller =
        new EnvStatusController(
            mock(TigerTestEnvMgr.class), mock(TigerBuildPropertiesService.class));
    controller.receiveTestEnvUpdate(statusUpdate(scenario));
    return controller;
  }

  private static ScenarioUpdate scenarioWithSteps(Map<String, StepUpdate> steps) {
    return ScenarioUpdate.builder().steps(steps).build();
  }

  private static ScenarioUpdate scenarioFrom(EnvStatusController controller) {
    return controller.getStatus().getFeatureMap().get("feature").getScenarios().get("scenario");
  }

  private static TigerStatusUpdate statusUpdate(ScenarioUpdate scenario) {
    FeatureUpdate feature =
        FeatureUpdate.builder().scenarios(linkedMap("scenario", scenario)).build();
    return TigerStatusUpdate.builder().featureMap(linkedMap("feature", feature)).build();
  }

  private static void verifyFailBannerWasSent(TigerTestEnvMgr envMgr) {
    ArgumentCaptor<TigerStatusUpdate> updateCaptor =
        ArgumentCaptor.forClass(TigerStatusUpdate.class);
    verify(envMgr).receiveTestEnvUpdate(updateCaptor.capture());
    assertThat(updateCaptor.getValue().getBannerMessage()).isEqualTo("Failing test run");
    assertThat(updateCaptor.getValue().getBannerColor()).isEqualTo("red");
  }

  private static <T> LinkedHashMap<String, T> linkedMap(String key, T value) {
    LinkedHashMap<String, T> result = new LinkedHashMap<>();
    result.put(key, value);
    return result;
  }
}
