/*
 *
 * Copyright 2021-2025 gematik GmbH
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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.test.tiger.proxy.client;

import de.gematik.rbellogger.data.RbelMessageMetadata;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JsonParser;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Custom Jackson deserializer for additionalInformation that converts timestamp strings back to
 * ZonedDateTime objects where appropriate.
 */
@Slf4j
public class TimestampAwareMapDeserializer extends ValueDeserializer<Map<String, Object>> {

  private static final TypeReference<Map<String, Object>> MAP_TYPE_REF = new TypeReference<>() {};

  @Override
  public Map<String, Object> deserialize(JsonParser p, DeserializationContext ctxt) {
    Map<String, Object> rawMap = ctxt.readValue(p, MAP_TYPE_REF);
    Map<String, Object> processedMap = new HashMap<>(rawMap);

    convertTimestampField(processedMap, RbelMessageMetadata.PREVIOUS_MESSAGE_TIMESTAMP.getKey());
    convertTimestampField(processedMap, RbelMessageMetadata.MESSAGE_TRANSMISSION_TIME.getKey());

    return processedMap;
  }

  private void convertTimestampField(Map<String, Object> map, String fieldName) {
    Object value = map.get(fieldName);
    if (value instanceof String timestampString) {
      try {
        ZonedDateTime zonedDateTime = ZonedDateTime.parse(timestampString);
        map.put(fieldName, zonedDateTime);
      } catch (Exception e) {
        log.atDebug()
            .addArgument(fieldName)
            .addArgument(value)
            .addArgument(e::getMessage)
            .log("Failed to parse timestamp field '{}' with value '{}': {}");
      }
    }
  }
}
