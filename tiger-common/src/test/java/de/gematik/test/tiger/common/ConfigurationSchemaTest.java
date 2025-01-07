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
import java.util.Map;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

public class ConfigurationSchemaTest {

  private static final String schemaFile = "src/test/resources/schema/configurationSchema.json";

  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void testConfigurationSchema() throws Exception {
    final JsonSchema jsonSchema = loadSchema();
    final JsonNode example = loadExample("src/test/resources/schema/exampleConfiguration.yml");

    final Set<ValidationMessage> validationMessages = jsonSchema.validate(example);
    assertThat(validationMessages).isEmpty();
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
