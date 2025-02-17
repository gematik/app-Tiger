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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgrApplication;
import de.gematik.test.tiger.testenvmgr.api.model.ErrorDto;
import de.gematik.test.tiger.testenvmgr.api.model.ExecutionResultDto;
import de.gematik.test.tiger.testenvmgr.api.model.TestDescriptionDto;
import de.gematik.test.tiger.testenvmgr.api.model.TestExecutionInformationDto;
import de.gematik.test.tiger.testenvmgr.api.model.TestExecutionRequestDto;
import de.gematik.test.tiger.testenvmgr.api.model.TestExecutionResultDto;
import de.gematik.test.tiger.testenvmgr.api.model.mapper.TestDescriptionMapper;
import de.gematik.test.tiger.testenvmgr.api.model.mapper.TestExecutionStatusFactory;
import de.gematik.test.tiger.testenvmgr.api.model.mapper.TigerTestIdentifier;
import de.gematik.test.tiger.testenvmgr.env.ScenarioRunner;
import java.io.File;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import kong.unirest.ContentType;
import kong.unirest.GenericType;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import kong.unirest.UnirestInstance;
import kong.unirest.jackson.JacksonObjectMapper;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.support.descriptor.FileSource;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(
    classes = TigerTestEnvMgrApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "tiger.lib.enableTestManagementRestApi=true")
class TestsApiControllerTest {
  @LocalServerPort private int port;
  @Autowired private TestDescriptionMapper testDescriptionMapper;
  private UnirestInstance unirestInstance;
  private static final String BASE_URL = "http://localhost:";
  @Autowired ObjectMapper objectMapper;

  @BeforeEach
  void setup() {
    unirestInstance = Unirest.spawnInstance();
    unirestInstance.config().setObjectMapper(new JacksonObjectMapper(objectMapper));
  }

  @AfterEach
  void clearScenarios() {
    unirestInstance.close();
    ScenarioRunner.clearScenarios();
  }

  @Test
  void testGetDiscoveredTests_isEmpty() {
    val responseBody = unirestInstance.get(BASE_URL + port + "/tests").asString().getBody();
    assertEquals("[]", responseBody);
  }

  @Test
  void testGetDiscoveredTest_containsDiscoveredTests() {
    val testDescriptors = TestExecutionStatusFactory.createTestDescriptorsFlat();
    val testIdentifiers = testDescriptors.stream().map(TestIdentifier::from).toList();
    val tigerTestIdentifier =
        testIdentifiers.stream()
            .map(
                testIdentifier ->
                    TigerTestIdentifier.builder()
                        .testIdentifier(testIdentifier)
                        .displayName(testIdentifier.getDisplayName())
                        .build())
            .collect(Collectors.toList());
    ScenarioRunner.addTigerScenarios(tigerTestIdentifier);
    List<TestDescriptionDto> responseBody =
        unirestInstance
            .get(BASE_URL + port + "/tests")
            .asObject(new GenericType<List<TestDescriptionDto>>() {})
            .getBody();

    assertThat(responseBody)
        .containsExactly(
            createTestDescriptionDto("dummyTest1"),
            createTestDescriptionDto("dummyTest2"),
            createTestDescriptionDto("dummyTest3"),
            createTestDescriptionDto("dummyTest4"));
  }

  TestDescriptionDto createTestDescriptionDto(String name) {
    return new TestDescriptionDto()
        .uniqueId("[engine:cucumber]/[test:" + name + "]")
        .sourceFile(FileSource.from(new File("/a/file/source")).getUri().toString())
        .displayName(name + ": dummy display name")
        .tags(Set.of("ThisAnotherTag", "ThisATag"));
  }

  @Test
  void testGetTestResults_nonExistingId() {
    val aRandomUUID = UUID.randomUUID().toString();
    HttpResponse<ErrorDto> response =
        unirestInstance
            .get(BASE_URL + port + "/tests/runs/" + aRandomUUID)
            .asObject(ErrorDto.class);
    assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    assertThat(response.getBody())
        .isEqualTo(
            new ErrorDto().errorCode("404").errorMessage("Test run " + aRandomUUID + " not found"));
  }

  @Test
  void testGetTestResult_notAUuid() {
    val somethingNotUuid = "something_not_UUId";
    HttpResponse<ErrorDto> response =
        unirestInstance
            .get(BASE_URL + port + "/tests/runs/" + somethingNotUuid)
            .asObject(ErrorDto.class);
    assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    var result = response.getBody();

    assertThat(result.getErrorCode()).isEqualTo(String.valueOf(HttpStatus.BAD_REQUEST.value()));
    assertThat(result.getErrorMessage())
        .isEqualTo(
            "Method parameter 'testRunId': Failed to convert value of type 'java.lang.String' to required type 'java.util.UUID'; Invalid UUID string: "
                + somethingNotUuid);
  }

