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

import com.google.common.collect.Streams;
import de.gematik.test.tiger.common.config.TigerConfigurationKeys;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.testenvmgr.api.model.TestExecutionRequestDto;
import de.gematik.test.tiger.testenvmgr.util.ScenarioCollector;
import io.cucumber.messages.types.Examples;
import io.cucumber.messages.types.Location;
import io.cucumber.messages.types.Scenario;
import io.cucumber.messages.types.TableRow;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.TestTag;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.discovery.UniqueIdSelector;
import org.junit.platform.engine.support.descriptor.ClasspathResourceSource;
import org.junit.platform.engine.support.descriptor.FilePosition;
import org.junit.platform.engine.support.descriptor.FileSource;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ScenarioRunner {

  private static final LinkedHashSet<TestIdentifier> scenarios =
      new LinkedHashSet<>(); // NOSONAR - important to keep order

  // Must be single threaded because we do not want to have multiple tests runs at the same time.
  // Since the Launcher.execute() runs synchronously, we have the guarantee that the runner waits
  // for the test to finish.
  private static final ExecutorService scenarioExecutionService =
      Executors.newSingleThreadExecutor();

  private final Map<UUID, TestExecutionStatus> testExecutionResults = new ConcurrentHashMap<>();

  public static void addScenarios(Collection<TestIdentifier> newScenarios) {
    scenarios.addAll(newScenarios);
  }

  public static void addScenarios(TestPlan testPlan) {
    addScenarios(ScenarioCollector.collectScenarios(testPlan));
  }

  public TestExecutionStatus enqueueExecutionSelectedTests(
      TestExecutionRequestDto testExecutionRequest) {

    var sources = testExecutionRequest.getSourceFiles();
    var tags = testExecutionRequest.getTags();
    var uniqueIds = testExecutionRequest.getTestUniqueIds();

    // if one of the sourceFiles / tags / uniqueIds lists is empty, their filter is not used. That
    // means all scenarios are selected for the next filter round.
    List<UniqueIdSelector> selectors =
        scenarios.stream()
            .filter(testIdentifier -> sources.isEmpty() || anyFileMatches(sources, testIdentifier))
            .filter(testIdentifier -> tags.isEmpty() || anyTagMatches(tags, testIdentifier))
            .filter(
                testIdentifier ->
                    uniqueIds.isEmpty() || anyUniqueIdMatches(uniqueIds, testIdentifier))
            .map(t -> DiscoverySelectors.selectUniqueId(t.getUniqueId()))
            .toList();
    return runTests(selectors);
  }

  public TestExecutionStatus enqueueExecutionAllTests() {
    List<UniqueIdSelector> selectors =
        scenarios.stream().map(t -> DiscoverySelectors.selectUniqueId(t.getUniqueId())).toList();
    return runTests(selectors);
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

  public TestExecutionStatus runTest(ScenarioIdentifier scenarioIdentifier) {
    UniqueIdSelector selector = DiscoverySelectors.selectUniqueId(scenarioIdentifier.uniqueId());
    return runTests(List.of(selector));
  }

  protected TestExecutionStatus runTests(List<? extends DiscoverySelector> selectors) {
    var initialConfiguration =
        TigerGlobalConfiguration.readMap(
            TigerConfigurationKeys.CUCUMBER_ENGINE_RUNTIME_CONFIGURATION.downsampleKey());
    initialConfiguration.put(EXECUTION_DRY_RUN_PROPERTY_NAME, "false");
    val uuidForThisRun = UUID.randomUUID();
    LauncherDiscoveryRequest runRequest =
        request().selectors(selectors).configurationParameters(initialConfiguration).build();

    Launcher launcher = LauncherFactory.create();
    TestPlan testPlan = launcher.discover(runRequest);

    val summaryListener = new TigerSummaryListener(testPlan);
    val testExecutionStatus = new TestExecutionStatus(uuidForThisRun, testPlan, summaryListener);
    testExecutionResults.put(uuidForThisRun, testExecutionStatus);
    scenarioExecutionService.execute(() -> launcher.execute(testPlan, summaryListener));
    return testExecutionStatus;
  }

  public static UniqueId findScenarioUniqueId(
      Scenario scenario, URI featurePath, boolean isOutline, int scenarioDataVariantIndex) {
    ScenarioRunner.ScenarioLocation scenarioLocation;
    if (isOutline) {
      var exampleLocation =
          Streams.mapWithIndex(
                  scenario.getExamples().stream().map(Examples::getTableBody).flatMap(List::stream),
                  Pair::of) // (tableRow, index)
              .filter(pair -> pair.getRight() == scenarioDataVariantIndex)
              .map(Pair::getLeft)
              .map(TableRow::getLocation)
              .findFirst()
              .orElseThrow();
      scenarioLocation = new ScenarioRunner.ScenarioLocation(featurePath, exampleLocation);
    } else {
      scenarioLocation = new ScenarioRunner.ScenarioLocation(featurePath, scenario.getLocation());
    }
    return ScenarioRunner.findScenarioByLocation(scenarioLocation)
        .orElseThrow()
        .getUniqueIdObject();
  }

  private boolean sameFile(TestIdentifier test, String filePath) {
    return test.getSource()
        .map(
            source -> {
              if (source instanceof FileSource fileSource) {
                return fileSource.getUri().normalize().toString().equals(filePath);
              } else if (source instanceof ClasspathResourceSource classpathResourceSource) {
                return ("classpath:" + classpathResourceSource.getClasspathResourceName())
                    .equals(filePath);
              } else {
                return false;
              }
            })
        .orElse(false);
  }

  private boolean anyFileMatches(List<String> files, TestIdentifier test) {
    return files.stream().anyMatch(f -> sameFile(test, f));
  }

  private boolean anyTagMatches(List<String> tags, TestIdentifier test) {
    return tags.stream()
        .anyMatch(t -> test.getTags().stream().map(TestTag::getName).toList().contains(t));
  }

  private boolean anyUniqueIdMatches(List<String> uniqueIds, TestIdentifier test) {
    return uniqueIds.stream().anyMatch(u -> test.getUniqueId().equals(u));
  }

  public Optional<TestExecutionStatus> getTestResults(UUID testRunId) {
    return Optional.ofNullable(testExecutionResults.get(testRunId));
  }

  public record ScenarioIdentifier(String uniqueId) {}

  public record ScenarioLocation(URI featurePath, Location testVariantLocation) {
    public FilePosition filePosition() {
      return FilePosition.from(
          testVariantLocation.getLine().intValue(),
          testVariantLocation.getColumn().orElseThrow().intValue());
    }
  }

  public static Set<TestIdentifier> getScenarios() {
    return Collections.unmodifiableSet(scenarios);
  }

  public record TestExecutionStatus(
      UUID testRunId, TestPlan testPlan, TigerSummaryListener testSummaryListener) {
    public TestExecutionSummary getSummary() {
      return testSummaryListener.getSummary();
    }

    public List<TigerSummaryListener.TestResultWithId> getIndividualTestResults() {
      return testSummaryListener.getIndividualTestResults();
    }
  }
}
