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

package de.gematik.test.tiger.testenvmgr.api.model.mapper;

import de.gematik.test.tiger.testenvmgr.api.model.ExecutionResultDto;
import de.gematik.test.tiger.testenvmgr.api.model.TestExecutionResultDto;
import de.gematik.test.tiger.testenvmgr.api.model.TestExecutionResultTestsInnerDto;
import de.gematik.test.tiger.testenvmgr.env.ScenarioRunner;
import de.gematik.test.tiger.testenvmgr.env.TigerSummaryListener;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.Setter;
import lombok.val;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = TestDescriptionMapper.class)
public abstract class TestExecutionResultMapper {

  @Autowired @Setter private TestDescriptionMapper testDescriptionMapper;

  @Mapping(source = "testPlan", target = "tests")
  public TestExecutionResultDto testExecutionStatusToTestExecutionResultDto(
      ScenarioRunner.TestExecutionStatus testExecutionStatus) {
    TestExecutionSummary summary = testExecutionStatus.getSummary();
    val testExecutionResultDto = new TestExecutionResultDto();
    if (summary == null) { // Testplan is not yet started
      testExecutionResultDto.result(new ExecutionResultDto(ExecutionResultDto.ResultEnum.PENDING));
    } else {
      testExecutionResultDto
          .testRunStarted(mapTime(summary.getTimeStarted()).orElse(null))
          .testRunFinished(mapTime(summary.getTimeFinished()).orElse(null))
          .result(testExecutionStatusToExecutionResultDto(testExecutionStatus));
    }
    testExecutionResultDto.tests(
        testExecutionStatus.getIndividualTestResults().stream()
            .map(this::testResultWithIdToTestExecutionResultTestsInnerDto)
            .toList());
    return testExecutionResultDto;
  }

  private Optional<OffsetDateTime> mapTime(long timeInMilliseconds) {
    if (timeInMilliseconds == 0) {
      // In the MutableTestExecutionSummary, the times are not nullable and stay 0 if not set.
      return Optional.empty();
    } else {
      return Optional.of(
          OffsetDateTime.ofInstant(
              Instant.ofEpochMilli(timeInMilliseconds), ZoneId.systemDefault()));
    }
  }

  public TestExecutionResultTestsInnerDto testResultWithIdToTestExecutionResultTestsInnerDto(
      TigerSummaryListener.TestResultWithId testResultWithId) {
    return new TestExecutionResultTestsInnerDto()
        .result(testResultWithId.getTestExecutionResult().orElse(null))
        .test(
            testDescriptionMapper.testIdentifierToTestDescription(
                testResultWithId.getTestIdentifier()));
  }

  public ExecutionResultDto testExecutionStatusToExecutionResultDto(
      ScenarioRunner.TestExecutionStatus executionResult) {
    Optional<OffsetDateTime> startTime = mapTime(executionResult.getSummary().getTimeStarted());
    Optional<OffsetDateTime> endTime = mapTime(executionResult.getSummary().getTimeFinished());
    if (startTime.isEmpty()) {
      return new ExecutionResultDto(ExecutionResultDto.ResultEnum.PENDING);
    }
    if (endTime.isEmpty()) {
      return new ExecutionResultDto(ExecutionResultDto.ResultEnum.RUNNING);
    }
    val failures = executionResult.getSummary().getFailures();
    if (failures.isEmpty()) {
      return new ExecutionResultDto(ExecutionResultDto.ResultEnum.SUCCESSFUL);
    } else {
      return new ExecutionResultDto(ExecutionResultDto.ResultEnum.FAILED)
          .failureMessage(
              failures.stream()
                  .map(e -> e.getException().getMessage())
                  .collect(Collectors.joining("\n")));
    }
  }
}
