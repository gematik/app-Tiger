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
 */

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
