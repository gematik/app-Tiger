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

package de.gematik.test.tiger.testenvmgr.env;

import static io.cucumber.core.options.Constants.EXECUTION_DRY_RUN_PROPERTY_NAME;
import static org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request;

import de.gematik.test.tiger.common.config.TigerConfigurationKeys;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import io.cucumber.messages.types.Examples;
import io.cucumber.messages.types.Location;
import io.cucumber.messages.types.Scenario;
import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.discovery.UniqueIdSelector;
import org.junit.platform.engine.support.descriptor.ClasspathResourceSource;
import org.junit.platform.engine.support.descriptor.FilePosition;
import org.junit.platform.engine.support.descriptor.FileSource;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherFactory;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ScenarioRunner {

  private static final Set<TestIdentifier> scenarios = new HashSet<>();

  public static void addScenarios(Collection<TestIdentifier> newScenarios) {
    var onlyCucumberEngineScenarios =
        newScenarios.stream()
            .filter(
                t ->
                    t.getUniqueIdObject().getSegments().stream()
                        .anyMatch(
                            s -> s.getType().equals("engine") && s.getValue().equals("cucumber")))
            .toList();
    scenarios.addAll(onlyCucumberEngineScenarios);
  }

  @SneakyThrows
  public void runTest(ScenarioIdentifier scenarioIdentifier) {
    runTest(
        findScenarioByUniqueId(scenarioIdentifier)
            .orElseThrow(
                () ->
                    new NoSuchElementException("did not find a scenario matching the given id.")));
  }

  public static void clearScenarios() {
    scenarios.clear();
  }

  private static boolean doesLocationMatch(
      TestIdentifier testIdentifier, ScenarioLocation scenarioLocation) {
    TestSource testSource = testIdentifier.getSource().orElseThrow();
    if (testSource instanceof FileSource fileSource) {
      return areUrisSameFile(fileSource.getUri(), scenarioLocation.featurePath())
          && fileSource.getPosition().orElseThrow().equals(scenarioLocation.filePosition());
    } else if (testSource instanceof ClasspathResourceSource classpathSource) {
      return ("classpath:" + classpathSource.getClasspathResourceName())
              .equals(scenarioLocation.featurePath().toString())
          && classpathSource.getPosition().orElseThrow().equals(scenarioLocation.filePosition());
    } else {
      return false;
    }
  }

  private static boolean areUrisSameFile(URI uri1, URI uri2) {
    return uri1.normalize().equals(uri2.normalize());
  }

  public static Optional<TestIdentifier> findScenarioByLocation(ScenarioLocation scenarioLocation) {
    return scenarios.stream().filter(s -> doesLocationMatch(s, scenarioLocation)).findAny();
  }

  public static Optional<TestIdentifier> findScenarioByUniqueId(
      ScenarioIdentifier scenarioIdentifier) {
    return scenarios.stream()
        .filter(s -> Objects.equals(scenarioIdentifier.uniqueId(), s.getUniqueId()))
        .findAny();
  }

  protected void runTest(TestIdentifier testIdentifier) {
    UniqueIdSelector selector =
        DiscoverySelectors.selectUniqueId(testIdentifier.getUniqueIdObject());
    var initialConfiguration =
        TigerGlobalConfiguration.readMap(
            TigerConfigurationKeys.CUCUMBER_ENGINE_RUNTIME_CONFIGURATION.downsampleKey());
    initialConfiguration.put(EXECUTION_DRY_RUN_PROPERTY_NAME, "false");
    LauncherDiscoveryRequest rerunRequest =
        request().selectors(selector).configurationParameters(initialConfiguration).build();

    Launcher launcher = LauncherFactory.create();
    launcher.execute(rerunRequest);
  }

  public static UniqueId findScenarioUniqueId(
      Scenario scenario, URI featurePath, boolean isOutline, int scenarioDataVariantIndex) {
    ScenarioRunner.ScenarioLocation scenarioLocation;
    if (isOutline) {
      var exampleLocation =
          scenario.getExamples().stream()
              .map(Examples::getTableBody)
              .flatMap(List::stream)
              .toList()
              .get(scenarioDataVariantIndex)
              .getLocation();
      scenarioLocation = new ScenarioRunner.ScenarioLocation(featurePath, exampleLocation);
    } else {
      scenarioLocation = new ScenarioRunner.ScenarioLocation(featurePath, scenario.getLocation());
    }
    return ScenarioRunner.findScenarioByLocation(scenarioLocation)
        .orElseThrow()
        .getUniqueIdObject();
  }

  public record ScenarioIdentifier(String uniqueId) {}

  public record ScenarioLocation(URI featurePath, Location testVariantLocation) {
    public FilePosition filePosition() {
      return FilePosition.from(
          testVariantLocation.getLine().intValue(),
          testVariantLocation.getColumn().orElseThrow().intValue());
    }
  }
}
