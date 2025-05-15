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

import com.jayway.jsonpath.JsonPath;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.report.ReportDataKeys;
import de.gematik.test.tiger.lib.TigerDirector;
import de.gematik.test.tiger.server.TigerBuildPropertiesService;
import de.gematik.test.tiger.testenvmgr.controller.EnvStatusController;
import de.gematik.test.tiger.testenvmgr.data.TigerEnvStatusDto;
import de.gematik.test.tiger.testenvmgr.env.ScenarioRunner;
import de.gematik.test.tiger.testenvmgr.env.ScenarioUpdate;
import de.gematik.test.tiger.testenvmgr.env.StepUpdate;
import de.gematik.test.tiger.testenvmgr.env.TestResult;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import io.cucumber.core.gherkin.DataTableArgument;
import io.cucumber.core.gherkin.DocStringArgument;
import io.cucumber.core.internal.com.fasterxml.jackson.core.TreeNode;
import io.cucumber.core.internal.com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.core.internal.com.fasterxml.jackson.databind.node.ArrayNode;
import io.cucumber.core.plugin.report.LocationConverter;
import io.cucumber.core.plugin.report.SerenityReporterCallbacks;
import io.cucumber.plugin.event.Argument;
import io.cucumber.plugin.event.Group;
import io.cucumber.plugin.event.Location;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.Result;
import io.cucumber.plugin.event.Status;
import io.cucumber.plugin.event.Step;
import io.cucumber.plugin.event.StepArgument;
import io.cucumber.plugin.event.TestCase;
import io.cucumber.plugin.event.TestCaseFinished;
import io.cucumber.plugin.event.TestCaseStarted;
import io.cucumber.plugin.event.TestRunFinished;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import junit.framework.AssertionFailedError;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import net.minidev.json.JSONArray;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherFactory;

public class TestTigerSerenityReporterPlugin {

  public static final String GENERATED_JSON_REPORT =
      "target/site/serenity/d11408aff740706845d0a023dbe62d62f228d65c01a235474885c0879ce920e1.json";

