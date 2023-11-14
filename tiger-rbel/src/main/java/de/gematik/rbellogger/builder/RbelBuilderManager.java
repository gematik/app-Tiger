package de.gematik.rbellogger.builder;

import java.util.HashMap;
import java.util.Map;

public class RbelBuilderManager {

  private Map<String, RbelBuilder> rbelBuilders = new HashMap<>();

  /**
   * serializes the treeRootNode of a RbelBuilder by key
   *
   * @param key key of RbelBuilder
   * @return serialization result
   */
  public String serialize(String key) {
    return rbelBuilders.get(key).serialize();
  }

  public RbelBuilder get(String key) {
    return rbelBuilders.get(key);
  }

  public void put(String key, RbelBuilder builder) {
    rbelBuilders.put(key, builder);
  }
}
