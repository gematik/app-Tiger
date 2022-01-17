package de.gematik.test.tiger.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.test.tiger.common.config.TigerConfigurationException;
import de.gematik.test.tiger.common.config.TigerConfigurationLoader.DuplicateMapKeysForbiddenConstructor;
import org.json.JSONObject;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.util.Map;

public class TigerSerializationUtil {

    private static final ObjectMapper objMapper = new ObjectMapper();

    public static JSONObject yamlToJsonObject(String yamlStr) {
        Yaml yaml = new Yaml(new DuplicateMapKeysForbiddenConstructor());
        Map<String, Object> map = yaml.load(yamlStr);
        return new JSONObject(map);
    }

    public static <T> T fromJson(String jsonFile, Class<T> targetClass) {
        try {
            return objMapper.readValue(jsonFile, targetClass);
        } catch (IOException e) {
            throw new TigerConfigurationException(
                "Failed to convert given JSON string to object of class " + targetClass.getName() + "!", e);
        }
    }

    public static String toJson(Object value) {
        try {
            return objMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new TigerConfigurationException("Failed to convert given object to JSON!", e);
        }
    }

    public static String toYaml(Object value) {
        Yaml yaml = new Yaml(new DuplicateMapKeysForbiddenConstructor());
        return yaml.dump(value);
    }
}