  public static final String TEST_GLUE_CLASS = "de.gematik.test.tiger.lib.integrationtest.TestGlue";
  public static final String TEST_GLUE_STEP_LOCATION =
      TEST_GLUE_CLASS + ".testGlueMethod(java.lang.String,java.lang.String)";
  public static final String RESOLVABLE_DOCSTRING_STEP_LOCATION =
      TEST_GLUE_CLASS + ".testDocString(java.lang.String)";
  public static final String NON_RESOLVABLE_DOCSTRING_STEP_LOCATION =
      TEST_GLUE_CLASS + ".testUnresolvableDocString(java.lang.String)";
  public static final String RESOLVABLE_TABLE_STEP_LOCATION =
      TEST_GLUE_CLASS + ".testDatatable(io.cucumber.datatable.DataTable)";
  public static final String NON_RESOLVABLE_TABLE_STEP_LOCATION =
      TEST_GLUE_CLASS + ".testUnresolvableDatatable(io.cucumber.datatable.DataTable)";
  public static final String NOT_REPLACED_STRING_REF = "${not.replaced.string}";
  public static final String REPLACED_STRING_REF = "${replaced.string}";
  public static final DocStringArgument DOC_STRING_ARGUMENT =
      new DocStringArgument() {
        @Override
        public String getContent() {
          return "{\"key\": \"" + REPLACED_STRING_REF + "\"}";
        }

        @Override
        public String getContentType() {
          return "json";
        }

        @Override
        public String getMediaType() {
          return "application/json";
        }

        @Override
        public int getLine() {
          return 74;
        }
      };
  public static final DataTableArgument TABLE_ARGUMENT =
      new DataTableArgument() {
        @Override
        public List<List<String>> cells() {
          return List.of(List.of("Replaced"), List.of(REPLACED_STRING_REF));
        }

        @Override
        public int getLine() {
          return 74;
        }
      };

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
                .extractScenarioDataVariantIndex(listener.getContext(featureUri), testCase))
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

    IScenarioContext context = listener.getContext(featureUri);
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
    TestStep testStep1 = TestStepAdapter.builder().line(71).column(5).build();
    TestStep testStep2 = TestStepAdapter.builder().line(73).column(5).build();
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
    StepUpdate step0 = scenario.getSteps().get("0");
    assertThat(step0.getStatus()).isEqualTo(TestResult.PASSED);
    assertThat(scenario.getSteps().get("1").getStatus()).isEqualTo(TestResult.FAILED);
  }

  @AfterEach
  void deleteJsonReport() {
    File jsonReport = new File(GENERATED_JSON_REPORT);
    if (jsonReport.exists()) {
      jsonReport.delete();
    }
  }

  @Test
  void testStepsResolutionHandling() throws IOException {
    TigerGlobalConfiguration.putValue("replaced.string", "replacement");
    TigerGlobalConfiguration.putValue("not.replaced.string", "not.replaced");

    TestSourceRead event =
        new TestSourceRead(
            Instant.now(), featureUri, IOUtils.toString(featureUri, StandardCharsets.UTF_8));
    listener.handleTestSourceRead(event);
    TestStep testStepFail = TestStepAdapter.builder().line(71).column(5).build();
    TestStep testStep1 =
        TestStepAdapter.builder()
            .line(71)
            .column(5)
            .definitionArgument(
                List.of(
                    new ArgumentAdapter("tigerResolvedString", REPLACED_STRING_REF),
                    new ArgumentAdapter("string", "\"" + NOT_REPLACED_STRING_REF + "\"")))
            .build();
    TestStep testStep2 =
        TestStepAdapter.builder()
            .line(73)
            .column(5)
            .pattern("step has a resolvable docstring:")
            .stepText("step has a resolvable docstring:")
            .codeLocation(RESOLVABLE_DOCSTRING_STEP_LOCATION)
            .stepArgument(DOC_STRING_ARGUMENT)
            .build();
    TestStep testStep3 =
        TestStepAdapter.builder()
            .line(73)
            .column(5)
            .pattern("step has a non resolvable docstring:")
            .stepText("step has a non resolvable docstring:")
            .codeLocation(NON_RESOLVABLE_DOCSTRING_STEP_LOCATION)
            .stepArgument(DOC_STRING_ARGUMENT)
            .build();
    TestStep testStep4 =
        TestStepAdapter.builder()
            .line(73)
            .column(5)
            .pattern("step has a resolvable datatable:")
            .stepText("step has a resolvable datatable:")
            .codeLocation(RESOLVABLE_TABLE_STEP_LOCATION)
            .stepArgument(TABLE_ARGUMENT)
            .build();
    TestStep testStep5 =
        TestStepAdapter.builder()
            .line(73)
            .column(5)
            .pattern("step has a non resolvable docstring:")
            .stepText("step has a non resolvable docstring:")
            .codeLocation(NON_RESOLVABLE_TABLE_STEP_LOCATION)
            .stepArgument(TABLE_ARGUMENT)
            .build();

    List<TestStep> steps = List.of(testStep1, testStep2, testStep3, testStep4, testStep5);

    var allSteps = new LinkedList<>(steps);
    allSteps.add(testStepFail);
    allSteps.addAll(steps);

    TestCase testCase =
        new TestcaseAdapter() {
          @Override
          public List<TestStep> getTestSteps() {
            return allSteps;
          }
        };
    TestCaseStarted startedEvent = new TestCaseStarted(Instant.now(), testCase);
    listener.handleTestCaseStarted(startedEvent);

    steps.forEach(step -> handleStep(testCase, step, Status.PASSED, null));
    handleStep(testCase, testStepFail, Status.FAILED, new AssertionFailedError("Failed"));
    steps.forEach(step -> handleStep(testCase, step, Status.SKIPPED, null));

    TigerEnvStatusDto status = envStatusController.getStatus();
    assertThat(status.getFeatureMap()).containsOnlyKeys(featureName);
    String scenarioUniqueId = findScenarioUniqueId(testCase);
    ScenarioUpdate scenario =
        status.getFeatureMap().get(featureName).getScenarios().get(scenarioUniqueId);

    Map<String, StepUpdate> stepUpdates = scenario.getSteps();
    checkReplacement(stepUpdates.get("0"), true);
    checkReplacement(stepUpdates.get("1"), false);
    StepUpdate step2 = stepUpdates.get("2");
    assertThat(step2.getDescription()).contains(REPLACED_STRING_REF);
    assertThat(step2.getTooltip()).contains(REPLACED_STRING_REF);
    checkReplacement(stepUpdates.get("3"), false);
    StepUpdate step4 = stepUpdates.get("4");
    assertThat(step4.getDescription()).contains(REPLACED_STRING_REF);
    assertThat(step4.getTooltip()).contains(REPLACED_STRING_REF);

    listener.handleTestCaseFinished(
        new TestCaseFinished(
            Instant.now(), testCase, new Result(Status.PASSED, Duration.ofMillis(500), null)));

    listener.handleTestRunFinished(
        new TestRunFinished(
            Instant.now(), new Result(Status.PASSED, Duration.ofMillis(500), null)));

    try (var reader = new ObjectMapper().createParser(new File(GENERATED_JSON_REPORT))) {
      TreeNode testSteps = reader.readValueAsTree().get("testSteps");

      assertThat(testSteps.size()).isEqualTo(11);

      testStepResolutionPassedSteps(testSteps);

      // skipped steps --> no replacements

      testStepsREsolutionSkippedSteps(testSteps);

    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static void testStepsREsolutionSkippedSteps(TreeNode testSteps) {
    var description6 = testSteps.get(6).get("description").toString();
    assertThat(description6).contains(NOT_REPLACED_STRING_REF);
    assertThat(description6).contains(REPLACED_STRING_REF);
    assertThat(testSteps.get(6).get("reportData")).isNull();
    assertThat(testSteps.get(7).get("reportData")).isNull();
    assertThat(testSteps.get(8).get("reportData")).isNull();
    assertThat(testSteps.get(9).get("reportData")).isNull();
    for (TreeNode node : (ArrayNode) testSteps.get(10).get("reportData")) {
      assertThat(node.get("title").toString())
          .isNotEqualTo(ReportDataKeys.TIGER_RESOLVED_STEP_DESCRIPTION_KEY);
    }

    var description9 = testSteps.get(9).get("description").toString();
    assertThat(description9).contains(REPLACED_STRING_REF);
    assertThat(description9).doesNotContain("replacement");

    var description10 = testSteps.get(10).get("description").toString();
    assertThat(description10).contains(REPLACED_STRING_REF);
    assertThat(description10).doesNotContain("replacement");
  }

  private static void testStepResolutionPassedSteps(TreeNode testSteps) {
    var description = testSteps.get(0).get("description").toString();
    assertThat(description).contains(NOT_REPLACED_STRING_REF);
    assertThat(description).contains(REPLACED_STRING_REF);

    var reportDataContents0 = getTigerResolvedStepDescriptionContents(testSteps.get(0));
    assertThat(reportDataContents0).contains(NOT_REPLACED_STRING_REF);
    assertThat(reportDataContents0).doesNotContain(REPLACED_STRING_REF);

    var reportDataContents1 = getTigerResolvedStepDescriptionContents(testSteps.get(1));
    assertThat(reportDataContents1).doesNotContain(REPLACED_STRING_REF);
    assertThat(reportDataContents1).contains("replacement");

    var reportDataContents2 = getTigerResolvedStepDescriptionContents(testSteps.get(2));
    assertThat(reportDataContents2).contains(REPLACED_STRING_REF);

    var reportDataContents3 = getTigerResolvedStepDescriptionContents(testSteps.get(3));
    assertThat(reportDataContents3).doesNotContain(REPLACED_STRING_REF);
    assertThat(reportDataContents3).contains("replacement");

    var reportDataContents4 = getTigerResolvedStepDescriptionContents(testSteps.get(4));
    assertThat(reportDataContents4).contains(REPLACED_STRING_REF);
  }

  private static String getTigerResolvedStepDescriptionContents(TreeNode testStep) {
    JSONArray resolvedStepDescriptionContent =
        JsonPath.read(
            testStep.toString(),
            "$.reportData[?(@.title == '%s')].contents"
                .formatted(ReportDataKeys.TIGER_RESOLVED_STEP_DESCRIPTION_KEY));
    if (resolvedStepDescriptionContent.isEmpty()) {
      return null;
    } else {
      return resolvedStepDescriptionContent.get(0).toString();
    }
  }

  private void checkReplacement(StepUpdate step, boolean checkNotReplaced) {
    assertThat(step.getDescription()).doesNotContain(REPLACED_STRING_REF);
    assertThat(step.getTooltip()).contains(REPLACED_STRING_REF);
    if (checkNotReplaced) {
      assertThat(step.getDescription()).contains(NOT_REPLACED_STRING_REF);
      assertThat(step.getTooltip()).contains(NOT_REPLACED_STRING_REF);
    }
  }

  private void handleStep(TestCase testCase, TestStep testStep, Status status, Throwable error) {
    listener.handleTestStepStarted(new TestStepStarted(Instant.now(), testCase, testStep));
    listener.handleTestStepFinished(
        new TestStepFinished(
            Instant.now(), testCase, testStep, new Result(status, Duration.ofMillis(500), error)));
  }

  @Test
  void testCaseFinished() throws IOException {
    long startms = System.currentTimeMillis();
    TestSourceRead event =
        new TestSourceRead(
            Instant.now(), featureUri, IOUtils.toString(featureUri, StandardCharsets.UTF_8));
    listener.handleTestSourceRead(event);
    TestStep testStep = TestStepAdapter.builder().line(71).column(5).build();
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
            "rbel_"
                + StringUtils.abbreviate(
                    listener.getReporterCallbacks().replaceSpecialCharacters(scenarioName),
                    "",
                    0,
                    30));
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

  @Getter
  @AllArgsConstructor
  private static class ArgumentAdapter implements Argument {
    private final String parameterTypeName;
    private final String value;

    @Override
    public int getStart() {
      return 0;
    }

    @Override
    public int getEnd() {
      return 0;
    }

    @Override
    public Group getGroup() {
      return null;
    }
  }

  @Data
  @Builder
  private static class TestStepAdapter implements PickleStepTestStep {
    private int line;
    private int column;
    private StepArgument stepArgument;
    private @Builder.Default String codeLocation = TEST_GLUE_STEP_LOCATION;
    private @Builder.Default String pattern =
        "test step resolves {tigerResolvedString} and does not resolve {string}";
    private @Builder.Default String stepText =
        "test step resolves "
            + REPLACED_STRING_REF
            + " and does not resolve \""
            + NOT_REPLACED_STRING_REF
            + "\"";
    private @Builder.Default List<Argument> definitionArgument = List.of();

    private final UUID stepUuid = UUID.randomUUID();

    @Override
    public UUID getId() {
      return stepUuid;
    }

    @Override
    public Step getStep() {
      return new Step() {
        @Override
        public StepArgument getArgument() {
          return stepArgument;
        }

        @Override
        public String getKeyword() {
          return "When ";
        }

        @Override
        public String getText() {
          return stepText;
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
    public int getStepLine() {
      return line;
    }

    @Override
    public URI getUri() {
      return null;
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
      return List.of(TestStepAdapter.builder().line(71).column(5).build());
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