  @Test
  void testPostExecutionAllTests() {

    TestPlan testPlan = loadRealTestDescriptors("unitTest_A.feature");
    ScenarioRunner.addTigerScenarios(testPlan);

    assertThat(testPlan.containsTests()).isTrue();

    HttpResponse<TestExecutionInformationDto> response =
        unirestInstance
            .post(BASE_URL + port + "/tests/runs/all")
            .asObject(TestExecutionInformationDto.class);

    assertThat(response.getStatus()).isEqualTo(HttpStatus.ACCEPTED.value());

    UUID uuid = response.getBody().getTestRunId();
    TigerTestIdentifier testIdentifier = ScenarioRunner.getTigerScenarios().iterator().next();

    assertThat(response.getBody())
        .isEqualTo(
            new TestExecutionInformationDto()
                .testRunId(uuid)
                .resultUrl(URI.create("http://localhost:" + port + "/tests/runs/" + uuid))
                .testsToExecute(
                    List.of(
                        testDescriptionMapper.tigerTestIdentifierToTestDescription(
                            testIdentifier))));
  }

  @Test
  void testPostExecutionEmptySelectors_selectsAll() {
    TestPlan testPlan = loadTestDescriptorsAllFeatures();
    ScenarioRunner.addTigerScenarios(testPlan);

    assertThat(testPlan.containsTests()).isTrue();

    HttpResponse<TestExecutionInformationDto> response =
        unirestInstance
            .post(BASE_URL + port + "/tests/runs")
            .body("{}")
            .header("Content-Type", ContentType.APPLICATION_JSON.toString())
            .asObject(TestExecutionInformationDto.class);

    assertThat(response.getStatus()).isEqualTo(HttpStatus.ACCEPTED.value());
    assertThat(response.getBody().getTestsToExecute()).hasSize(3);
  }

  @SneakyThrows
  @Test
  void testGetTestResults() {

    TestPlan testPlan = loadRealTestDescriptors("unitTest_A.feature");
    ScenarioRunner.addTigerScenarios(testPlan);

    assertThat(testPlan.containsTests()).isTrue();

    HttpResponse<TestExecutionInformationDto> postExecuteAllResponse =
        unirestInstance
            .post(BASE_URL + port + "/tests/runs/all")
            .asObject(TestExecutionInformationDto.class);

    assertThat(postExecuteAllResponse.getStatus()).isEqualTo(HttpStatus.ACCEPTED.value());
    UUID uuid = postExecuteAllResponse.getBody().getTestRunId();

    await()
        .atMost(1, TimeUnit.MINUTES)
        .pollInterval(500, TimeUnit.MILLISECONDS)
        .logging()
        .untilAsserted(
            () -> {
              HttpResponse<TestExecutionResultDto> response =
                  unirestInstance
                      .get(BASE_URL + port + "/tests/runs/" + uuid)
                      .asObject(TestExecutionResultDto.class);

              assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
              var body = response.getBody();
              assertThat(body.getTestRunFinished()).isBeforeOrEqualTo(OffsetDateTime.now());
              assertThat(body.getResult().getResult())
                  .isEqualTo(ExecutionResultDto.ResultEnum.FAILED);
              assertThat(body.getResult().getFailureMessage())
                  .startsWith("The step 'TGR show banner \"HELLO\"' is undefined.");
              assertThat(body.getTests()).hasSize(1);
            });
  }

  @Test
  void testPostExecutionByTag() {
    TestPlan testPlan = loadTestDescriptorsAllFeatures();
    ScenarioRunner.addTigerScenarios(testPlan);

    assertThat(testPlan.containsTests()).isTrue();

    HttpResponse<TestExecutionInformationDto> postExecuteTagResponse =
        unirestInstance
            .post(BASE_URL + port + "/tests/runs")
            .header("Content-Type", ContentType.APPLICATION_JSON.toString())
            .body(new TestExecutionRequestDto().tags(List.of("Tag_To_Filter")))
            .asObject(TestExecutionInformationDto.class);

    assertThat(postExecuteTagResponse.getStatus()).isEqualTo(HttpStatus.ACCEPTED.value());

    List<TestDescriptionDto> testsToRun = postExecuteTagResponse.getBody().getTestsToExecute();

    assertThat(testsToRun).hasSize(2);
    assertThat(testsToRun)
        .extracting(TestDescriptionDto::getTags)
        .allMatch(t -> t.contains("Tag_To_Filter"));
    assertThat(testsToRun)
        .extracting(TestDescriptionDto::getSourceFile)
        .containsExactlyInAnyOrder(
            "classpath:features/unitTest_A.feature", "classpath:features/unitTest_B.feature");
  }

