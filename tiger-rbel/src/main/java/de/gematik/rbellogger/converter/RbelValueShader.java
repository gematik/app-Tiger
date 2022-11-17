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

package de.gematik.rbellogger.converter;

import de.gematik.rbellogger.data.RbelJexlShadingExpression;
import de.gematik.rbellogger.data.RbelElement;
import java.util.*;

import de.gematik.rbellogger.data.facet.RbelNoteFacet;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class RbelValueShader {

    private final List<RbelJexlShadingExpression> jexlShadingMap = new ArrayList<>();
    private final List<RbelJexlShadingExpression> jexlNoteMap = new ArrayList<>();
    private final RbelJexlExecutor rbelJexlExecutor = new RbelJexlExecutor();

    public Optional<String> shadeValue(final Object element, final Optional<String> key) {
        return jexlShadingMap.stream()
            .filter(entry -> rbelJexlExecutor.matchesAsJexlExpression(element, entry.getJexlExpression(), key))
            .peek(entry -> entry.getNumberOfMatches().incrementAndGet())
            .map(entry -> String.format(entry.getShadingValue(), toStringValue(element)))
            .findFirst();
    }

    public void addNote(final RbelElement element) {
        jexlNoteMap.stream()
            .filter(entry -> rbelJexlExecutor.matchesAsJexlExpression(element, entry.getJexlExpression(), element.findKeyInParentElement()))
            .peek(entry -> entry.getNumberOfMatches().incrementAndGet())
            .map(entry -> String.format(entry.getShadingValue(), toStringValue(element)))
            .map(note -> new RbelNoteFacet(note, RbelNoteFacet.NoteStyling.INFO))
            .forEach(element::addFacet);
    }

    private String toStringValue(final Object value) {
        if (value instanceof RbelElement) {
            return ((RbelElement) value).getRawStringContent();
        } else {
            return value.toString();
        }
    }

    public RbelValueShader addSimpleShadingCriterion(String attributeName, String stringFValue) {
        jexlShadingMap.add(RbelJexlShadingExpression.builder()
            .jexlExpression("key == '" + attributeName + "'")
            .shadingValue(stringFValue)
            .build());
        return this;
    }

    public RbelValueShader addJexlShadingCriterion(String jsonPathExpression, String stringFValue) {
        jexlShadingMap.add(RbelJexlShadingExpression.builder()
            .jexlExpression(jsonPathExpression)
            .shadingValue(stringFValue)
            .build());
        return this;
    }

    public RbelValueShader addJexlNoteCriterion(String jsonPathExpression, String stringFValue) {
        jexlNoteMap.add(RbelJexlShadingExpression.builder()
            .jexlExpression(jsonPathExpression)
            .shadingValue(stringFValue)
            .build());
        return this;
    }

    public RbelConverterPlugin getPostConversionListener() {
        return (element, converter) -> addNote(element);
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
}
