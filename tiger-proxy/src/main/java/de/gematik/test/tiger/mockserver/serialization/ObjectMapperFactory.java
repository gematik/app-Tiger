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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.*;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;

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
    return JsonMapper.builder()

        // ignore failures
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
        .configure(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS, false)
        .configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, false)
        .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
        .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
        .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true)
        .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_VALUES, true)
        .configure(MapperFeature.ALLOW_COERCION_OF_SCALARS, true)
        .configure(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS, true)
        .configure(MapperFeature.REQUIRE_SETTERS_FOR_GETTERS, false)

        // relax parsing
        .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
        .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
        .configure(EnumFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)
        .configure(DeserializationFeature.USE_JAVA_ARRAY_FOR_JSON_ARRAY, true)
        .configure(JsonReadFeature.ALLOW_JAVA_COMMENTS, true)
        .configure(JsonReadFeature.ALLOW_YAML_COMMENTS, true)
        .configure(JsonReadFeature.ALLOW_UNQUOTED_PROPERTY_NAMES, true)
        .configure(JsonReadFeature.ALLOW_SINGLE_QUOTES, true)
        .configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS, true)
        .configure(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true)
        .configure(JsonReadFeature.ALLOW_LEADING_ZEROS_FOR_NUMBERS, true)
        .configure(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS, true)
        .configure(JsonReadFeature.ALLOW_MISSING_VALUES, true)
        .configure(JsonReadFeature.ALLOW_TRAILING_COMMA, true)

        // consistent json output
        .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
        .build();
  }

  public static ObjectMapper buildObjectMapperWithOnlyConfigurationDefaults() {
    return buildObjectMapperWithoutRemovingEmptyValues()
        .rebuild()
        .changeDefaultPropertyInclusion(
            incl ->
                incl.withContentInclusion(JsonInclude.Include.NON_DEFAULT)
                    .withValueInclusion(JsonInclude.Include.NON_DEFAULT))
        .build();
  }
}
