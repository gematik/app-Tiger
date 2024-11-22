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

package de.gematik.test.tiger.testenvmgr.controller;

import de.gematik.test.tiger.testenvmgr.api.TestsApi;
import de.gematik.test.tiger.testenvmgr.api.model.ErrorDto;
import de.gematik.test.tiger.testenvmgr.api.model.TestDescriptionDto;
import de.gematik.test.tiger.testenvmgr.api.model.TestExecutionInformationDto;
import de.gematik.test.tiger.testenvmgr.api.model.TestExecutionRequestDto;
import de.gematik.test.tiger.testenvmgr.api.model.TestExecutionResultDto;
import de.gematik.test.tiger.testenvmgr.api.model.mapper.TestDescriptionMapper;
import de.gematik.test.tiger.testenvmgr.api.model.mapper.TestExecutionInformationMapper;
import de.gematik.test.tiger.testenvmgr.api.model.mapper.TestExecutionResultMapper;
import de.gematik.test.tiger.testenvmgr.env.ScenarioRunner;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@ConditionalOnProperty(name = "tiger.lib.enableTestManagementRestApi", matchIfMissing = true)
public class TestsApiController implements TestsApi {

  private final TestDescriptionMapper testDescriptionMapper;
  private final TestExecutionInformationMapper testExecutionInformationMapper;
  private final ScenarioRunner scenarioRunner;
  private final TestExecutionResultMapper testExecutionResultMapper;

  public TestsApiController(
      @Autowired TestDescriptionMapper testDescriptionMapper,
      @Autowired TestExecutionInformationMapper testExecutionInformationMapper,
      @Autowired TestExecutionResultMapper testExecutionResultMapper,
      @Autowired ScenarioRunner scenarioRunner) {
    assertCucumberEngineInClasspath();
    this.testDescriptionMapper = testDescriptionMapper;
    this.testExecutionInformationMapper = testExecutionInformationMapper;
    this.testExecutionResultMapper = testExecutionResultMapper;
    this.scenarioRunner = scenarioRunner;
  }

  private void assertCucumberEngineInClasspath() {
    String cucumberClassName = "io.cucumber.junit.platform.engine.CucumberTestEngine";
    try {
      Class.forName(cucumberClassName);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException(
          "Required class '" + cucumberClassName + "' is not present in the classpath.");
    }
  }

  @Override
  public ResponseEntity<List<TestDescriptionDto>> getAvailableTests() {
    var scenarios = ScenarioRunner.getScenarios();
    var testDescriptionDtos =
        scenarios.stream().map(testDescriptionMapper::testIdentifierToTestDescription).toList();
    return ResponseEntity.ok(testDescriptionDtos);
  }

  @Override
  public ResponseEntity<TestExecutionInformationDto> postExecutionRequest(
      TestExecutionRequestDto testExecutionRequest) {
    ScenarioRunner.TestExecutionStatus testExecutionStatus =
        scenarioRunner.enqueueExecutionSelectedTests((testExecutionRequest));
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(testExecutionInformationMapper.mapFrom(testExecutionStatus, getBasePath()));
  }

  @Override
  public ResponseEntity<TestExecutionInformationDto> postExecutionRequestAllTests() {
    ScenarioRunner.TestExecutionStatus testExecutionStatus =
        scenarioRunner.enqueueExecutionAllTests();
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(testExecutionInformationMapper.mapFrom(testExecutionStatus, getBasePath()));
  }

  @Override
  public ResponseEntity<TestExecutionResultDto> getTestResults(UUID testRunId) {

    Optional<ScenarioRunner.TestExecutionStatus> testResults =
        scenarioRunner.getTestResults(testRunId);
    return testResults
        .map(
            testExecutionStatus ->
                ResponseEntity.ok(
                    testExecutionResultMapper.testExecutionStatusToTestExecutionResultDto(
                        testExecutionStatus)))
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Test run " + testRunId + " not found"));
  }

  private String getBasePath() {
    return ServletUriComponentsBuilder.fromCurrentRequestUri().replacePath("").toUriString();
  }

  @ExceptionHandler(value = Exception.class)
  public ResponseEntity<ErrorDto> handleException(Exception e) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR.value())
        .body(
            new ErrorDto(
                String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()), "Internal Server Error"));
  }

  @ExceptionHandler(value = ResponseStatusException.class)
  public ResponseEntity<ErrorDto> handleException(ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode())
        .body(new ErrorDto(String.valueOf(e.getStatusCode().value()), e.getReason()));
  }

  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorDto> handleValidationExceptions(MethodArgumentNotValidException ex) {
    Map<String, String> errors = new HashMap<>();
    ex.getBindingResult()
        .getAllErrors()
        .forEach(
            error -> {
              String fieldName = ((FieldError) error).getField();
              String errorMessage = error.getDefaultMessage();
              errors.put(fieldName, errorMessage);
            });
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            new ErrorDto()
                .errorCode(String.valueOf(HttpStatus.BAD_REQUEST.value()))
                .errorMessage(errors.toString()));
  }

  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorDto> handleValidationExceptions(
      MethodArgumentTypeMismatchException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            new ErrorDto()
                .errorCode(String.valueOf(HttpStatus.BAD_REQUEST.value()))
                .errorMessage(ex.getMessage()));
  }
}
