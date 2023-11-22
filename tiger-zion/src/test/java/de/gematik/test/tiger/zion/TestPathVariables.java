/*
 * Copyright (c) 2023 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.zion;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.test.tiger.zion.config.TigerMockResponse;
import de.gematik.test.tiger.zion.config.TigerMockResponseDescription;
import de.gematik.test.tiger.zion.config.ZionConfiguration;
import de.gematik.test.tiger.zion.config.ZionRequestMatchDefinition;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import kong.unirest.HttpResponse;
import kong.unirest.HttpStatus;
import kong.unirest.Unirest;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TestPathVariables {

  final Path tempDirectory = Path.of("target", "zionResponses");

  @Autowired private ZionConfiguration configuration;
  @LocalServerPort private int port;
  private Map<String, TigerMockResponse> mockResponsesBackup;

  private static Stream<Arguments> provideNonNestedTestArguments() {
    return Stream.of(
        Arguments.of(
            "/foobar/{myVar}", HttpStatus.OK, "${myVar}", "/foobar/valueToAssign", "valueToAssign"),
        Arguments.of(
            "/foobar/{var1}/{var2}",
            HttpStatus.OK,
            "${var1} and ${var2}",
            "/foobar/valueFor1/valueFor2",
            "valueFor1 and valueFor2"),
        Arguments.of(
            "/this/path/has/no/variables",
            HttpStatus.OK,
            "no variables",
            "/this/path/has/no/variables",
            "no variables"),
        Arguments.of(
            "/foobar/{myVar}",
            HttpStatus.OK,
            "${myVar}",
            "/foobar/valueToAssign?someQuerParams=blabla",
            "valueToAssign"));
  }

  @SneakyThrows
  @BeforeEach
  public void setupTempDirectory() {
    Files.createDirectories(tempDirectory);
    Files.list(tempDirectory).forEach(path -> path.toFile().delete());
    mockResponsesBackup = configuration.getMockResponses();
  }

  @AfterEach
  public void resetMockResponses() {
    configuration.setMockResponses(mockResponsesBackup);
    configuration.setSpy(null);
  }

  @ParameterizedTest
  @MethodSource("provideNonNestedTestArguments")
  void testPathVariablesAssignments(
      String configuredPath,
      int configuredStatusCode,
      String configuredBody,
      String pathToTest,
      String expectedBody) {
    configuration.setMockResponses(
        Map.of(
            "testAssignmentPathVariable",
            TigerMockResponse.builder()
                .request(
                    ZionRequestMatchDefinition.builder()
                        .path(configuredPath)
                        .method("GET")
                        .additionalCriterions(List.of("'hello' != 'world'"))
                        .build())
                .response(
                    TigerMockResponseDescription.builder()
                        .statusCode(configuredStatusCode)
                        .body(configuredBody)
                        .build())
                .build()));

    HttpResponse<String> actualResponse =
        Unirest.get("http://localhost:" + port + pathToTest).asString();

    assertThat(actualResponse.getStatus()).isEqualTo(configuredStatusCode);
    assertThat(actualResponse.getBody()).isEqualTo(expectedBody);
  }

  @Test
  void testAssignmentOfSinglePathVariableInNestedResponse() {
    configuration.setMockResponses(
        Map.of(
            "testAssignmentPathVariable",
            TigerMockResponse.builder()
                .request(ZionRequestMatchDefinition.builder().path("/foobar").build())
                .nestedResponses(
                    Map.of(
                        "exampleNested",
                        TigerMockResponse.builder()
                            .request(ZionRequestMatchDefinition.builder().path("/{myVar}").build())
                            .response(
                                TigerMockResponseDescription.builder()
                                    .statusCode(201)
                                    .body("${myVar}")
                                    .build())
                            .build()))
                .build()));
    assertThat(
            Unirest.get("http://localhost:" + port + "/foobar/valueToAssign").asString().getBody())
        .isEqualTo("valueToAssign");
  }

  @Test
  void testPathMatchUseAssignedValueInAdditionalCriterion() {
    configuration.setMockResponses(
        Map.of(
            "testAssignmentPathVariable",
            TigerMockResponse.builder()
                .request(
                    ZionRequestMatchDefinition.builder()
                        .path("/foobar/{myVar}")
                        .method("GET")
                        .build())
                .nestedResponses(
                    Map.of(
                        "equalsPathVariable",
                        TigerMockResponse.builder()
                            .requestCriterions(List.of("'${myVar}' == 'valueToAssign'"))
                            .response(
                                TigerMockResponseDescription.builder()
                                    .statusCode(HttpStatus.OK)
                                    .body("${myVar}")
                                    .build())
                            .importance(10)
                            .build(),
                        "doesNotEqualPathVariable",
                        TigerMockResponse.builder()
                            .response(
                                TigerMockResponseDescription.builder()
                                    .statusCode(HttpStatus.NOT_FOUND)
                                    .build())
                            .importance(0)
                            .build()))
                .build()));
    assertThat(
            Unirest.get("http://localhost:" + port + "/foobar/valueToAssign").asString().getBody())
        .isEqualTo("valueToAssign");

    assertThat(
            Unirest.get("http://localhost:" + port + "/foobar/somethingElse").asJson().getStatus())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }
}
