/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.config;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.yaml.snakeyaml.Yaml;

@Slf4j
@SuppressWarnings("unused")
public class TigerConfigurationHelper<T> {

  private TigerConfigurationHelper() {}

  /**
   * converts a given yaml content string to JSON object.
   *
   * @param yamlStr YAML content
   * @return JSON object representing the yaml content
   */
  public static JSONObject yamlStringToJson(String yamlStr) {
    Yaml yaml = new Yaml(new DuplicateMapKeysForbiddenConstructor());
    Map<String, Object> map = yaml.load(yamlStr);
    return new JSONObject(map);
  }
}
