/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.util;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.test.tiger.common.config.DuplicateMapKeysForbiddenConstructor;
import de.gematik.test.tiger.common.config.TigerConfigurationException;
import de.gematik.test.tiger.common.config.TigerConfigurationKey;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONObject;
import org.yaml.snakeyaml.Yaml;

public class TigerSerializationUtil {

  private static final ObjectMapper objMapper =
      new ObjectMapper().setSerializationInclusion(Include.NON_NULL);

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
          "Failed to convert given JSON string to object of class " + targetClass.getName() + "!",
          e);
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

  public static Map<String, String> toMap(Object value, String... baseKeys) {
    return recursiveMapDumping(new JSONObject(toJson(value)), new TigerConfigurationKey(baseKeys))
        .entrySet()
        .stream()
        .collect(Collectors.toMap(e -> e.getKey().downsampleKey(), e -> e.getValue()));
  }

  private static Map<TigerConfigurationKey, String> recursiveMapDumping(
      JSONObject jsonObject, TigerConfigurationKey baseKey) {
    Map<TigerConfigurationKey, String> result = new HashMap<>();
    for (String key : jsonObject.keySet()) {
      final Object value = jsonObject.get(key);
      if (value instanceof JSONObject) {
        result.putAll(
            recursiveMapDumping((JSONObject) value, new TigerConfigurationKey(baseKey, key)));
      } else if (value instanceof JSONArray) {
        result.putAll(
            recursiveMapDumping((JSONArray) value, new TigerConfigurationKey(baseKey, key)));
      } else {
        result.put(new TigerConfigurationKey(baseKey, key), value.toString());
      }
    }
    return result;
  }

  private static Map<TigerConfigurationKey, String> recursiveMapDumping(
      JSONArray value, TigerConfigurationKey baseKey) {
    Map<TigerConfigurationKey, String> result = new HashMap<>();
    int index = 0;
    for (Object entry : value) {
      String key = Integer.toString(index);
      if (entry instanceof JSONObject) {
        result.putAll(
            recursiveMapDumping((JSONObject) entry, new TigerConfigurationKey(baseKey, key)));
      } else if (entry instanceof JSONArray) {
        result.putAll(
            recursiveMapDumping((JSONArray) entry, new TigerConfigurationKey(baseKey, key)));
      } else {
        if (entry != null) {
          result.put(new TigerConfigurationKey(baseKey, key), entry.toString());
        }
      }
      index++;
    }
    return result;
  }
}
