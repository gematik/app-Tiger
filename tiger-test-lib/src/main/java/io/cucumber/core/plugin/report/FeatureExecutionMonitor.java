/*
 *
 * Copyright 2021-2026 gematik GmbH
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
package io.cucumber.core.plugin.report;

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import io.cucumber.plugin.event.TestCaseStarted;
import java.net.URI;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * This class is responsible for monitoring the execution of feature files in a Cucumber test suite.
 * It keeps track of the currently executing feature file and clears test variables when a new
 * feature file starts executing.
 */
@Slf4j
public class FeatureExecutionMonitor {

  private URI currentFeatureFile;

  @Setter private Consumer<URI> onFeatureCompleted;

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
   * Called at the start of each test case. Updates {@code currentFeatureFile}. If a different
   * feature file starts and its predecessor's completion has not yet been signaled (fallback path
   * when pickle counts were unknown), the completion is fired here.
   */
  public void startTestCase(TestCaseStarted testCaseStartedEvent) {
    URI newUri = testCaseStartedEvent.getTestCase().getUri();
    getCurrentFeatureFile()
        .filter(featureFile -> !featureFile.equals(newUri))
        .ifPresent(
            previous -> {
              TigerGlobalConfiguration.clearTestVariables();
            });

    currentFeatureFile = newUri;
  }

  /** This method is called at the end of the test run. It clears the test variables. */
  public void stopTestRun() {
    if (currentFeatureFile != null) {
      notifyFeatureCompleted(currentFeatureFile);
    }
    TigerGlobalConfiguration.clearTestVariables();
  }

  private void notifyFeatureCompleted(URI completedFeatureFile) {
    if (onFeatureCompleted != null) {
      try {
        onFeatureCompleted.accept(completedFeatureFile);
      } catch (Exception e) {
        log.warn("Error in onFeatureCompleted callback for {}", completedFeatureFile, e);
      }
    }
  }
}
