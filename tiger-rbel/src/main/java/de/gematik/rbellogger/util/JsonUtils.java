/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.util;

import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;

public class JsonUtils {

  public static List<Entry<String, String>> convertJsonObjectStringToMap(String jsonObjectString) {
    return JsonParser.parseString(jsonObjectString).getAsJsonObject().entrySet().stream()
        .map(
            entry -> {
              if (entry.getValue().isJsonPrimitive()
                  && entry.getValue().getAsJsonPrimitive().isString()) {
                return Pair.of(entry.getKey(), entry.getValue().getAsString());
              } else {
                return Pair.of(entry.getKey(), entry.getValue().toString());
              }
            })
        .collect(Collectors.toList());
  }
}
