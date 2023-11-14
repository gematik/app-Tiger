/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package io.cucumber.core.plugin;

import static de.gematik.test.tiger.common.config.TigerConfigurationKeys.LOCAL_PROXY_ADMIN_PORT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.gematik.test.tiger.lib.TigerDirector;
import de.gematik.test.tiger.spring_utils.TigerBuildPropertiesService;
import de.gematik.test.tiger.testenvmgr.controller.EnvStatusController;
import de.gematik.test.tiger.testenvmgr.data.TigerEnvStatusDto;
import de.gematik.test.tiger.testenvmgr.env.ScenarioUpdate;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
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
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TestTigerSerenityReporterPlugin {

  private TigerSerenityReporterPlugin listener;

  private static EnvStatusController envStatusController;

  private final String featureName = "Authentifiziere Anwendung am IDP Server";
  private final UUID scenarioId = UUID.randomUUID();

  private final String scenarioName = "Auth - Gutfall - Validiere Claims";
  private final String scenarioOutlineId = "6223db6e-708e-4c3f-ab11-1373b7e94ad7";
  private final String scenarioOutlineName = "Auth - Fehlende Parameter alle anderen";
  private final String featureFilePath =
      "src/test/resources/testdata/parser/bdd/authentication.feature";
  private final URI featureUri = new File(featureFilePath).toURI();

  @BeforeAll
  public static void startTiger() {
    TigerDirector.start();
    envStatusController =
        new EnvStatusController(
            TigerDirector.getTigerTestEnvMgr(), mock(TigerBuildPropertiesService.class));
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

    assertThat(listener.getContext().currentFeaturePath()).isNull();

    TestCaseStarted startedEvent = new TestCaseStarted(Instant.now(), new TestcaseAdapter());

    listener.handleTestCaseStarted(startedEvent);
    assertThat(listener.getContext().currentFeaturePath()).isEqualTo(featureUri);

    assertThat(listener.getContext().currentScenarioDefinition.getName()).isEqualTo(scenarioName);
    assertThat(listener.getReporterCallbacks().getCurrentScenarioDataVariantIndex()).isEqualTo(-1);

    TigerEnvStatusDto status = envStatusController.getStatus();
    assertThat(status.getFeatureMap()).containsOnlyKeys(featureName);
    Map<String, ScenarioUpdate> scenarios = status.getFeatureMap().get(featureName).getScenarios();
    assertThat(scenarios).hasSize(1);
    assertThat(
            scenarios.get(listener.getContext().currentScenarioDefinition.getId()).getDescription())
        .isEqualTo(scenarioName);
  }

  @Test
  void testCaseStartedScenarioOutline() throws IOException {
    TestSourceRead event =
        new TestSourceRead(
            Instant.now(), featureUri, IOUtils.toString(featureUri, StandardCharsets.UTF_8));
    listener.handleTestSourceRead(event);
    assertThat(listener.getContext().currentFeaturePath()).isNull();

    TestCaseStarted startedEvent =
        new TestCaseStarted(Instant.now(), new ScenarioOutlineTestCaseAdapter());
    listener.handleTestCaseStarted(startedEvent);
    assertThat(listener.getContext().currentFeaturePath()).isEqualTo(featureUri);

    assertThat(listener.getContext().getTable().getHeaders())
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
    String scenarioId0 = "0-" + listener.getContext().currentScenarioDefinition.getId();
    assertThat(scenarios.get(scenarioId0).getDescription()).isEqualTo(scenarioOutlineName);
    assertThat(scenarios.get(scenarioId0).getVariantIndex()).isZero();

    listener.handleTestCaseStarted(startedEvent);
    assertThat(listener.getReporterCallbacks().getCurrentScenarioDataVariantIndex()).isEqualTo(1);
    status = envStatusController.getStatus();
    String scenarioId1 = "1-" + listener.getContext().currentScenarioDefinition.getId();
    scenarios = status.getFeatureMap().get(featureName).getScenarios();
    assertThat(scenarios).hasSize(2);
    assertThat(scenarios.get(scenarioId0).getDescription()).isEqualTo(scenarioOutlineName);
    assertThat(scenarios.get(scenarioId0).getVariantIndex()).isZero();
    assertThat(scenarios.get(scenarioId1).getDescription()).isEqualTo(scenarioOutlineName);
    assertThat(scenarios.get(scenarioId1).getVariantIndex()).isEqualTo(1);
  }

  /* TODO in order to get this working with the tighter integration with serenity test run event management
  we would need to get the test step adapter contain more meaningful info. So the test parts that rely on test steps are commented out
  */
  @Test
  void testStepsHandling() throws IOException {
    TestSourceRead event =
        new TestSourceRead(
            Instant.now(), featureUri, IOUtils.toString(featureUri, StandardCharsets.UTF_8));
    listener.handleTestSourceRead(event);
    TestCase testCase = new TestcaseAdapter();
    TestCaseStarted startedEvent = new TestCaseStarted(Instant.now(), testCase);
    listener.handleTestCaseStarted(startedEvent);

    TestStepStarted stepStartedEvent =
        new TestStepStarted(Instant.now(), testCase, new TestStepAdapter());
    listener.handleTestStepStarted(stepStartedEvent);
    // TODO assertThat(listener.getContext().getCurrentStep().getLocation().getLine()).isEqualTo(1);
    listener.handleTestStepFinished(
        new TestStepFinished(
            Instant.now(),
            testCase,
            new TestStepAdapter(),
            new Result(Status.PASSED, Duration.ofMillis(500), null)));

    listener.handleTestStepStarted(stepStartedEvent);
    // TODO assertThat(listener.getContext().getCurrentStep().getLocation().getLine()).isEqualTo(1);
    listener.handleTestStepFinished(
        new TestStepFinished(
            Instant.now(),
            testCase,
            new TestStepAdapter(),
            new Result(
                Status.FAILED, Duration.ofMillis(500), new TigerTestEnvException("Test Tiger A"))));

    TigerEnvStatusDto status = envStatusController.getStatus();
    assertThat(status.getFeatureMap()).containsOnlyKeys(featureName);
    ScenarioUpdate scenario =
        status
            .getFeatureMap()
            .get(featureName)
            .getScenarios()
            .get(listener.getContext().currentScenarioDefinition.getId());
    // TODO assertThat(scenario.getSteps().get("0").getStatus()).isEqualTo(TestResult.PASSED);
    // TODO assertThat(scenario.getSteps().get("1").getStatus()).isEqualTo(TestResult.FAILED);

  }

  @Test
  void testCaseFinished() throws IOException {
    long startms = System.currentTimeMillis();
    TestSourceRead event =
        new TestSourceRead(
            Instant.now(), featureUri, IOUtils.toString(featureUri, StandardCharsets.UTF_8));
    listener.handleTestSourceRead(event);
    TestCase testCase = new TestcaseAdapter();
    TestCaseStarted startedEvent = new TestCaseStarted(Instant.now(), testCase);
    listener.handleTestCaseStarted(startedEvent);

    TestStepStarted stepStartedEvent =
        new TestStepStarted(Instant.now(), testCase, new TestStepAdapter());
    listener.handleTestStepStarted(stepStartedEvent);
    listener.handleTestStepFinished(
        new TestStepFinished(
            Instant.now(),
            testCase,
            new TestStepAdapter(),
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
    ScenarioUpdate scenario =
        status
            .getFeatureMap()
            .get(featureName)
            .getScenarios()
            .get(listener.getContext().currentScenarioDefinition.getId());
    // TODO as the scenario status is dervided from all step status, doesnt work, see above
    //  assertThat(scenario.getStatus()).isEqualTo(TestResult.FAILED);

    assertThat(listener.getReporterCallbacks().getScFailed()).isEqualTo(1);
    assertThat(listener.getReporterCallbacks().getScPassed()).isZero();
  }

  private static class TestStepAdapter implements PickleStepTestStep {

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
          return 43;
        }

        @Override
        public Location getLocation() {
          return new Location(43, 3);
        }
      };
    }

    @Override
    public List<Argument> getDefinitionArgument() {
      return null;
    }

    @Override
    public StepArgument getStepArgument() {
      return null;
    }

    @Override
    public int getStepLine() {
      return 26;
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

    @Override
    public Integer getLine() {
      return 60;
    }

    @Override
    public Location getLocation() {
      return new Location(60, 13);
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
      return null;
    }

    @Override
    public List<TestStep> getTestSteps() {
      return null;
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

    @Override
    public Integer getLine() {
      return 164;
    }

    @Override
    public Location getLocation() {
      return new Location(164, 21);
    }

    @Override
    public String getName() {
      return scenarioOutlineName;
    }

    @Override
    public UUID getId() {
      return UUID.fromString(scenarioOutlineId);
    }
  }
}
