/*
 * Copyright (c) 2024 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.common.toolschecker;

import static org.junit.jupiter.api.Assertions.*;
import static uk.org.webcompere.systemstubs.SystemStubs.restoreSystemProperties;

import de.gematik.test.tiger.common.config.TigerConfigurationKeys;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DependencyToolsCheckerTest {

  @BeforeEach
  void setup() {
    TigerGlobalConfiguration.deleteFromAllSources(
        TigerConfigurationKeys.ExperimentalFeatures.TRAFFIC_VISUALIZATION_ACTIVE.getKey());
  }

  @Test
  void testFeatureInactive() {
    DependencyToolsChecker checker = new DependencyToolsChecker();

    TigerConfigurationKeys.ExperimentalFeatures.TRAFFIC_VISUALIZATION_ACTIVE.putValue(false);

    DependencyCheckResult result = checker.areNecessaryDependenciesAvailable();

    assertTrue(result.isValid());
    assertEquals("", result.validationMessage());
  }

  @Test
  void testWindowsEnvironment() throws Exception {
    restoreSystemProperties(
        () -> {
          DependencyToolsChecker checker = new DependencyToolsChecker();

          TigerConfigurationKeys.ExperimentalFeatures.TRAFFIC_VISUALIZATION_ACTIVE.putValue(true);
          System.setProperty("os.name", "Windows");

          DependencyCheckResult result = checker.areNecessaryDependenciesAvailable();

          assertTrue(result.isValid());
          assertEquals("", result.validationMessage());
        });
  }

  @Test
  void testUnixEnvironmentWithLsof() throws Exception {
    restoreSystemProperties(
        () -> {
          DependencyToolsChecker checker = Mockito.spy(new DependencyToolsChecker());
          Mockito.doReturn(true).when(checker).hasLsof();

          TigerConfigurationKeys.ExperimentalFeatures.TRAFFIC_VISUALIZATION_ACTIVE.putValue(true);
          System.setProperty("os.name", "Linux");

          DependencyCheckResult result = checker.areNecessaryDependenciesAvailable();

          assertTrue(result.isValid());
          assertEquals("", result.validationMessage());
        });
  }

  @Test
  void testUnixEnvironmentWithoutLsof() throws Exception {
    restoreSystemProperties(
        () -> {
          DependencyToolsChecker checker = Mockito.spy(new DependencyToolsChecker());
          Mockito.doReturn(false).when(checker).hasLsof();

          TigerConfigurationKeys.ExperimentalFeatures.TRAFFIC_VISUALIZATION_ACTIVE.putValue(true);
          System.setProperty("os.name", "Linux");

          DependencyCheckResult result = checker.areNecessaryDependenciesAvailable();

          assertFalse(result.isValid());
          assertEquals(
              "missing required 'lsof' command in your operating system",
              result.validationMessage());
        });
  }
}
