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

package de.gematik.test.tiger.maven.adapter.mojos;

import static org.awaitility.Awaitility.await;

import de.gematik.test.tiger.lib.TigerDirector;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.awaitility.core.ConditionTimeoutException;

/**
 * This plugin allows to start up the Tiger test environment configured in a specific tiger yaml
 * file in the pre-integration-test phase. To trigger use the "setup-testenv" goal. For more details
 * please refer to the README.adoc file in the project root.
 */
@EqualsAndHashCode(callSuper = false)
@Data
@Mojo(name = "setup-testenv", defaultPhase = LifecyclePhase.INITIALIZE)
public class TestEnvironmentMojo extends AbstractMojo {

  /** Skip running this plugin. Default is false. */
  @Parameter private boolean skip = false;

  /** Timespan to keep the test environment up and running in seconds */
  @Parameter(defaultValue = "86400")
  private long autoShutdownAfterSeconds = Duration.ofDays(1).getSeconds();

  private boolean isRunning = false;

  public TestEnvironmentMojo() {
    super();
  }

  @Override
  public void execute() {
    if (skip) {
      getLog().info("Skipping");
      return;
    }
    isRunning = true;
    TigerDirector.startStandaloneTestEnvironment();
    getLog().info("Tiger standalone test environment is setup!");
    try {
      await()
          .atMost(autoShutdownAfterSeconds, TimeUnit.SECONDS)
          .pollInterval(200, TimeUnit.MILLISECONDS)
          .until(
              () ->
                  !isRunning()
                      || TigerDirector.getTigerTestEnvMgr() == null
                      || TigerDirector.getTigerTestEnvMgr().isShutDown());
      if (TigerDirector.getTigerTestEnvMgr() != null) {
        TigerDirector.getTigerTestEnvMgr().shutDown();
      }
    } catch (ConditionTimeoutException cte) {
      getLog().info("Tiger Testenvironment TIMEOUT reached, shutting down...");
      if (TigerDirector.getTigerTestEnvMgr() != null) {
        TigerDirector.getTigerTestEnvMgr().shutDown();
      }
    }
    getLog().info("Tiger standalone test environment is shut down!");
    isRunning = false;
  }

  void abort() {
    isRunning = false;
  }
}
