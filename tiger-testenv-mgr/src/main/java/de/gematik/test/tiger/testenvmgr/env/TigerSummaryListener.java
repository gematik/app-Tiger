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
 *
 */

package de.gematik.test.tiger.testenvmgr.env;

import de.gematik.test.tiger.testenvmgr.api.model.ExecutionResultDto;
import de.gematik.test.tiger.testenvmgr.util.ScenarioCollector;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

public class TigerSummaryListener extends SummaryGeneratingListener {
  private final Map<UniqueId, TestResultWithId> individualTestResults = new LinkedHashMap<>();

  public TigerSummaryListener(TestPlan testPlan) {
    preFillIndividualTestResults(testPlan);
  }

  private void preFillIndividualTestResults(TestPlan testPlan) {
    ScenarioCollector.collectScenarios(testPlan)
        .forEach(
            s ->
                individualTestResults.put(
                    s.getUniqueIdObject(),
                    new TestResultWithId(
                        s,
                        new ExecutionResultDto().result(ExecutionResultDto.ResultEnum.PENDING))));
  }

  @Override
  public void executionStarted(TestIdentifier testIdentifier) {
    super.executionStarted(testIdentifier);
    // we just keep track of the individual tests and not of the container nodes
    // that means a test identifier for the container node will trigger this callback but we don't
    // have a result in our map
    getIndividualTestResult(testIdentifier.getUniqueIdObject())
        .ifPresent(
            testResultWithId ->
                testResultWithId.setTestExecutionResult(
                    new ExecutionResultDto().result(ExecutionResultDto.ResultEnum.RUNNING)));
  }

  @Override
  public void executionFinished(
      TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
    super.executionFinished(testIdentifier, testExecutionResult);
    getIndividualTestResult(testIdentifier.getUniqueIdObject())
        .ifPresent(
            testResultWithId ->
                testResultWithId.setTestExecutionResult(
                    new ExecutionResultDto()
                        .result(testResultStatusToResultEnum(testExecutionResult.getStatus()))
                        .failureMessage(
                            testExecutionResult
                                .getThrowable()
                                .map(Throwable::getMessage)
                                .orElse(null))));
  }

  private Optional<TestResultWithId> getIndividualTestResult(UniqueId uniqueId) {
    return Optional.ofNullable(individualTestResults.get(uniqueId));
  }

  public List<TestResultWithId> getIndividualTestResults() {
    // A copy to avoid concurrency problems
    return List.copyOf(individualTestResults.values());
  }

  @AllArgsConstructor
  @Getter
  public class TestResultWithId {
    private final TestIdentifier testIdentifier;
    @Setter private ExecutionResultDto testExecutionResult;

    public UniqueId getUniqueId() {
      return testIdentifier.getUniqueIdObject();
    }

    public Optional<ExecutionResultDto> getTestExecutionResult() {
      return Optional.ofNullable(testExecutionResult);
    }
  }

  public ExecutionResultDto.ResultEnum testResultStatusToResultEnum(
      TestExecutionResult.Status status) {
    return switch (status) {
      case SUCCESSFUL -> ExecutionResultDto.ResultEnum.SUCCESSFUL;
      case ABORTED -> ExecutionResultDto.ResultEnum.ABORTED;
      case FAILED -> ExecutionResultDto.ResultEnum.FAILED;
    };
  }
}