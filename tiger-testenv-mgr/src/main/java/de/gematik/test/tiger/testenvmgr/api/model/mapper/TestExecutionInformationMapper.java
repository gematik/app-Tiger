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

import de.gematik.test.tiger.testenvmgr.api.model.TestDescriptionDto;
import de.gematik.test.tiger.testenvmgr.api.model.TestExecutionInformationDto;
import de.gematik.test.tiger.testenvmgr.env.ScenarioRunner;
import de.gematik.test.tiger.testenvmgr.util.ScenarioCollector;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import lombok.Setter;
import org.junit.platform.launcher.TestPlan;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = TestDescriptionMapper.class)
public abstract class TestExecutionInformationMapper {

  @Autowired @Setter private TestDescriptionMapper testDescriptionMapper;

  @Mapping(source = "testRunId", target = "testRunId")
  @Mapping(source = "testRunId", target = "resultUrl")
  @Mapping(source = "testPlan", target = "testsToExecute")
  public abstract TestExecutionInformationDto mapFrom(
      ScenarioRunner.TestExecutionStatus testExecutionStatus, @Context String basePath);

  public List<TestDescriptionDto> testPlanToTestDescriptionDtos(TestPlan testPlan) {
    LinkedHashSet<TigerTestIdentifier> tigerTestIdentifiers =
        ScenarioCollector.collectTigerScenarios(testPlan);

    return tigerTestIdentifiers.stream()
        .map(testDescriptionMapper::tigerTestIdentifierToTestDescription)
        .toList();
  }

  public URI uuidToResultUri(UUID value, @Context String basePath) {
    return URI.create(basePath + "/tests/runs/" + value.toString());
  }
}
