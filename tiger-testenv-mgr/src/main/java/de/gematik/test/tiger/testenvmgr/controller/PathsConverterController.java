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
package de.gematik.test.tiger.testenvmgr.controller;

import de.gematik.test.tiger.testenvmgr.env.ScenarioRunner;
import de.gematik.test.tiger.testenvmgr.service.PathsConverter;
import jakarta.validation.Valid;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.platform.engine.UniqueId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/paths")
@Slf4j
public class PathsConverterController {

  private final PathsConverter pathsConverter;

  public PathsConverterController(@Autowired PathsConverter pathsConverter) {
    this.pathsConverter = pathsConverter;
  }

  @PostMapping("/relativize")
  @ResponseStatus(value = HttpStatus.OK)
  public @Valid List<ScenarioRunner.ScenarioIdentifier> relativizePaths(
      @Valid @RequestBody List<ScenarioRunner.ScenarioIdentifier> identifiers) {

    var uniqueIds = mapToUniqueIds(identifiers);
    var withRelativePaths = pathsConverter.relativizePaths(uniqueIds);
    return mapToScenarioIdentifier(withRelativePaths);
  }

  @PostMapping("/resolve")
  @ResponseStatus(value = HttpStatus.OK)
  public @Valid List<ScenarioRunner.ScenarioIdentifier> resolvePaths(
      @Valid @RequestBody List<ScenarioRunner.ScenarioIdentifier> identifiers) {
    var uniqueIds = mapToUniqueIds(identifiers);
    var withResolvedPaths = pathsConverter.resolvePaths(uniqueIds);
    return mapToScenarioIdentifier(withResolvedPaths);
  }

  private List<UniqueId> mapToUniqueIds(List<ScenarioRunner.ScenarioIdentifier> identifiers) {
    return identifiers.stream()
        .map(ScenarioRunner.ScenarioIdentifier::uniqueId)
        .map(UniqueId::parse)
        .toList();
  }

  private List<ScenarioRunner.ScenarioIdentifier> mapToScenarioIdentifier(
      List<UniqueId> identifiers) {
    return identifiers.stream()
        .map(UniqueId::toString)
        .map(ScenarioRunner.ScenarioIdentifier::new)
        .toList();
  }
}
