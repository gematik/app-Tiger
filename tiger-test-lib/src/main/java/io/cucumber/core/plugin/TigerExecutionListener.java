/*
 * Copyright (c) 2024 gematik GmbH
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

package io.cucumber.core.plugin;

import static io.cucumber.junit.platform.engine.Constants.EXECUTION_DRY_RUN_PROPERTY_NAME;
import static org.awaitility.Awaitility.await;

import de.gematik.test.tiger.lib.TigerDirector;
import de.gematik.test.tiger.testenvmgr.api.model.mapper.TigerTestIdentifier;
import de.gematik.test.tiger.testenvmgr.env.ScenarioRunner;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

/**
 * We register this listener to save the scenarios that are found by the cucumber engine. The main
 * purpose of finding them, is that we then get unique TestIdentifiers that make it much easier to
 * rerun tests. See
 * de.gematik.test.tiger.testenvmgr.env.ScenarioRunner#runTest(org.junit.platform.launcher.TestIdentifier)
 */
@Slf4j
@NoArgsConstructor
public class TigerExecutionListener implements TestExecutionListener {

  @Override
  public void testPlanExecutionStarted(TestPlan testPlan) {
    ScenarioRunner.addTigerScenarios(testPlan);
  }

  @Override
  public void testPlanExecutionFinished(TestPlan testPlan) {
    testPlan
        .getConfigurationParameters()
        .getBoolean(EXECUTION_DRY_RUN_PROPERTY_NAME)
        .ifPresent(
            dryRun -> {
              if (dryRun) {
                log.debug("Dry run detected. Will wait for tiger shutdown");
                await()
                    .logging(log::trace)
                    .pollInterval(1, TimeUnit.SECONDS)
                    .atMost(
                        TigerDirector.getLibConfig().getPauseExecutionTimeoutSeconds(),
                        TimeUnit.SECONDS)
                    .until(() -> TigerDirector.getTigerTestEnvMgr().isShutDown());
              }
            });
  }

  @Override
  public void dynamicTestRegistered(TestIdentifier testIdentifier) {
    if (testIdentifier.isTest()) {
      ScenarioRunner.addTigerScenarios(
          List.of(new TigerTestIdentifier(testIdentifier, testIdentifier.getDisplayName())));
    }
  }
}
