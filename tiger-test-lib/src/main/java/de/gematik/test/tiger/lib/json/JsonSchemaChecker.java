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

package de.gematik.test.tiger.lib.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import de.gematik.test.tiger.exceptions.GenericTigerException;
import java.util.Set;
import java.util.stream.Collectors;

/** Validates a given json string against a given json schema */
public class JsonSchemaChecker {

  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Checks that the json conforms to the given schema
   *
   * @param jsonToCheck json to check
   * @param schema schema to check against
   */
  public void compareJsonToSchema(String jsonToCheck, String schema) {
    try {
      JsonNode jsonNode = objectMapper.readTree(jsonToCheck);
      JsonNode schemaNode = objectMapper.readTree(schema);

      JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
      JsonSchema jsonSchema = factory.getSchema(schemaNode);

      Set<ValidationMessage> errors = jsonSchema.validate(jsonNode);

      if (!errors.isEmpty()) {
        var errorMessages =
            errors.stream().map(ValidationMessage::toString).collect(Collectors.joining("\n  "));
        throw new JsonSchemaAssertionError("JSON schema validation failed:\n  " + errorMessages);
      }
    } catch (JsonProcessingException e) {
      throw new JsonSchemaProcessingError("Failed to process input json and/or input schema", e);
    }
  }

  public static class JsonSchemaAssertionError extends AssertionError {

    public JsonSchemaAssertionError(String message) {
      super(message);
    }
  }

  public static class JsonSchemaProcessingError extends GenericTigerException {

    public JsonSchemaProcessingError(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
