/*
 * Copyright 2025 gematik GmbH
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
 */

package org.apache.maven.surefire.junitplatform;

import static de.gematik.test.tiger.common.config.TigerConfigurationKeys.TIGER_CUSTOM_FAILSAFE_PROVIDER_ACTIVE;
import static io.cucumber.junit.platform.engine.Constants.FEATURES_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;
import static java.util.Arrays.stream;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.logging.Level.WARNING;
import static org.apache.maven.surefire.api.booter.ProviderParameterNames.EXCLUDE_JUNIT5_ENGINES_PROP;
import static org.apache.maven.surefire.api.booter.ProviderParameterNames.TESTNG_EXCLUDEDGROUPS_PROP;
import static org.apache.maven.surefire.api.booter.ProviderParameterNames.TESTNG_GROUPS_PROP;
import static org.apache.maven.surefire.api.report.ConsoleOutputCapture.startCapture;
import static org.apache.maven.surefire.api.report.RunMode.NORMAL_RUN;
import static org.apache.maven.surefire.api.report.RunMode.RERUN_TEST_AFTER_FAILURE;
import static org.apache.maven.surefire.api.testset.TestListResolver.optionallyWildcardFilter;
import static org.apache.maven.surefire.api.util.TestsToRun.fromClass;
import static org.apache.maven.surefire.shared.utils.StringUtils.isBlank;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectUniqueId;
import static org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request;

import de.gematik.test.tiger.common.config.TigerConfigurationKeys;
import io.cucumber.core.plugin.TigerSerenityReporterPlugin;
import io.cucumber.junit.TigerCucumberRunner;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Logger;
import org.apache.maven.surefire.api.provider.AbstractProvider;
import org.apache.maven.surefire.api.provider.ProviderParameters;
import org.apache.maven.surefire.api.report.ReporterException;
import org.apache.maven.surefire.api.report.ReporterFactory;
import org.apache.maven.surefire.api.suite.RunResult;
import org.apache.maven.surefire.api.testset.TestSetFailedException;
import org.apache.maven.surefire.api.util.ScanResult;
import org.apache.maven.surefire.api.util.SurefireReflectionException;
import org.apache.maven.surefire.api.util.TestsToRun;
import org.apache.maven.surefire.shared.utils.StringUtils;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.Filter;
import org.junit.platform.launcher.EngineFilter;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TagFilter;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;

/**
 * Tiger Changes: <br>
 * - in {@link TigerFailsafeProvider#newConfigurationParameters()} we configure cucumber properties
 * to only execute cucumber tests. <br>
 * - in {@link TigerFailsafeProvider#newFilters()} we include only the cucumber engine. <br>
 * - in {@link TigerFailsafeProvider#execute(TestsToRun, RunListenerAdapter)} we delegate the
 * execution of tests to the method {@link
 * TigerCucumberRunner#discoverAndRunTests(LauncherDiscoveryRequest, TestExecutionListener...)}
 * which performs test discovery, informs Workflow UI and conditionally executes the tests according
 * to the configured option {@link TigerConfigurationKeys#RUN_TESTS_ON_START}
 *
 * <p>This class is based on the JUnitPlatformProvider of the <a
 * href="https://github.com/apache/maven-surefire/blob/master/surefire-providers/surefire-junit-platform/src/main/java/org/apache/maven/surefire/junitplatform/JUnitPlatformProvider.java">maven-surefire
 * project</a> (Apache License, Version 2.0 )
 *
 * <p>We modified the class to only run cucumber tests and start the TigerDirector before starting
 * the tests.
 */
public class TigerFailsafeProvider extends AbstractProvider {
  static final String CONFIGURATION_PARAMETERS = "configurationParameters";
  private final ProviderParameters parameters;
  private final LazyLauncher launcher;
  private final Filter<?>[] filters;
  private final Map<String, String> configurationParameters;

  public TigerFailsafeProvider(ProviderParameters parameters) {
    TIGER_CUSTOM_FAILSAFE_PROVIDER_ACTIVE.putValue(true);
    this.parameters = parameters;
    this.launcher = new LazyLauncher();
    filters = newFilters();
    configurationParameters = newConfigurationParameters();
  }

  @Override
  public Iterable<Class<?>> getSuites() {
    try {
      return scanClasspath();
    } finally {
      closeLauncher();
    }
  }

  private TestsToRun scanClasspath() {
    TigerTestPlanScannerFilter filter =
        new TigerTestPlanScannerFilter(launcher, filters, configurationParameters);
    ScanResult scanResult = parameters.getScanResult();
    TestsToRun scannedClasses = scanResult.applyFilter(filter, parameters.getTestClassLoader());
    return parameters.getRunOrderCalculator().orderTestClasses(scannedClasses);
  }

  @Override
  public RunResult invoke(Object forkTestSet) throws TestSetFailedException, ReporterException {
    ReporterFactory reporterFactory = parameters.getReporterFactory();
    final RunResult runResult;
    try {
      RunListenerAdapter adapter =
          new RunListenerAdapter(reporterFactory.createTestReportListener());
      adapter.setRunMode(NORMAL_RUN);
      startCapture(adapter);
      setupJunitLogger();
      if (forkTestSet instanceof TestsToRun testsToRun) {
        invokeAllTests(testsToRun, adapter);
      } else if (forkTestSet instanceof Class<?> clazz) {
        invokeAllTests(fromClass(clazz), adapter);
      } else if (forkTestSet == null) {
        invokeAllTests(scanClasspath(), adapter);
      } else {
        throw new IllegalArgumentException("Unexpected value of forkTestSet: " + forkTestSet);
      }
    } finally {
      runResult = reporterFactory.close();
    }
    return runResult;
  }

