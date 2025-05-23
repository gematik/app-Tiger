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

import de.gematik.test.tiger.testenvmgr.api.model.TestExecutionInformationDto;
import java.net.URI;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class TestExecutionInformationMapperTest {

  private TestExecutionInformationMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = Mappers.getMapper(TestExecutionInformationMapper.class);
    TestDescriptionMapper testDescriptionMapper = Mappers.getMapper(TestDescriptionMapper.class);
    mapper.setTestDescriptionMapper(testDescriptionMapper);
  }

  @Test
  void testMappingTestExecutionStatus_to_TestExecutionInformation() {
    val testExecutionStatus = TestExecutionStatusFactory.createTestExecutionStatus();

    TestExecutionInformationDto testExecutionInformationDto =
        mapper.mapFrom(testExecutionStatus, "http://localhost:8080");

    assertThat(testExecutionInformationDto.getTestRunId())
        .isEqualTo(TestExecutionStatusFactory.TEST_UUID);
    assertThat(testExecutionInformationDto.getResultUrl())
        .isEqualTo(
            URI.create("http://localhost:8080/tests/runs/" + TestExecutionStatusFactory.TEST_UUID));
    assertThat(testExecutionInformationDto.getTestsToExecute())
        .hasSize(4)
        .extracting("displayName")
        .containsExactly(
            "dummyTest1: dummy display name",
            "dummyTest2: dummy display name",
            "dummyContainerDescriptor: dummy display name:dummyTest3: dummy display name",
            "dummyContainerDescriptor: dummy display name:dummyTest4: dummy display name");
  }
}
