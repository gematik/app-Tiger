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

import static de.gematik.test.tiger.common.config.TigerConfigurationKeys.LOCAL_PROXY_ADMIN_PORT;
import static io.cucumber.core.options.Constants.FEATURES_PROPERTY_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request;
import static org.mockito.Mockito.mock;

import de.gematik.test.tiger.lib.TigerDirector;
import de.gematik.test.tiger.server.TigerBuildPropertiesService;
import de.gematik.test.tiger.testenvmgr.controller.EnvStatusController;
import de.gematik.test.tiger.testenvmgr.data.TigerEnvStatusDto;
import de.gematik.test.tiger.testenvmgr.env.ScenarioRunner;
import de.gematik.test.tiger.testenvmgr.env.ScenarioUpdate;
import de.gematik.test.tiger.testenvmgr.env.TestResult;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import io.cucumber.core.plugin.report.LocationConverter;
import io.cucumber.core.plugin.report.SerenityReporterCallbacks;
import io.cucumber.plugin.event.Argument;
import io.cucumber.plugin.event.Location;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.Result;
import io.cucumber.plugin.event.Status;
import io.cucumber.plugin.event.Step;
import io.cucumber.plugin.event.StepArgument;
import io.cucumber.plugin.event.TestCase;
import io.cucumber.plugin.event.TestCaseFinished;
import io.cucumber.plugin.event.TestCaseStarted;
import io.cucumber.plugin.event.TestSourceRead;
import io.cucumber.plugin.event.TestStep;
import io.cucumber.plugin.event.TestStepFinished;
import io.cucumber.plugin.event.TestStepStarted;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherFactory;

public class TestTigerSerenityReporterPlugin {

  private TigerSerenityReporterPlugin listener;

  private static EnvStatusController envStatusController;

  private final String featureName = "Authentifiziere Anwendung am IDP Server";
  private final UUID scenarioId = UUID.randomUUID();

  private final String scenarioName = "Auth - Gutfall - Validiere Claims";
  private final String scenarioOutlineId = "6223db6e-708e-4c3f-ab11-1373b7e94ad7";
  private final String scenarioOutlineName = "Auth - Fehlende Parameter alle anderen";
  private final String featureFilePath =
      "src/test/resources/testdata/parser/bdd/features/authentication.feature";
  private final URI featureUri = new File(featureFilePath).toURI();

  @BeforeAll
  public static void startTiger() {
    TigerDirector.start();
    envStatusController =
        new EnvStatusController(
            TigerDirector.getTigerTestEnvMgr(), mock(TigerBuildPropertiesService.class));
  }

  @BeforeEach
  public void setUp() {
    Launcher launcher = LauncherFactory.create();
    TestPlan testPlan =
        launcher.discover(
            request().configurationParameter(FEATURES_PROPERTY_NAME, featureFilePath).build());
    new TigerExecutionListener().testPlanExecutionStarted(testPlan);
  }

  @AfterEach
  public void tearDown() {
    ScenarioRunner.clearScenarios();
  }

  @BeforeEach
  public void initListener() {
    listener = new TigerSerenityReporterPlugin();
    LOCAL_PROXY_ADMIN_PORT.putValue(9999);
    envStatusController.getStatus().getFeatureMap().clear();
  }

  @Test
  void testCaseStartedSimpleScenario() throws IOException {

    TestSourceRead event =
        new TestSourceRead(
            Instant.now(), featureUri, IOUtils.toString(featureUri, StandardCharsets.UTF_8));
    listener.handleTestSourceRead(event);

    TestcaseAdapter testCase = new TestcaseAdapter();
    TestCaseStarted startedEvent = new TestCaseStarted(Instant.now(), testCase);

    listener.handleTestCaseStarted(startedEvent);

    String scenarioId = listener.getReporterCallbacks().scenarioIdFrom(testCase);
    assertThat(listener.getContext(featureUri).getCurrentScenarioDefinition(scenarioId).getName())
        .isEqualTo(scenarioName);
    assertThat(
            listener
                .getReporterCallbacks()
                .extractScenarioDataVariantIndex(
                    listener.getScenarioContextDelegate(featureUri), testCase))
        .isEqualTo(-1);

    TigerEnvStatusDto status = envStatusController.getStatus();
    assertThat(status.getFeatureMap()).containsOnlyKeys(featureName);
    Map<String, ScenarioUpdate> scenarios = status.getFeatureMap().get(featureName).getScenarios();
    assertThat(scenarios).hasSize(1);
    String scenarioUniqueId = findScenarioUniqueId(startedEvent.getTestCase());
    assertThat(scenarios.get(scenarioUniqueId).getDescription()).isEqualTo(scenarioName);
  }

