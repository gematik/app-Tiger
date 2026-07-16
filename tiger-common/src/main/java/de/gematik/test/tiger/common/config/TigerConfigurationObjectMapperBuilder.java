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
package de.gematik.test.tiger.common.config;

import static de.gematik.test.tiger.common.config.TigerConfigurationLoader.TIGER_CONFIGURATION_ATTRIBUTE_KEY;

import de.gematik.test.tiger.common.TokenSubstituteHelper;
import de.gematik.test.tiger.zion.config.TigerSkipEvaluation;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JsonParser;
import tools.jackson.core.Version;
import tools.jackson.databind.*;
import tools.jackson.databind.cfg.ContextAttributes;
import tools.jackson.databind.deser.DeserializationProblemHandler;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.node.StringNode;

class TigerConfigurationObjectMapperBuilder {

  private final JsonMapper objectMapper;

  public TigerConfigurationObjectMapperBuilder(TigerConfigurationLoader configurationLoader) {
    SimpleModule skipEvaluationModule = new SimpleModule();
    skipEvaluationModule.addDeserializer(
        String.class, new SkipEvaluationDeserializer(configurationLoader));
    this.objectMapper =
        JsonMapper.builder()
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
            .configure(MapperFeature.USE_GETTERS_AS_SETTERS, true)
            .configure(MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
            .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CASE)
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .addModule(new AllowDelayedPrimitiveResolvementModule(configurationLoader))
            .addModule(skipEvaluationModule)
            .defaultAttributes(
                ContextAttributes.getEmpty()
                    .withSharedAttributes(
                        Map.of(TIGER_CONFIGURATION_ATTRIBUTE_KEY, configurationLoader)))
            .build();
  }

  public ObjectMapper retrieveLenientObjectMapper() {
    return objectMapper;
  }

  @AllArgsConstructor
  private static class AllowDelayedPrimitiveResolvementModule extends JacksonModule {

    private TigerConfigurationLoader tigerConfigurationLoader;

    @Override
    public String getModuleName() {
      return "fallback provider";
    }

    @Override
    public Version version() {
      return Version.unknownVersion();
    }

    @Override
    public void setupModule(SetupContext setupContext) {
      setupContext.addHandler(new ClazzFallbackConverter(tigerConfigurationLoader));
    }
  }

  @AllArgsConstructor
  @RequiredArgsConstructor
  public static class SkipEvaluationDeserializer extends ValueDeserializer<String> {

    private final TigerConfigurationLoader configurationLoader;
    private boolean skipEvaluation;

    @Override
    public ValueDeserializer<?> createContextual(
        DeserializationContext ctxt, BeanProperty property) {
      this.skipEvaluation =
          property != null && property.getAnnotation(TigerSkipEvaluation.class) != null;
      return new SkipEvaluationDeserializer(configurationLoader, skipEvaluation);
    }

    @Override
    public String deserialize(
        JsonParser jsonParser, DeserializationContext deserializationContext) {
      final String valueAsString = jsonParser.getValueAsString();
      if (skipEvaluation) {
        return valueAsString;
      } else {
        return TokenSubstituteHelper.substitute(valueAsString, configurationLoader);
      }
    }
  }

  @AllArgsConstructor
  private static class ClazzFallbackConverter extends DeserializationProblemHandler {

    TigerConfigurationLoader tigerConfigurationLoader;

    @Override
    public Object handleWeirdStringValue(
        DeserializationContext ctxt,
        Class<?> targetType,
        String valueToConvert,
        String failureMsg) {
      if (valueToConvert.contains("!{") || valueToConvert.contains("${")) {
        final String substitute =
            TokenSubstituteHelper.substitute(valueToConvert, tigerConfigurationLoader);
        if (!substitute.equals(valueToConvert)) {
          final StringNode replacedTextNode = ctxt.getNodeFactory().stringNode(substitute);
          return ctxt.readTreeAsValue(replacedTextNode, targetType);
        }
        return returnTigerSpecificFallbackValue(ctxt, targetType, valueToConvert, failureMsg);
      }
      return super.handleWeirdStringValue(ctxt, targetType, valueToConvert, failureMsg);
    }

    Object returnTigerSpecificFallbackValue(
        DeserializationContext ctxt,
        Class<?> targetType,
        String valueToConvert,
        String failureMsg) {
      if (targetType.equals(Boolean.class)
          || targetType.equals(Integer.class)
          || targetType.equals(Long.class)
          || targetType.equals(Character.class)
          || targetType.equals(Double.class)
          || targetType.equals(Float.class)
          || targetType.equals(Byte.class)
          || targetType.equals(Short.class)) {
        return null;
      } else if (targetType.equals(boolean.class)) {
        return false;
      } else if (targetType.equals(int.class)) {
        return -1;
      } else if (targetType.equals(long.class)) {
        return (long) -1;
      } else if (targetType.equals(double.class)) {
        return -1.;
      } else if (targetType.equals(float.class)) {
        return -1f;
      } else if (targetType.equals(short.class)) {
        return (short) -1;
      } else if (targetType.equals(char.class)) {
        return ' ';
      } else if (targetType.equals(byte.class)) {
        return (byte) -1;
      } else {
        return super.handleWeirdStringValue(ctxt, targetType, valueToConvert, failureMsg);
      }
    }
  }
}
