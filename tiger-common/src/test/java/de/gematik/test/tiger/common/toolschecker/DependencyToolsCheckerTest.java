/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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
        TigerConfigurationKeys.TRAFFIC_VISUALIZATION_ACTIVE.getKey());
  }

  @Test
  void testFeatureInactive() {
    DependencyToolsChecker checker = new DependencyToolsChecker();

    TigerConfigurationKeys.TRAFFIC_VISUALIZATION_ACTIVE.putValue(false);

    DependencyCheckResult result = checker.areNecessaryDependenciesAvailable();

    assertTrue(result.isValid());
    assertEquals("", result.validationMessage());
  }

  @Test
  void testWindowsEnvironment() throws Exception {
    restoreSystemProperties(
        () -> {
          DependencyToolsChecker checker = new DependencyToolsChecker();

          TigerConfigurationKeys.TRAFFIC_VISUALIZATION_ACTIVE.putValue(true);
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

          TigerConfigurationKeys.TRAFFIC_VISUALIZATION_ACTIVE.putValue(true);
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

          TigerConfigurationKeys.TRAFFIC_VISUALIZATION_ACTIVE.putValue(true);
          System.setProperty("os.name", "Linux");

          DependencyCheckResult result = checker.areNecessaryDependenciesAvailable();

          assertFalse(result.isValid());
          assertEquals(
              "missing required 'lsof' command in your operating system",
              result.validationMessage());
        });
  }
}
