/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger;

import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.lib.TigerDirector;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.controller.EnvStatusController;
import de.gematik.test.tiger.testenvmgr.data.TigerEnvStatusDto;
import de.gematik.test.tiger.testenvmgr.env.ScenarioUpdate;
import de.gematik.test.tiger.testenvmgr.env.TestResult;
import io.cucumber.plugin.event.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TestTigerCucumberListener {

    private TigerCucumberListener listener;

    private static EnvStatusController envStatusController;

    private final String featureName = "Authentifiziere Anwendung am IDP Server";
    private final UUID scenarioId = UUID.randomUUID();

    private final String scenarioName = "Auth - Gutfall - Validiere Claims";
    private final UUID scenarioOutlineId = UUID.randomUUID();
    private final String scenarioOutlineName = "Auth - Fehlende Parameter alle anderen";
    private final String featureFilePath = "src/test/resources/testdata/parser/bdd/authentication.feature";
    private final URI featureUri = new File(featureFilePath).toURI();

    @BeforeAll
    public static void startTiger() {
        TigerDirector.start();
        envStatusController = new EnvStatusController(TigerDirector.getTigerTestEnvMgr());
    }

    @BeforeEach
    public void initListener() {
        listener = new TigerCucumberListener();
        TigerGlobalConfiguration.putValue(TigerTestEnvMgr.CFG_PROP_NAME_LOCAL_PROXY_ADMIN_PORT, 9999);
        envStatusController.getStatus().getFeatureMap().clear();
    }

    @Test
    public void testCaseStartedSimpleScenario() throws IOException {
        TestSourceRead event = new TestSourceRead(Instant.now(), featureUri, IOUtils.toString(featureUri, StandardCharsets.UTF_8));
        listener.getSourceRead().receive(event);
        assertThat(listener.getIdFeatureMap()).hasSize(0);
        assertThat(listener.getUriFeatureMap().get(featureUri).getScenarios()).hasSize(7);

        TestCaseStarted startedEvent = new TestCaseStarted(Instant.now(), new TestcaseAdapter());

        listener.getCaseStarted().receive(startedEvent);

        assertThat(listener.getIdFeatureMap()).containsKey(scenarioId.toString());
        assertThat(listener.getScenarioStepsMap()).containsKey(scenarioId.toString());
        assertThat(listener.getCurrentScenarioId()).isEqualTo(scenarioId.toString());
        assertThat(listener.getCurrentScenarioDataVariantIndex()).isEqualTo(-1);

        TigerEnvStatusDto status = envStatusController.getStatus();
        assertThat(status.getFeatureMap()).containsOnlyKeys(featureName);
        Map<String, ScenarioUpdate> scenarios = status.getFeatureMap().get(featureName).getScenarios();
        assertThat(scenarios).hasSize(1);
        assertThat(scenarios.get(scenarioId.toString()).getDescription()).isEqualTo(scenarioName);
    }

    @Test
    public void testCaseStartedScenarioOutline() throws IOException {
        TestSourceRead event = new TestSourceRead(Instant.now(), featureUri, IOUtils.toString(featureUri, StandardCharsets.UTF_8));
        listener.getSourceRead().receive(event);
        assertThat(listener.getIdFeatureMap()).hasSize(0);
        assertThat(listener.getUriFeatureMap().get(featureUri).getScenarios()).hasSize(7);

        TestCaseStarted startedEvent = new TestCaseStarted(Instant.now(), new ScenarioOutlineTestCaseAdapter());
        listener.getCaseStarted().receive(startedEvent);
        assertThat(listener.getIdFeatureMap()).containsKey(scenarioOutlineId.toString());
        assertThat(listener.getScenarioStepsMap()).containsKey(scenarioOutlineId.toString());
        assertThat(listener.getCurrentScenarioId()).isEqualTo(scenarioOutlineId.toString());
        assertThat(listener.getCurrentScenarioDataVariantIndex()).isEqualTo(0);
        assertThat(listener.getCurrentScenarioDataVariant()).hasSize(7);
        assertThat(listener.getCurrentScenarioDataVariantKeys()).contains(
            "http_code", "err_id", "err", "client_id", "scope", "code_challenge", "code_challenge_method",
            "redirect_uri", "state", "nonce", "response_type");
        TigerEnvStatusDto status = envStatusController.getStatus();
        assertThat(status.getFeatureMap()).containsOnlyKeys(featureName);
        Map<String, ScenarioUpdate> scenarios = status.getFeatureMap().get(featureName).getScenarios();
        assertThat(scenarios).hasSize(1);
        assertThat(scenarios.get("0-" + scenarioOutlineId).getDescription()).isEqualTo(scenarioOutlineName);
        assertThat(scenarios.get("0-" + scenarioOutlineId).getVariantIndex()).isEqualTo(0);

        listener.getCaseStarted().receive(startedEvent);
        assertThat(listener.getIdFeatureMap()).containsKey(scenarioOutlineId.toString());
        assertThat(listener.getScenarioStepsMap()).containsKey(scenarioOutlineId.toString());
        assertThat(listener.getCurrentScenarioId()).isEqualTo(scenarioOutlineId.toString());
        assertThat(listener.getCurrentScenarioDataVariantIndex()).isEqualTo(1);
        status = envStatusController.getStatus();
        scenarios = status.getFeatureMap().get(featureName).getScenarios();
        assertThat(scenarios).hasSize(2);
        assertThat(scenarios.get("0-" + scenarioOutlineId).getDescription()).isEqualTo(scenarioOutlineName);
        assertThat(scenarios.get("0-" + scenarioOutlineId).getVariantIndex()).isEqualTo(0);
        assertThat(scenarios.get("1-" + scenarioOutlineId).getDescription()).isEqualTo(scenarioOutlineName);
        assertThat(scenarios.get("1-" + scenarioOutlineId).getVariantIndex()).isEqualTo(1);
    }

    @Test
    public void testStepsHandling() throws IOException {
        TestSourceRead event = new TestSourceRead(Instant.now(), featureUri, IOUtils.toString(featureUri, StandardCharsets.UTF_8));
        listener.getSourceRead().receive(event);
        TestCase testCase = new TestcaseAdapter();
        TestCaseStarted startedEvent = new TestCaseStarted(Instant.now(), testCase);
        listener.getCaseStarted().receive(startedEvent);

        TestStepStarted stepStartedEvent = new TestStepStarted(Instant.now(), testCase, new TestStepAdapter());
        listener.getStepStarted().receive(stepStartedEvent);
        listener.getStepFinished().receive(new TestStepFinished(Instant.now(), testCase, new TestStepAdapter(), new Result(Status.PASSED,
            Duration.ofMillis(500), null)));
        assertThat(listener.getCurrentStepIndex()).isEqualTo(1);

        listener.getStepStarted().receive(stepStartedEvent);
        listener.getStepFinished().receive(new TestStepFinished(Instant.now(), testCase, new TestStepAdapter(), new Result(Status.FAILED,
            Duration.ofMillis(500), null)));
        assertThat(listener.getCurrentStepIndex()).isEqualTo(2);


        TigerEnvStatusDto status = envStatusController.getStatus();
        assertThat(status.getFeatureMap()).containsOnlyKeys(featureName);
        ScenarioUpdate scenario = status.getFeatureMap().get(featureName).getScenarios().get(scenarioId.toString());
        assertThat(scenario.getSteps().get("0").getStatus()).isEqualTo(TestResult.PASSED);
        assertThat(scenario.getSteps().get("1").getStatus()).isEqualTo(TestResult.FAILED);

    }

    @Test
    public void testCaseFinished() throws IOException {
        TestSourceRead event = new TestSourceRead(Instant.now(), featureUri, IOUtils.toString(featureUri, StandardCharsets.UTF_8));
        listener.getSourceRead().receive(event);
        TestCase testCase = new TestcaseAdapter();
        TestCaseStarted startedEvent = new TestCaseStarted(Instant.now(), testCase);
        listener.getCaseStarted().receive(startedEvent);

        TestStepStarted stepStartedEvent = new TestStepStarted(Instant.now(), testCase, new TestStepAdapter());
        listener.getStepStarted().receive(stepStartedEvent);
        listener.getStepFinished().receive(new TestStepFinished(Instant.now(), testCase, new TestStepAdapter(), new Result(Status.FAILED,
            Duration.ofMillis(500), null)));
        TestCaseFinished finishedEvent = new TestCaseFinished(Instant.now(), testCase,
            new Result(Status.PASSED, Duration.ofMillis(500), null));
        listener.getCaseFinished().receive(finishedEvent);

        assertThat(listener.getScenarioStepsMap()).doesNotContainKey(scenarioId.toString());
        assertThat(listener.getScFailed()).isEqualTo(0);
        assertThat(listener.getScPassed()).isEqualTo(1);

        File logFile = new File("target/rbellogs/" + listener.getFileNameFor(scenarioName, -1));
        assertThat(logFile).exists();
        assertThat(logFile).content(StandardCharsets.UTF_8).hasSizeGreaterThan(800).contains(scenarioName);

        TigerEnvStatusDto status = envStatusController.getStatus();
        assertThat(status.getFeatureMap()).containsOnlyKeys(featureName);
        ScenarioUpdate scenario = status.getFeatureMap().get(featureName).getScenarios().get(scenarioId.toString());
        assertThat(scenario.getSteps().get("0").getStatus()).isEqualTo(TestResult.FAILED);

        finishedEvent = new TestCaseFinished(Instant.now(), testCase, new Result(Status.FAILED, Duration.ofMillis(500), null));
        listener.getCaseFinished().receive(finishedEvent);
        assertThat(listener.getScFailed()).isEqualTo(1);
        assertThat(listener.getScPassed()).isEqualTo(1);

    }

    private static class TestStepAdapter implements PickleStepTestStep {

        private final UUID stepUuid = UUID.randomUUID();

        @Override
        public String getCodeLocation() {
            return "featurefile:43";
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
            return null;
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
            return 0;
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
            return scenarioOutlineId;
        }
    }
}
