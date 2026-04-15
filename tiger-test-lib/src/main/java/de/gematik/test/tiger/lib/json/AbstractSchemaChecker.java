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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.test.tiger.exceptions.GenericTigerException;
import java.util.stream.Collectors;

/** Base class for schema-based content checkers (JSON and YAML). */
public abstract class AbstractSchemaChecker extends AbstractRbelJsonChecker {

  protected abstract ObjectMapper mapper();

  @Override
  public void verify(String oracle, RbelElement element, String diffOptionCSV) {
    compareToSchema(getAsJsonString(element), oracle);
  }

  public void compareToSchema(String contentToCheck, String schema) {
    try {
      JsonNode contentNode = mapper().readTree(contentToCheck);
      JsonNode schemaNode = mapper().readTree(schema);

      SchemaRegistry factory =
          SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
      Schema jsonSchema = factory.getSchema(schemaNode);

      var errors = jsonSchema.validate(contentNode);

      if (!errors.isEmpty()) {
        var errorMessages =
            errors.stream().map(Error::toString).collect(Collectors.joining("\n  "));
        throw new SchemaAssertionError("Schema validation failed:\n  " + errorMessages);
      }
    } catch (JsonProcessingException e) {
      throw new SchemaProcessingError("Failed to process content and/or schema", e);
    }
  }

  public static class SchemaAssertionError extends AssertionError {
    public SchemaAssertionError(String message) {
      super(message);
    }
  }

  public static class SchemaProcessingError extends GenericTigerException {
    public SchemaProcessingError(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
