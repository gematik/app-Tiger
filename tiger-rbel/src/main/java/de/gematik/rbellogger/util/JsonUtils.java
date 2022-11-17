/*
 * Copyright (c) 2022 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.rbellogger.util;

import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;

public class JsonUtils {

    public static List<Entry<String, String>> convertJsonObjectStringToMap(String jsonObjectString) {
        return JsonParser.parseString(jsonObjectString)
            .getAsJsonObject().entrySet()
            .stream()
            .map(entry -> {
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