  private static void setupJunitLogger() {
    Logger logger = Logger.getLogger("org.junit");
    if (logger.getLevel() == null) {
      logger.setLevel(WARNING);
    }
  }

  private Filter<?>[] newFilters() {
    List<Filter<?>> newFilters = new ArrayList<>();

    getPropertiesList(TESTNG_GROUPS_PROP).map(TagFilter::includeTags).ifPresent(newFilters::add);

    getPropertiesList(TESTNG_EXCLUDEDGROUPS_PROP)
        .map(TagFilter::excludeTags)
        .ifPresent(newFilters::add);

    of(optionallyWildcardFilter(parameters.getTestRequest().getTestListResolver()))
        .filter(f -> !f.isEmpty())
        .filter(f -> !f.isWildcard())
        .map(TestMethodFilter::new)
        .ifPresent(newFilters::add);

    // including only the cucumber engine
    newFilters.add(EngineFilter.includeEngines("cucumber"));

    getPropertiesList(EXCLUDE_JUNIT5_ENGINES_PROP)
        .map(EngineFilter::excludeEngines)
        .ifPresent(newFilters::add);

    return newFilters.toArray(new Filter<?>[0]);
  }

  private void invokeAllTests(TestsToRun testsToRun, RunListenerAdapter adapter) {
    try {
      execute(testsToRun, adapter);
    } finally {
      closeLauncher();
    }
    // Rerun failing tests if requested
    int count = parameters.getTestRequest().getRerunFailingTestsCount();
    if (count > 0 && adapter.hasFailingTests()) {
      adapter.setRunMode(RERUN_TEST_AFTER_FAILURE);
      for (int i = 0; i < count; i++) {
        try {
          // Replace the "discoveryRequest" so that it only specifies the failing tests
          LauncherDiscoveryRequest discoveryRequest =
              buildLauncherDiscoveryRequestForRerunFailures(adapter);
          // Reset adapter's recorded failures and invoke the failed tests again
          adapter.reset();
          launcher.execute(discoveryRequest, adapter);
          // If no tests fail in the rerun, we're done
          if (!adapter.hasFailingTests()) {
            break;
          }
        } finally {
          closeLauncher();
        }
      }
    }
  }

  private void closeLauncher() {
    if (launcher != null) {
      try {
        launcher.close();
      } catch (Exception e) {
        throw new SurefireReflectionException(e);
      }
    }
  }

  private LauncherDiscoveryRequest buildLauncherDiscoveryRequestForRerunFailures(
      RunListenerAdapter adapter) {
    LauncherDiscoveryRequestBuilder builder =
        request().filters(filters).configurationParameters(configurationParameters);
    // Iterate over recorded failures
    for (TestIdentifier identifier : new LinkedHashSet<>(adapter.getFailures().keySet())) {
      builder.selectors(selectUniqueId(identifier.getUniqueId()));
    }
    return builder.build();
  }

  private void execute(TestsToRun testsToRun, RunListenerAdapter adapter) {
    if (testsToRun.allowEagerReading()) {
      List<DiscoverySelector> selectors = new ArrayList<>();
      testsToRun.iterator().forEachRemaining(c -> selectors.add(selectClass(c.getName())));

      LauncherDiscoveryRequest launcherDiscoveryRequest =
          request()
              .filters(filters)
              .configurationParameters(configurationParameters)
              .selectors(selectors)
              .build();
      TigerCucumberRunner.discoverAndRunTests(launcherDiscoveryRequest, adapter);
    } else {
      testsToRun
          .iterator()
          .forEachRemaining(
              c -> {
                LauncherDiscoveryRequest launcherDiscoveryRequest =
                    request()
                        .filters(filters)
                        .configurationParameters(configurationParameters)
                        .selectors(selectClass(c.getName()))
                        .build();
                TigerCucumberRunner.discoverAndRunTests(launcherDiscoveryRequest, adapter);
              });
    }
  }

  private Map<String, String> newConfigurationParameters() {
    String content = parameters.getProviderProperties().get(CONFIGURATION_PARAMETERS);
    var features = System.getProperty("tiger.features", "src/test/resources/features");
    var tags = System.getProperty("tiger.filter.tags", "not @Ignore");
    var glue = System.getProperty("tiger.glue", "de.gematik.test.tiger.glue");
    var tigerSerenityPluginName = TigerSerenityReporterPlugin.class.getName();
    var plugin = System.getProperty("tiger.plugin", tigerSerenityPluginName);
    if (!plugin.contains("io.cucumber.core.plugin.TigerSerenityReporterPlugin")) {
      plugin = String.join(",", tigerSerenityPluginName, plugin);
    }

    var defaultMap =
        Map.of(
            FEATURES_PROPERTY_NAME,
            features,
            GLUE_PROPERTY_NAME,
            glue,
            FILTER_TAGS_PROPERTY_NAME,
            tags,
            PLUGIN_PROPERTY_NAME,
            plugin);
    if (content == null) {
      return defaultMap;
    }
    try (StringReader reader = new StringReader(content)) {
      Map<String, String> result = new HashMap<>(defaultMap);
      Properties props = new Properties();
      props.load(reader);
      props.stringPropertyNames().forEach(key -> result.put(key, props.getProperty(key)));
      return result;
    } catch (IOException e) {
      throw new UncheckedIOException("Error reading " + CONFIGURATION_PARAMETERS, e);
    }
  }

  private Optional<List<String>> getPropertiesList(String key) {
    String property = parameters.getProviderProperties().get(key);
    return isBlank(property)
        ? empty()
        : of(
            stream(property.split(",")).filter(StringUtils::isNotBlank).map(String::trim).toList());
  }
}