  @Test
  void testCaseStartedScenarioOutline() throws IOException {
    TestSourceRead event =
        new TestSourceRead(
            Instant.now(), featureUri, IOUtils.toString(featureUri, StandardCharsets.UTF_8));
    listener.handleTestSourceRead(event);

    TestCaseStarted startedEvent =
        new TestCaseStarted(Instant.now(), new ScenarioOutlineTestCaseAdapter());
    listener.handleTestCaseStarted(startedEvent);

    String scenarioId = listener.getReporterCallbacks().scenarioIdFrom(startedEvent.getTestCase());
    assertThat(listener.getContext(featureUri).getTable(scenarioId).getHeaders())
        .contains(
            "http_code",
            "err_id",
            "err",
            "client_id",
            "scope",
            "code_challenge",
            "code_challenge_method",
            "redirect_uri",
            "state",
            "nonce",
            "response_type");
    TigerEnvStatusDto status = envStatusController.getStatus();
    assertThat(status.getFeatureMap()).containsOnlyKeys(featureName);
    Map<String, ScenarioUpdate> scenarios = status.getFeatureMap().get(featureName).getScenarios();
    assertThat(scenarios).hasSize(1);
    var scenario1UniqueId = findScenarioUniqueId(startedEvent.getTestCase());
    var scenario1 = scenarios.get(scenario1UniqueId);
    assertThat(scenario1.getDescription()).isEqualTo(scenarioOutlineName);
    assertThat(scenario1.getVariantIndex()).isZero();

    ScenarioOutlineTestCaseAdapter testCase =
        (ScenarioOutlineTestCaseAdapter) startedEvent.getTestCase();

    ScenarioContextDelegate context = listener.getScenarioContextDelegate(featureUri);
    SerenityReporterCallbacks reporterCallbacks = listener.getReporterCallbacks();

    assertThat(reporterCallbacks.extractScenarioDataVariantIndex(context, testCase)).isZero();

    // moving to next variant
    testCase.incrementLocationLine();
    listener.handleTestCaseStarted(startedEvent);

    assertThat(reporterCallbacks.extractScenarioDataVariantIndex(context, testCase)).isEqualTo(1);

    status = envStatusController.getStatus();
    scenarios = status.getFeatureMap().get(featureName).getScenarios();
    assertThat(scenarios).hasSize(2);
    var scenario2UniqueId = findScenarioUniqueId(startedEvent.getTestCase());
    var scenario2 = scenarios.get(scenario2UniqueId);

    assertThat(scenario1.getDescription()).isEqualTo(scenarioOutlineName);
    assertThat(scenario1.getVariantIndex()).isZero();
    assertThat(scenario2.getDescription()).isEqualTo(scenarioOutlineName);
    assertThat(scenario2.getVariantIndex()).isEqualTo(1);
  }

  @Test
  void testStepsHandling() throws IOException {
    TestSourceRead event =
        new TestSourceRead(
            Instant.now(), featureUri, IOUtils.toString(featureUri, StandardCharsets.UTF_8));
    listener.handleTestSourceRead(event);
    TestStep testStep1 = new TestStepAdapter(71, 5);
    TestStep testStep2 = new TestStepAdapter(73, 5);
    TestCase testCase =
        new TestcaseAdapter() {
          @Override
          public List<TestStep> getTestSteps() {
            return List.of(testStep1, testStep2);
          }
        };
    TestCaseStarted startedEvent = new TestCaseStarted(Instant.now(), testCase);
    listener.handleTestCaseStarted(startedEvent);

    TestStepStarted stepStartedEvent1 = new TestStepStarted(Instant.now(), testCase, testStep1);
    listener.handleTestStepStarted(stepStartedEvent1);
    assertThat(listener.getContext(featureUri).getCurrentStep(testCase).getLocation().getLine())
        .isEqualTo(71);
    listener.handleTestStepFinished(
        new TestStepFinished(
            Instant.now(),
            testCase,
            testStep1,
            new Result(Status.PASSED, Duration.ofMillis(500), null)));

    TestStepStarted stepStartedEvent2 = new TestStepStarted(Instant.now(), testCase, testStep2);
    listener.handleTestStepStarted(stepStartedEvent2);
    assertThat(listener.getContext(featureUri).getCurrentStep(testCase).getLocation().getLine())
        .isEqualTo(73);
    listener.handleTestStepFinished(
        new TestStepFinished(
            Instant.now(),
            testCase,
            testStep2,
            new Result(
                Status.FAILED, Duration.ofMillis(500), new TigerTestEnvException("Test Tiger A"))));

    TigerEnvStatusDto status = envStatusController.getStatus();
    assertThat(status.getFeatureMap()).containsOnlyKeys(featureName);
    String scenarioUniqueId = findScenarioUniqueId(testCase);
    ScenarioUpdate scenario =
        status.getFeatureMap().get(featureName).getScenarios().get(scenarioUniqueId);
    assertThat(scenario.getSteps().get("0").getStatus()).isEqualTo(TestResult.PASSED);
    assertThat(scenario.getSteps().get("1").getStatus()).isEqualTo(TestResult.FAILED);
  }

