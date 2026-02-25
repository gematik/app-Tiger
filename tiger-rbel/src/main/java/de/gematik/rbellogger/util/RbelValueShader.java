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
package de.gematik.rbellogger.util;

import static de.gematik.test.tiger.common.util.FunctionWithCheckedException.falseOnException;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConversionPhase;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.converter.ConverterInfo;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelJexlShadingExpression;
import de.gematik.rbellogger.data.core.RbelNoteFacet;
import de.gematik.test.tiger.common.jexl.TigerJexlExecutor;
import java.util.*;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class RbelValueShader {

  private final List<RbelJexlShadingExpression> jexlShadingMap = new ArrayList<>();
  private final List<RbelJexlShadingExpression> jexlNoteMap = new ArrayList<>();

  public Optional<String> shadeValue(final Object element, final Optional<String> key) {
    return jexlShadingMap.stream()
        .filter(
            falseOnException(
                entry ->
                    TigerJexlExecutor.matchesAsJexlExpression(
                        element, entry.getJexlExpression(), key)))
        .map(this::incrementNumberOfMatches)
        .map(entry -> String.format(entry.getShadingValue(), toStringValue(element)))
        .findFirst();
  }

  private RbelJexlShadingExpression incrementNumberOfMatches(
      RbelJexlShadingExpression rbelJexlShadingExpression) {
    rbelJexlShadingExpression.getNumberOfMatches().incrementAndGet();
    return rbelJexlShadingExpression;
  }

  public void addNote(final RbelElement element) {
    jexlNoteMap.stream()
        .filter(
            falseOnException(
                entry ->
                    TigerJexlExecutor.matchesAsJexlExpression(
                        element, entry.getJexlExpression(), element.findKeyInParentElement())))
        .map(this::incrementNumberOfMatches)
        .map(entry -> String.format(entry.getShadingValue(), toStringValue(element)))
        .map(note -> new RbelNoteFacet(note, RbelNoteFacet.NoteStyling.INFO))
        .forEach(element::addFacet);
  }

  private String toStringValue(final Object value) {
    if (value instanceof RbelElement element) {
      return element.getRawStringContent();
    } else {
      return value.toString();
    }
  }

  public RbelValueShader addSimpleShadingCriterion(String attributeName, String stringFValue) {
    jexlShadingMap.add(
        RbelJexlShadingExpression.builder()
            .jexlExpression("key == '" + attributeName + "'")
            .shadingValue(stringFValue)
            .build());
    return this;
  }

  public RbelValueShader addJexlShadingCriterion(String jsonPathExpression, String stringFValue) {
    jexlShadingMap.add(
        RbelJexlShadingExpression.builder()
            .jexlExpression(jsonPathExpression)
            .shadingValue(stringFValue)
            .build());
    return this;
  }

  public RbelValueShader addJexlNoteCriterion(String jsonPathExpression, String stringFValue) {
    jexlNoteMap.add(
        RbelJexlShadingExpression.builder()
            .jexlExpression(jsonPathExpression)
            .shadingValue(stringFValue)
            .build());
    return this;
  }

  public RbelConverterPlugin getPostConversionListener() {
    return new RbelJexlShadingConverter();
  }

  @Builder
  @Data
  public static class JexlMessage {

    public final String method;
    public final String url;
    public final boolean isRequest;
    public final boolean isResponse;
    public final Map<String, String> headers;
    public final String bodyAsString;
    public final RbelElement body;
  }

  @ConverterInfo(addAutomatically = false)
  public class RbelJexlShadingConverter extends RbelConverterPlugin {

    @Override
    public RbelConversionPhase getPhase() {
      return RbelConversionPhase.CONTENT_ENRICHMENT;
    }

    @Override
    public void consumeElement(RbelElement rbelElement, RbelConversionExecutor converter) {
      addNote(rbelElement);
      rbelElement.getChildNodesStream().forEach(child -> consumeElement(child, converter));
    }
  }
}
