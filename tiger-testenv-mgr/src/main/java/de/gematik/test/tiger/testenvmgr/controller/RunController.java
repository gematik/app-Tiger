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
import jakarta.validation.Valid;
import java.util.NoSuchElementException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/run")
@Slf4j
public class RunController {

  private final ScenarioRunner scenarioRunner;

  public RunController(@Autowired ScenarioRunner scenarioRunner) {
    this.scenarioRunner = scenarioRunner;
  }

  @PostMapping
  @ResponseStatus(value = HttpStatus.NO_CONTENT)
  public void runScenario(
      @Valid @RequestBody ScenarioRunner.ScenarioIdentifier scenarioIdentifier) {
    scenarioRunner.runTest(scenarioIdentifier);
  }

  @ExceptionHandler(value = NoSuchElementException.class)
  public ResponseEntity<String> handleNotFound() {
    return ResponseEntity.notFound().build();
  }
}
