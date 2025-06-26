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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.cfg.ContextAttributes;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.gematik.test.tiger.common.TokenSubstituteHelper;
import de.gematik.test.tiger.zion.config.TigerSkipEvaluation;
import java.io.IOException;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

class TigerConfigurationObjectMapperBuilder {

  private final JsonMapper objectMapper;

  public TigerConfigurationObjectMapperBuilder(TigerConfigurationLoader configurationLoader) {
    SimpleModule skipEvaluationModule = new SimpleModule();
    skipEvaluationModule.addDeserializer(
        String.class, new SkipEvaluationDeserializer(configurationLoader));
    this.objectMapper =
        JsonMapper.builder()
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CASE)
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .addModule(new JavaTimeModule())
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
  private static class AllowDelayedPrimitiveResolvementModule extends Module {

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
      setupContext.addDeserializationProblemHandler(
          new ClazzFallbackConverter(tigerConfigurationLoader));
    }
  }

  @RequiredArgsConstructor
  @AllArgsConstructor
  @Slf4j
  public static class SkipEvaluationDeserializer extends JsonDeserializer<String>
      implements ContextualDeserializer {

    private final TigerConfigurationLoader configurationLoader;
    private boolean skipEvaluation;

    @Override
    public JsonDeserializer<?> createContextual(
        DeserializationContext ctxt, BeanProperty property) {
      this.skipEvaluation =
          property != null && property.getAnnotation(TigerSkipEvaluation.class) != null;
      return new SkipEvaluationDeserializer(configurationLoader, skipEvaluation);
    }

    @Override
    public String deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
        throws IOException {
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
        DeserializationContext ctxt, Class<?> targetType, String valueToConvert, String failureMsg)
        throws IOException {
      if (valueToConvert.contains("!{") || valueToConvert.contains("${")) {
        final String substitute =
            TokenSubstituteHelper.substitute(valueToConvert, tigerConfigurationLoader);
        if (!substitute.equals(valueToConvert)) {
          final TextNode replacedTextNode = ctxt.getNodeFactory().textNode(substitute);
          return ctxt.readTreeAsValue(replacedTextNode, targetType);
        }
        return returnTigerSpecificFallbackValue(ctxt, targetType, valueToConvert, failureMsg);
      }
      return super.handleWeirdStringValue(ctxt, targetType, valueToConvert, failureMsg);
    }

    Object returnTigerSpecificFallbackValue(
        DeserializationContext ctxt, Class<?> targetType, String valueToConvert, String failureMsg)
        throws IOException {
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
