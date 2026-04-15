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
package de.gematik.test.tiger.lib.json;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class YamlSchemaCheckerTest {

  static final String YAML_SCHEMA =
      """
      type: object
      additionalProperties: false
      properties:
        username:
          type: string
          const: example
        kimVersion:
          type: string
          const: "1.5+"
        regStat:
          type: string
          const: registered
        maxMailSize:
          type: integer
          const: 734003200
        dataTimeToLive:
          type: integer
          minimum: 10
          maximum: 15
      """;

  static final String VALID_YAML_CONTENT =
      """
      username: example
      kimVersion: "1.5+"
      regStat: registered
      maxMailSize: 734003200
      dataTimeToLive: 10
      """;

  static final String INVALID_YAML_CONTENT =
      """
      username: exampleASD
      kimVersion: "1.52"
      regStat: registeredA
      maxMailSize: 734003201
      dataTimeToLive: 1
      """;

  static final String VALID_JSON_CONTENT =
      """
      {
        "username": "example",
        "kimVersion": "1.5+",
        "regStat": "registered",
        "maxMailSize": 734003200,
        "dataTimeToLive": 10
      }
      """;

  static final String INVALID_JSON_CONTENT =
      """
      {
        "username": "exampleASD",
        "kimVersion": "1.52",
        "regStat": "registeredA",
        "maxMailSize": 734003201,
        "dataTimeToLive": 1
      }
      """;

  public static Collection<Arguments> validInputs() {
    return List.of(
        Arguments.of("YAML content vs YAML schema", VALID_YAML_CONTENT, YAML_SCHEMA),
        Arguments.of("JSON content vs YAML schema", VALID_JSON_CONTENT, YAML_SCHEMA));
  }

  public static Collection<Arguments> invalidInputs() {
    return List.of(
        Arguments.of("YAML content vs YAML schema", INVALID_YAML_CONTENT, YAML_SCHEMA),
        Arguments.of("JSON content vs YAML schema", INVALID_JSON_CONTENT, YAML_SCHEMA));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("validInputs")
  void testValidInputs_shouldNotThrowException(String label, String content, String schema) {
    assertDoesNotThrow(() -> new YamlSchemaChecker().compareToSchema(content, schema));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidInputs")
  void testInvalidInputs_shouldThrowException(String label, String content, String schema) {
    assertThatExceptionOfType(AbstractSchemaChecker.SchemaAssertionError.class)
        .isThrownBy(() -> new YamlSchemaChecker().compareToSchema(content, schema));
  }

  @Test
  void testMultipleViolations_shouldAllBeReportedInException() {
    assertThatExceptionOfType(AbstractSchemaChecker.SchemaAssertionError.class)
        .isThrownBy(
            () -> new YamlSchemaChecker().compareToSchema(INVALID_YAML_CONTENT, YAML_SCHEMA))
        .withMessageContaining("username")
        .withMessageContaining("kimVersion")
        .withMessageContaining("regStat")
        .withMessageContaining("maxMailSize")
        .withMessageContaining("dataTimeToLive");
  }
}
