/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.json;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Collection;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class JsonSchemaCheckerTest {

  public static Collection<Arguments> validInputs() {
    return List.of(
        Arguments.of(
            """
{
  "username": "example",
  "kimVersion": "1.5+",
  "regStat": "registered",
  "maxMailSize": 734003200,
  "dataTimeToLive": 10
}
""",
            """
{
  "type": "object",
  "additionalProperties": false,
  "properties": {
    "username": {
      "type": "string",
      "const": "example"
    },
    "kimVersion": {
      "type": "string",
      "const": "1.5+"
    },
    "regStat": {
      "type": "string",
      "const": "registered"
    },
    "maxMailSize": {
      "type": "integer",
      "const": 734003200
    }
    ,"dataTimeToLive": {
      "type": "integer",
      "minimum": 10,
      "maximum": 15
    }
  }
}
"""));
  }

  public static Collection<Arguments> invalidInputs() {
    return List.of(
        Arguments.of(
            """
            {
              "username": "exampleASD",
              "kimVersion": "1.52",
              "regStat": "registeredA",
              "maxMailSize": 734003201,
              "dataTimeToLive": 1
            }
            """,
            """
            {
              "type": "object",
              "additionalProperties": false,
              "properties": {
                "username": {
                  "type": "string",
                  "const": "example"
                },
                "kimVersion": {
                  "type": "string",
                  "const": "1.5+"
                },
                "regStat": {
                  "type": "string",
                  "const": "registered"
                },
                "maxMailSize": {
                  "type": "integer",
                  "const": 734003200
                }
                ,"dataTimeToLive": {
                  "type": "integer",
                  "minimum": 10,
                  "maximum": 15
                }
              }
            }
            """));
  }

  @ParameterizedTest
  @MethodSource("validInputs")
  void testValidInputs_shouldNotThrowException(String jsonToCheck, String schemaToCheck) {
    JsonSchemaChecker jsonSchemaChecker = new JsonSchemaChecker();
    assertDoesNotThrow(() -> jsonSchemaChecker.compareJsonToSchema(jsonToCheck, schemaToCheck));
  }

  @ParameterizedTest
  @MethodSource("invalidInputs")
  void testInvalidInputs_shouldThrowException(String jsonToCheck, String schemaToCheck) {
    JsonSchemaChecker jsonSchemaChecker = new JsonSchemaChecker();
    assertThatExceptionOfType(JsonSchemaChecker.JsonSchemaAssertionError.class)
        .isThrownBy(
            () -> {
              jsonSchemaChecker.compareJsonToSchema(jsonToCheck, schemaToCheck);
            });
  }
}
