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
package io.cucumber.junit;

import static de.gematik.test.tiger.common.config.TigerConfigurationKeys.RUN_TESTS_ON_START;
import static io.cucumber.core.options.Constants.ANSI_COLORS_DISABLED_PROPERTY_NAME;
import static io.cucumber.core.options.Constants.EXECUTION_DRY_RUN_PROPERTY_NAME;
import static io.cucumber.core.options.Constants.FEATURES_PROPERTY_NAME;
import static io.cucumber.core.options.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.core.options.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.core.options.Constants.OBJECT_FACTORY_PROPERTY_NAME;
import static io.cucumber.core.options.Constants.PLUGIN_PROPERTY_NAME;
import static io.cucumber.core.options.Constants.SNIPPET_TYPE_PROPERTY_NAME;
import static io.cucumber.core.options.Constants.UUID_GENERATOR_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.FILTER_NAME_PROPERTY_NAME;
import static org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request;

import de.gematik.test.tiger.lib.TigerDirector;
import de.gematik.test.tiger.lib.TigerInitializer;
import de.gematik.test.tiger.testenvmgr.api.model.mapper.TigerTestIdentifier;
import de.gematik.test.tiger.testenvmgr.env.FeatureUpdate;
import de.gematik.test.tiger.testenvmgr.env.ScenarioRunner;
import de.gematik.test.tiger.testenvmgr.env.ScenarioUpdate;
import de.gematik.test.tiger.testenvmgr.env.TestResult;
import de.gematik.test.tiger.testenvmgr.env.TigerStatusUpdate;
import io.cucumber.core.options.CommandlineOptionsParser;
import io.cucumber.core.options.RuntimeOptions;
import io.cucumber.core.plugin.Options;
import io.cucumber.core.plugin.TigerSerenityReporterPlugin;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherFactory;

/**
 * Runner used to start the tests via IntelliJ. Since Tiger version 3.3.0 we no longer support JUnit
 * 4.
 *
 * <p>Starting the tests over maven or over a driver class do not require any runner, since the
 * JUnit Platform api ensures the "cucumber" engine is used.
 *
 * <p>In this class we read the options passed over the Intellij run configuration and start the
 * tests via the org.junit.platform.launcher.Launcher;
 *
 * <p>Important is that we register the TigerSerenityReporterPlugin and then Tiger will start
 * together with the cucumber engine.
 */
@Slf4j
public class TigerCucumberRunner {

  public static void main(String[] args) {
    log.info("Starting TigerCucumberRunner.main()...");

    RuntimeOptions cmdLineOptions =
        (new CommandlineOptionsParser(System.out)).parse(args).build(); // NOSONAR

    Map<String, String> configurationParameters =
        convertToConfigurationParametersMap(cmdLineOptions);

    LauncherDiscoveryRequest request =
        request().configurationParameters(configurationParameters).build();

    discoverAndRunTests(request);

    System.exit(0);
  }

  public static void discoverAndRunTests(
      LauncherDiscoveryRequest request, TestExecutionListener... listeners) {
    TigerInitializer tigerInitializer = new TigerInitializer();
    tigerInitializer.runWithSafelyInitialized(
        () -> {
          var runNow = RUN_TESTS_ON_START.getValueOrDefault().booleanValue();
          // We set these listeners, so that they also see replayed test executions
          ScenarioRunner.setTestListener(listeners);
          Launcher launcher = LauncherFactory.create();
          discoverTestsAndInformWorkflowUi(launcher, request);
          if (runNow) {
            launcher.execute(request, listeners);
          }
        });
  }

  private static void discoverTestsAndInformWorkflowUi(
      Launcher launcher, LauncherDiscoveryRequest discoveryRequest) {
    TestPlan testplan = launcher.discover(discoveryRequest);
    ScenarioRunner.addTigerScenarios(testplan);
    informWorkflowUiAboutScenarios();
  }

