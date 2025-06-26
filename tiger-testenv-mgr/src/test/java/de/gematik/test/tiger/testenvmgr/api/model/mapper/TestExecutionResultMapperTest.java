/*
 *
 * Copyright 2021-2025 gematik GmbH
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
package de.gematik.test.tiger.testenvmgr.api.model.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.test.tiger.testenvmgr.api.model.ExecutionResultDto;
import de.gematik.test.tiger.testenvmgr.api.model.TestExecutionResultDto;
import de.gematik.test.tiger.testenvmgr.api.model.TestExecutionResultTestsInnerDto;
import java.time.OffsetDateTime;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class TestExecutionResultMapperTest {
  private TestExecutionResultMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = Mappers.getMapper(TestExecutionResultMapper.class);
    TestDescriptionMapper testDescriptionMapper = Mappers.getMapper(TestDescriptionMapper.class);
    mapper.setTestDescriptionMapper(testDescriptionMapper);
  }

  @Test
  void testMappingTesTestExecutionStatus_to_TestExecutionResultDto() {
    val testExecutionStatus = TestExecutionStatusFactory.createTestExecutionStatus();

    TestExecutionResultDto testExecutionResultDto =
        mapper.testExecutionStatusToTestExecutionResultDto(testExecutionStatus);

    assertThat(testExecutionResultDto.getTestRunStarted()).isBeforeOrEqualTo(OffsetDateTime.now());
    assertThat(testExecutionResultDto.getTestRunFinished()).isBeforeOrEqualTo(OffsetDateTime.now());
    assertThat(testExecutionResultDto.getTestRunStarted())
        .isBeforeOrEqualTo(testExecutionResultDto.getTestRunFinished());
    assertThat(testExecutionResultDto.getResult().getResult())
        .isEqualTo(ExecutionResultDto.ResultEnum.FAILED);
    assertThat(testExecutionResultDto.getResult().getFailureMessage()).contains("test failed");

    var individualResults = testExecutionResultDto.getTests();
    TestExecutionResultTestsInnerDto aborted = individualResults.get(2);
    TestExecutionResultTestsInnerDto failed = individualResults.get(3);

    assertThat(individualResults)
        .extracting(r -> r.getResult().getResult())
        .containsExactly(
            ExecutionResultDto.ResultEnum.SUCCESSFUL,
            ExecutionResultDto.ResultEnum.SUCCESSFUL,
            ExecutionResultDto.ResultEnum.ABORTED,
            ExecutionResultDto.ResultEnum.FAILED);

    assertThat(aborted.getResult().getFailureMessage()).isEqualTo("test aborted");
    assertThat(failed.getResult().getFailureMessage()).isEqualTo("test failed");
  }
}
