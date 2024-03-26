/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package io.cucumber.core.plugin.report;

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import io.cucumber.plugin.event.TestCaseStarted;
import java.net.URI;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * This class is responsible for monitoring the execution of feature files in a Cucumber test suite.
 * It keeps track of the currently executing feature file and clears test variables when a new
 * feature file starts executing.
 */
@Slf4j
public class FeatureExecutionMonitor {

  private URI currentFeatureFile;

  /**
   * This method is called at the start of the test run. It initializes the currentFeatureFile to
   * null.
   */
  public void startTestRun() {
    currentFeatureFile = null;
  }

  /**
   * This method returns the URI of the currently executing feature file.
   *
   * @return Optional containing the URI of the current feature file, or an empty Optional if no
   *     feature file is currently executing.
   */
  public Optional<URI> getCurrentFeatureFile() {
    return Optional.ofNullable(currentFeatureFile);
  }

  /**
   * This method is called at the start of each test case. It updates the currentFeatureFile to the
   * URI of the feature file of the currently executing test case. If a new feature file starts
   * executing, it clears the test variables.
   *
   * @param testCaseStartedEvent The event triggered at the start of a test case.
   */
  public void startTestCase(TestCaseStarted testCaseStartedEvent) {
    getCurrentFeatureFile()
        .filter(featureFile -> !featureFile.equals(testCaseStartedEvent.getTestCase().getUri()))
        .ifPresent(featureFile -> TigerGlobalConfiguration.clearTestVariables());

    currentFeatureFile = testCaseStartedEvent.getTestCase().getUri();
  }

  /** This method is called at the end of the test run. It clears the test variables. */
  public void stopTestRun() {
    TigerGlobalConfiguration.clearTestVariables();
  }
}