  private static void informWorkflowUiAboutScenarios() {
    var featureMap = new LinkedHashMap<String, FeatureUpdate>();

    for (TigerTestIdentifier t : ScenarioRunner.getTigerScenarios()) {
      var scenarioUniqueId = t.getTestIdentifier().getUniqueId();
      var scenarioUpdate =
          ScenarioUpdate.builder()
              .isDryRun(true)
              .description(t.getScenarioName())
              .uniqueId(scenarioUniqueId)
              .status(TestResult.TEST_DISCOVERED)
              .build();
      featureMap
          .computeIfAbsent(
              t.getFeatureName(),
              featureName ->
                  FeatureUpdate.builder()
                      .description(featureName)
                      .scenarios(new LinkedHashMap<>())
                      .sourcePathForSource(t.getTestIdentifier().getSource().orElse(null))
                      .build())
          .getScenarios()
          .put(scenarioUniqueId, scenarioUpdate);
    }
    TigerDirector.getTigerTestEnvMgr()
        .receiveTestEnvUpdate(TigerStatusUpdate.builder().featureMap(featureMap).build());
  }

  private static Map<String, String> convertToConfigurationParametersMap(
      RuntimeOptions runtimeOptions) {
    // IntelliJ passes the command line options to the runner. The CommandlineOptionsParser parses
    // them and delivers a RuntimeOptions object. However this can only be used if we directly
    // start the io.cucumber.core.runtime.Runtime class. Since we want to use the JUnit Platform
    // API,
    // we need to pass the options as "configuration parameters" which will be picked up by the
    // cucumber engine.

    Map<String, String> map = new HashMap<>();

    if (runtimeOptions.isMonochrome()) {
      // If false we don't set it, so an eventual override via system properties can take place.
      map.put(ANSI_COLORS_DISABLED_PROPERTY_NAME, Boolean.toString(true));
    }
    if (runtimeOptions.isDryRun()) {
      // If false we don't set it, so an eventual override via system properties can take place.
      map.put(EXECUTION_DRY_RUN_PROPERTY_NAME, Boolean.toString(true));
    }
    if (!runtimeOptions.getFeaturePaths().isEmpty()) {
      map.put(
          FEATURES_PROPERTY_NAME,
          commaSeparatedString(runtimeOptions.getFeaturePaths(), URI::toString));
    }
    if (!runtimeOptions.getNameFilters().isEmpty()) {
      map.put(
          FILTER_NAME_PROPERTY_NAME,
          commaSeparatedString(runtimeOptions.getNameFilters(), Pattern::toString));
    }

    if (!runtimeOptions.getTagExpressions().isEmpty()) {
      map.put(
          FILTER_TAGS_PROPERTY_NAME,
          commaSeparatedString(runtimeOptions.getTagExpressions(), Object::toString));
    }

    if (!runtimeOptions.getGlue().isEmpty()) {
      map.put(GLUE_PROPERTY_NAME, commaSeparatedString(runtimeOptions.getGlue(), URI::toString));
    }

    Optional.ofNullable(runtimeOptions.getObjectFactoryClass())
        .ifPresent(
            objectFactoryClass ->
                map.put(OBJECT_FACTORY_PROPERTY_NAME, objectFactoryClass.getName()));
    Optional.ofNullable(runtimeOptions.getUuidGeneratorClass())
        .ifPresent(
            uuidGeneratorClass ->
                map.put(UUID_GENERATOR_PROPERTY_NAME, uuidGeneratorClass.getName()));
    Optional.ofNullable(runtimeOptions.getSnippetType())
        .ifPresent(
            snippetType -> map.put(SNIPPET_TYPE_PROPERTY_NAME, snippetType.name().toLowerCase()));

    map.put(
        PLUGIN_PROPERTY_NAME,
        String.join(
            ",",
            // Important to keep the TigerSerenityReporterPlugin as first plugin for the
            // de.gematik.test.tiger.exceptions.FailMessageOverrider
            // to modify the error messages before the others get to see them
            TigerSerenityReporterPlugin.class.getName(),
            commaSeparatedString(runtimeOptions.plugins(), Options.Plugin::pluginString)));

    return map;
  }

  private static <T> String commaSeparatedString(
      Collection<T> collection, Function<T, String> toStringFunction) {
    return collection.stream().map(toStringFunction).collect(Collectors.joining(","));
  }
}
