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
package io.cucumber.core.plugin;

import static io.cucumber.core.options.Constants.FEATURES_PROPERTY_NAME;
import static org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request;
import static org.mockito.Mockito.mock;

import de.gematik.test.tiger.lib.TigerDirector;
import de.gematik.test.tiger.server.TigerBuildPropertiesService;
import de.gematik.test.tiger.testenvmgr.controller.EnvStatusController;
import de.gematik.test.tiger.testenvmgr.env.ScenarioRunner;
import io.cucumber.junit.platform.engine.Constants;
import io.cucumber.plugin.event.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherFactory;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.properties.SystemProperties;

/**
 * Base test support class for tests verifying forwarding of mismatch notes and env status updates.
 * Provides common tiger initialization, feature source publication and helper utilities.
 */
@ExtendWith(SystemStubsExtension.class)
abstract class AbstractTigerSerenityEnvStatusTest {

  @SystemStub
  protected SystemProperties systemProperties =
      new SystemProperties(
          Constants.PLUGIN_PROPERTY_NAME, TigerSerenityReporterPlugin.class.getName());

  protected static EnvStatusController envStatusController;
  protected TigerSerenityReporterPlugin listener;

  protected final String featureFilePath =
      "src/test/resources/testdata/parser/bdd/features/authentication.feature";
  protected final String featureName = "Authentifiziere Anwendung am IDP Server";
  protected final String scenarioName = "Auth - Gutfall - Validiere Claims";
  protected final URI featureUri = new File(featureFilePath).toURI();
  protected final UUID scenarioId = UUID.randomUUID();

  @BeforeAll
  static void startTigerBase() {
    TigerDirector.start();
    envStatusController =
        new EnvStatusController(
            TigerDirector.getTigerTestEnvMgr(), mock(TigerBuildPropertiesService.class));
  }

  @BeforeEach
  void initBase() {
    listener = new TigerSerenityReporterPlugin();
    Launcher launcher = LauncherFactory.create();
    TestPlan testPlan =
        launcher.discover(
            request().configurationParameter(FEATURES_PROPERTY_NAME, featureFilePath).build());
    new TigerExecutionListener().testPlanExecutionStarted(testPlan);
    envStatusController.getStatus().getFeatureMap().clear();
  }

  @AfterEach
  void tearDownBase() {
    ScenarioRunner.clearScenarios();
  }

  @SneakyThrows(IOException.class)
  protected void publishFeatureSource() {
    listener.handleTestSourceRead(
        new TestSourceRead(
            Instant.now(), featureUri, IOUtils.toString(featureUri, StandardCharsets.UTF_8)));
  }

  protected String findScenarioUniqueId(TestCase testCase) {
    return ScenarioRunner.findScenarioUniqueId(
            featureUri,
            new io.cucumber.core.plugin.report.LocationConverter()
                .convertLocation(testCase.getLocation()))
        .toString();
  }

  @AllArgsConstructor
  @Getter
  protected static class ArgumentAdapter implements Argument {
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

  @Getter
  @Builder
  protected static class TestStepAdapter implements PickleStepTestStep {
    private final int line;
    private final int column;
    private final StepArgument stepArgument;

    @Builder.Default
    private final String codeLocation =
        "io.cucumber.core.plugin.TestGlue.testGlueMethod(java.lang.String,java.lang.String)";

    @Builder.Default
    private final String pattern =
        "test step resolves {tigerResolvedString} and does not resolve {string}";

    @Builder.Default
    private final String stepText =
        "test step resolves ${replaced.string} and does not resolve \"${not.replaced.string}\"";

    @Builder.Default private final List<Argument> definitionArgument = List.of();
    @Builder.Default private final UUID stepUuid = UUID.randomUUID();

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

  @Getter
  protected class BasicTestCase implements TestCase {
    private final List<TestStep> steps;

    protected BasicTestCase(List<TestStep> steps) {
      this.steps = steps;
    }

    @Override
    public Integer getLine() {
      return 60;
    }

    @Override
    public Location getLocation() {
      return new io.cucumber.plugin.event.Location(60, 3);
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
      return steps;
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
}
