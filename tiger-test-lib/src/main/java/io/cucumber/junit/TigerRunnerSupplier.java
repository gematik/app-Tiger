/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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