  @Test
  void testPostExecutionByFile() {
    TestPlan testPlan = loadTestDescriptorsAllFeatures();
    ScenarioRunner.addTigerScenarios(testPlan);

    assertThat(testPlan.containsTests()).isTrue();

    HttpResponse<TestExecutionInformationDto> postExecuteFileResponse =
        unirestInstance
            .post(BASE_URL + port + "/tests/runs")
            .header("Content-Type", ContentType.APPLICATION_JSON.toString())
            .body(
                new TestExecutionRequestDto()
                    .sourceFiles(List.of("classpath:features/unitTest_B.feature")))
            .asObject(TestExecutionInformationDto.class);

    assertThat(postExecuteFileResponse.getStatus()).isEqualTo(HttpStatus.ACCEPTED.value());
    List<TestDescriptionDto> testsToRun = postExecuteFileResponse.getBody().getTestsToExecute();

    assertThat(testsToRun).hasSize(2);
    assertThat(testsToRun)
        .extracting(TestDescriptionDto::getTags)
        .containsExactlyInAnyOrder(Set.of("Tag_To_Filter"), Set.of("Another_Tag_To_Filter"));
    assertThat(testsToRun)
        .extracting(TestDescriptionDto::getSourceFile)
        .allMatch("classpath:features/unitTest_B.feature"::equals);
  }

  @Test
  void testPostExecutionByUniqueID() {
    TestPlan testPlan = loadTestDescriptorsAllFeatures();
    ScenarioRunner.addTigerScenarios(testPlan);

    var uniqueIdToExecute =
        "[engine:cucumber]/[feature:classpath%3Afeatures%2FunitTest_B.feature]/[scenario:4]";
    assertThat(testPlan.containsTests()).isTrue();

    HttpResponse<TestExecutionInformationDto> postExecuteUniqueIdResponse =
        unirestInstance
            .post(BASE_URL + port + "/tests/runs")
            .header("Content-Type", ContentType.APPLICATION_JSON.toString())
            .body(new TestExecutionRequestDto().testUniqueIds(List.of(uniqueIdToExecute)))
            .asObject(TestExecutionInformationDto.class);

    assertThat(postExecuteUniqueIdResponse.getStatus()).isEqualTo(HttpStatus.ACCEPTED.value());
    List<TestDescriptionDto> testsToRun = postExecuteUniqueIdResponse.getBody().getTestsToExecute();

    assertThat(testsToRun).hasSize(1);
    assertThat(testsToRun)
        .extracting(TestDescriptionDto::getUniqueId)
        .containsExactly(uniqueIdToExecute);
  }

  @Test
  void testPostExecutionFileAndTag() {
    TestPlan testPlan = loadTestDescriptorsAllFeatures();
    ScenarioRunner.addTigerScenarios(testPlan);

    assertThat(testPlan.containsTests()).isTrue();

    HttpResponse<TestExecutionInformationDto> postExecuteTagResponse =
        unirestInstance
            .post(BASE_URL + port + "/tests/runs")
            .header("Content-Type", ContentType.APPLICATION_JSON.toString())
            .body(
                new TestExecutionRequestDto()
                    .sourceFiles(List.of("classpath:features/unitTest_B.feature"))
                    .tags(List.of("Another_Tag_To_Filter")))
            .asObject(TestExecutionInformationDto.class);

    List<TestDescriptionDto> testsToRun = postExecuteTagResponse.getBody().getTestsToExecute();

    assertThat(testsToRun).hasSize(1);
    assertThat(testsToRun)
        .extracting(TestDescriptionDto::getTags)
        .containsExactly(Set.of("Another_Tag_To_Filter"));
    assertThat(testsToRun)
        .extracting(TestDescriptionDto::getSourceFile)
        .containsExactly("classpath:features/unitTest_B.feature");
  }

  @Test
  void testGetResults_InvalidId() {
    val uuidNotFromTestRun = UUID.randomUUID().toString();
    HttpResponse<ErrorDto> response =
        unirestInstance
            .get(BASE_URL + port + "/tests/runs/" + uuidNotFromTestRun)
            .asObject(ErrorDto.class);

    assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    assertThat(response.getBody())
        .isEqualTo(
            new ErrorDto()
                .errorCode("404")
                .errorMessage("Test run " + uuidNotFromTestRun + " not found"));
  }

  // To have the ScenarioRunner executing actual tests we need a real test
  // plan instead of dummytestdescriptors
  public TestPlan loadRealTestDescriptors(String... fileName) {
    Launcher launcher = LauncherFactory.create();
    LauncherDiscoveryRequest testRunRequest =
        request()
            .configurationParameters(
                Map.of(
                    "cucumber.features",
                    Arrays.stream(fileName)
                        .map(f -> "classpath:/features/" + f)
                        .collect(Collectors.joining(","))))
            .build();
    return launcher.discover(testRunRequest);
  }

  public TestPlan loadTestDescriptorsAllFeatures() {
    return loadRealTestDescriptors("unitTest_A.feature", "unitTest_B.feature");
  }
}
