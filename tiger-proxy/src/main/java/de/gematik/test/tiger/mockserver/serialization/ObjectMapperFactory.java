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
package de.gematik.test.tiger.mockserver.serialization;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

/*
 * @author jamesdbloom
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ObjectMapperFactory {

  private static final ObjectWriter prettyPrintWriter =
      buildObjectMapperWithOnlyConfigurationDefaults().writerWithDefaultPrettyPrinter();

  public static ObjectWriter createObjectMapper() {
    return prettyPrintWriter;
  }

  @SuppressWarnings("deprecation")
  public static ObjectMapper buildObjectMapperWithoutRemovingEmptyValues() {
    ObjectMapper objectMapper = new ObjectMapper();

    // ignore failures
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    objectMapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
    objectMapper.configure(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS, false);
    objectMapper.configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, false);
    objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    objectMapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
    objectMapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true);
    objectMapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_VALUES, true);
    objectMapper.configure(MapperFeature.ALLOW_COERCION_OF_SCALARS, true);
    objectMapper.configure(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS, true);
    objectMapper.configure(MapperFeature.REQUIRE_SETTERS_FOR_GETTERS, false);
    objectMapper.configure(MapperFeature.AUTO_DETECT_GETTERS, true);

    // relax parsing
    objectMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
    objectMapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
    objectMapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);
    objectMapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
    objectMapper.configure(JsonParser.Feature.ALLOW_YAML_COMMENTS, true);
    objectMapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
    objectMapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
    objectMapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
    objectMapper.configure(JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true);
    objectMapper.configure(JsonParser.Feature.ALLOW_NUMERIC_LEADING_ZEROS, true);
    objectMapper.configure(JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS, true);
    objectMapper.configure(JsonParser.Feature.ALLOW_MISSING_VALUES, true);
    objectMapper.configure(JsonParser.Feature.ALLOW_TRAILING_COMMA, true);
    objectMapper.configure(JsonParser.Feature.IGNORE_UNDEFINED, true);

    // use arrays
    objectMapper.configure(DeserializationFeature.USE_JAVA_ARRAY_FOR_JSON_ARRAY, true);

    // consistent json output
    objectMapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);

    return objectMapper;
  }

  public static ObjectMapper buildObjectMapperWithOnlyConfigurationDefaults() {
    ObjectMapper objectMapper = buildObjectMapperWithoutRemovingEmptyValues();

    // remove empty values from JSON
    objectMapper.setSerializationInclusion(JsonInclude.Include.NON_DEFAULT);
    objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

    // add support for java date time serialisation and de-serialisation
    objectMapper.registerModule(new JavaTimeModule());

    return objectMapper;
  }
}
