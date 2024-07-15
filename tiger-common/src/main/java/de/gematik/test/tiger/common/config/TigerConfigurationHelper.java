/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.config;

import de.gematik.test.tiger.common.util.TigerSerializationUtil;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

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
    return TigerSerializationUtil.yamlToJsonObject(yamlStr);
  }
}