  @Test
  void testCaseFinished() throws IOException {
    long startms = System.currentTimeMillis();
    TestSourceRead event =
        new TestSourceRead(
            Instant.now(), featureUri, IOUtils.toString(featureUri, StandardCharsets.UTF_8));
    listener.handleTestSourceRead(event);
    TestStep testStep = new TestStepAdapter(71, 5);
    TestCase testCase =
        new TestcaseAdapter() {
          @Override
          public List<TestStep> getTestSteps() {
            return List.of(testStep);
          }
        };
    TestCaseStarted startedEvent = new TestCaseStarted(Instant.now(), testCase);
    listener.handleTestCaseStarted(startedEvent);

    TestStepStarted stepStartedEvent = new TestStepStarted(Instant.now(), testCase, testStep);
    listener.handleTestStepStarted(stepStartedEvent);
    listener.handleTestStepFinished(
        new TestStepFinished(
            Instant.now(),
            testCase,
            testStep,
            new Result(
                Status.FAILED,
                Duration.ofMillis(500),
                new TigerTestEnvException("Testing tiger 1"))));
    TestCaseFinished finishedEvent =
        new TestCaseFinished(
            Instant.now(), testCase, new Result(Status.FAILED, Duration.ofMillis(500), null));
    listener.handleTestCaseFinished(finishedEvent);

    assertThat(listener.getReporterCallbacks().getScFailed()).isEqualTo(1);
    assertThat(listener.getReporterCallbacks().getScPassed()).isZero();

    File logFileFolder = new File("target/rbellogs/");
    File logFile =
        Arrays.stream(logFileFolder.listFiles())
            .filter(file -> file.lastModified() > startms)
            .findFirst()
            .get();
    assertThat(logFile.getName())
        .startsWith(
            "rbel_" + listener.getReporterCallbacks().replaceSpecialCharacters(scenarioName));
    assertThat(logFile)
        .exists()
        .hasName(listener.getReporterCallbacks().replaceSpecialCharacters(logFile.getName()))
        .content(StandardCharsets.UTF_8)
        .hasSizeGreaterThan(800)
        .contains(scenarioName);

    TigerEnvStatusDto status = envStatusController.getStatus();
    assertThat(status.getFeatureMap()).containsOnlyKeys(featureName);

    String scenarioUniqueId = findScenarioUniqueId(testCase);
    ScenarioUpdate scenario =
        status.getFeatureMap().get(featureName).getScenarios().get(scenarioUniqueId);

    assertThat(scenario.getStatus()).isEqualTo(TestResult.FAILED);

    assertThat(listener.getReporterCallbacks().getScFailed()).isEqualTo(1);
    assertThat(listener.getReporterCallbacks().getScPassed()).isZero();
  }

  private String findScenarioUniqueId(TestCase testCase) {
    return ScenarioRunner.findScenarioUniqueId(
            featureUri, new LocationConverter().convertLocation(testCase.getLocation()))
        .toString();
  }

  @Data
  @AllArgsConstructor
  private static class TestStepAdapter implements PickleStepTestStep {
    private int line;
    private int column;

    private final UUID stepUuid = UUID.randomUUID();

    @Override
    public String getCodeLocation() {
      return "de.gematik.test.tiger.glue.TestGlue.testGlueMethod()";
    }

    @Override
    public UUID getId() {
      return stepUuid;
    }

    @Override
    public String getPattern() {
      return null;
    }

    @Override
    public Step getStep() {
      return new Step() {
        @Override
        public StepArgument getArgument() {
          return null;
        }

        @Override
        public String getKeyword() {
          return "When";
        }

        @Override
        public String getText() {
          return "Tiger test step";
        }

        @Override
        public int getLine() {
          return line;
        }

        @Override
        public Location getLocation() {
          return new Location(line, column);
        }
      };
    }

    @Override
    public List<Argument> getDefinitionArgument() {
      return List.of();
    }

    @Override
    public StepArgument getStepArgument() {
      return null;
    }

    @Override
    public int getStepLine() {
      return line;
    }

    @Override
    public URI getUri() {
      return null;
    }

    @Override
    public String getStepText() {
      return "a step text";
    }
  }

  private class TestcaseAdapter implements TestCase {

    public TestcaseAdapter() {}

    @Override
    public Integer getLine() {
      return 60;
    }

    @Override
    public Location getLocation() {
      return new Location(60, 3);
    }

    @Override
    public String getKeyword() {
      return null;
    }

    @Override
    public String getName() {
      return scenarioName;
    }

    @Override
    public String getScenarioDesignation() {
      return null;
    }

    @Override
    public List<String> getTags() {
      return List.of();
    }

    @Override
    public List<TestStep> getTestSteps() {
      return List.of(new TestStepAdapter(71, 5));
    }

    @Override
    public URI getUri() {
      return featureUri;
    }

    @Override
    public UUID getId() {
      return scenarioId;
    }
  }

  private class ScenarioOutlineTestCaseAdapter extends TestcaseAdapter {

    // Line refers to first example in src/test/resources/testdata/parser/bdd/authentication.feature
    private int line = 181;

    @Override
    public Integer getLine() {
      return line;
    }

    @Override
    public Location getLocation() {
      return new Location(line, 7);
    }

    @Override
    public String getName() {
      return scenarioOutlineName;
    }

    @Override
    public UUID getId() {
      return UUID.fromString(scenarioOutlineId);
    }

    public void incrementLocationLine() {
      line++;
    }
  }
}
