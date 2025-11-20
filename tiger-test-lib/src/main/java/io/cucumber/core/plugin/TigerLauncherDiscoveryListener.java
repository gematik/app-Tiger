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

import static io.cucumber.core.options.Constants.EXECUTION_DRY_RUN_PROPERTY_NAME;
import static io.cucumber.core.options.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.JUNIT_PLATFORM_SHORT_NAMING_STRATEGY_EXAMPLE_NAME_PROPERTY_NAME;

import de.gematik.test.tiger.common.config.ConfigurationValuePrecedence;
import de.gematik.test.tiger.common.config.TigerConfigurationKeys;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.lib.TigerInitializer;
import java.util.HashMap;
import java.util.Map;
import org.junit.platform.engine.ConfigurationParameters;
import org.junit.platform.launcher.LauncherDiscoveryListener;
import org.junit.platform.launcher.LauncherDiscoveryRequest;

/**
 * We want to be able to force a dry run where the tests are only discovered and not executed.
 *
 * <p>When running tests with maven or with a junit5 driver class, the Junit Platform launcher will
 * automatically find test engines to discover and run tests. By having this listener we can change
 * the dry run configuration before the cucumber engine starts discovering tests.
 */
public class TigerLauncherDiscoveryListener implements LauncherDiscoveryListener {

  @Override
  public void launcherDiscoveryStarted(LauncherDiscoveryRequest request) {
    if (!DetectTigerTestsKt.isATigerTest(request)) {
      return;
    }
    var initializer = new TigerInitializer();
    initializer.runWithSafelyInitialized(() -> {});
    backupInitialConfigParameters(request.getConfigurationParameters());
    forceDryRun();
    setExampleNamingStrategy();
    setDefaultFilterTags(request.getConfigurationParameters());
  }

  private void setDefaultFilterTags(ConfigurationParameters configurationParameters) {
    if (configurationParameters.get(FILTER_TAGS_PROPERTY_NAME).isEmpty()) {
      System.setProperty(FILTER_TAGS_PROPERTY_NAME, "not @Ignore");
    }
  }

  private void backupInitialConfigParameters(ConfigurationParameters configurationParameters) {
    String cucumberConfigKey =
        TigerConfigurationKeys.CUCUMBER_ENGINE_RUNTIME_CONFIGURATION.downsampleKey();
    Map<String, String> currentConfiguration = TigerGlobalConfiguration.readMap(cucumberConfigKey);
    if (currentConfiguration.isEmpty()) {
      var newConfiguration = new HashMap<String, String>();
      configurationParameters.keySet().stream()
          .filter(key -> key.startsWith("cucumber."))
          .filter(key -> !key.equals("cucumber.filter.tags"))
          .forEach(
              key -> newConfiguration.put(key, configurationParameters.get(key).orElseThrow()));
      TigerGlobalConfiguration.putValue(
          cucumberConfigKey, newConfiguration, ConfigurationValuePrecedence.RUNTIME_EXPORT);
    }
  }

  private void forceDryRun() {
    boolean autoRunOnStart = TigerConfigurationKeys.RUN_TESTS_ON_START.getValueOrDefault();
    System.setProperty(EXECUTION_DRY_RUN_PROPERTY_NAME, Boolean.toString(!autoRunOnStart));
  }

  private static void setExampleNamingStrategy() {
    if (System.getProperty(JUNIT_PLATFORM_SHORT_NAMING_STRATEGY_EXAMPLE_NAME_PROPERTY_NAME)
        == null) {
      System.setProperty(JUNIT_PLATFORM_SHORT_NAMING_STRATEGY_EXAMPLE_NAME_PROPERTY_NAME, "pickle");
    }
  }
}
