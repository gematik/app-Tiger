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

package de.gematik.rbellogger.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.Map.Entry;
import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.Pair;

public class JsonUtils {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private JsonUtils() {}

  @SneakyThrows
  public static synchronized List<Entry<String, String>> convertJsonObjectStringToMap(
      String jsonObjectString) {
    List<Entry<String, String>> result = new ArrayList<>();

    for (Iterator<Entry<String, JsonNode>> it = OBJECT_MAPPER.readTree(jsonObjectString).fields();
        it.hasNext(); ) {
      var entry = it.next();
      if (entry.getValue().isValueNode() && entry.getValue().isTextual()) {
        result.add(Pair.of(entry.getKey(), entry.getValue().textValue()));
      } else {
        result.add(Pair.of(entry.getKey(), entry.getValue().toString()));
      }
    }

    return result;
  }
}
