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

package io.cucumber.junit;

import de.gematik.test.tiger.exceptions.FailMessageOverrider;
import io.cucumber.core.runner.Runner;
import io.cucumber.core.runtime.RunnerSupplier;
import io.cucumber.core.runtime.ThreadLocalRunnerSupplier;
import io.cucumber.plugin.event.TestStepFinished;
import lombok.extern.slf4j.Slf4j;

// A Wrapper for the ThreadLocalRunnerSupplier which ensure
// that we override the failure message before any other handler gets the chance to handle the
// event.
// Will be called when the TigerCucumberRunner is started via the constructor instead of its main
// method.
@Slf4j
public class TigerRunnerSupplier implements RunnerSupplier {
  private final ThreadLocalRunnerSupplier threadLocalRunnerSupplier;

  public TigerRunnerSupplier(ThreadLocalRunnerSupplier runnerSupplier) {
    this.threadLocalRunnerSupplier = runnerSupplier;
  }

  @Override
  public Runner get() {
    Runner runner = threadLocalRunnerSupplier.get();
    runner
        .getBus()
        .registerHandlerFor(TestStepFinished.class, FailMessageOverrider::overrideFailureMessage);
    return runner;
  }
}
