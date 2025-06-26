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
package de.gematik.test.tiger.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class ConfigurationSchemaTest {

  private static final String schemaFile = "target/schemas/Configuration.json";

  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void testValidConfigurations() throws Exception {
    final JsonSchema jsonSchema = loadSchema();
    final List<String> exampleConfigurations =
        List.of(
            "src/test/resources/schema/exampleConfiguration1.yml",
            "src/test/resources/schema/exampleConfiguration2.yml",
            "src/test/resources/schema/exampleConfiguration3.yml");
    for (final String exampleConfiguration : exampleConfigurations) {
      final JsonNode example = loadExample(exampleConfiguration);

      final Set<ValidationMessage> validationMessages = jsonSchema.validate(example);
      assertThat(validationMessages).isEmpty();
    }
  }

  @Test
  void testValidTestenvMgrConfigs() throws Exception {
    final JsonSchema jsonSchema = loadSchema();
    final Path testenvMgrPath = Paths.get("../tiger-testenv-mgr/src/test/resources");
    final Set<Path> configsInTestenvMgr =
        Files.walk(testenvMgrPath)
            .filter(path -> path.getFileName().toString().endsWith(".yaml"))
            .filter(path -> !path.getFileName().toString().contains("Invalid"))
            .collect(Collectors.toSet());

    for (Path testenvMgrConfigPath : configsInTestenvMgr) {
      final String filename = testenvMgrConfigPath.toAbsolutePath().toString();
      final JsonNode example = loadExample(filename);

      final Set<ValidationMessage> validationMessages =
          jsonSchema.validate(example).stream()
              .filter(message -> !message.getError().contains("string"))
              .collect(Collectors.toSet());

      assertThat(validationMessages).isEmpty();
    }
  }

  private JsonSchema loadSchema() throws Exception {
    final ObjectMapper mapper = new ObjectMapper();
    final JsonNode schema =
        mapper.readValue(
            FileUtils.readFileToString(new File(schemaFile), StandardCharsets.UTF_8),
            JsonNode.class);

    final JsonSchemaFactory jsonSchemaFactory =
        JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    return jsonSchemaFactory.getSchema(schema);
  }

  private JsonNode loadExample(final String filename) throws Exception {
    final Map<String, Object> map =
        new Yaml().load(FileUtils.readFileToString(new File(filename), StandardCharsets.UTF_8));

    return objectMapper.valueToTree(map);
  }
}
