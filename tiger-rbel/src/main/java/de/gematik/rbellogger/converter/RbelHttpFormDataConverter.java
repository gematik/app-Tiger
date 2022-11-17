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

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.facet.RbelHttpFormDataFacet;
import de.gematik.rbellogger.data.facet.RbelHttpHeaderFacet;
import de.gematik.rbellogger.data.facet.RbelHttpMessageFacet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import de.gematik.rbellogger.data.facet.RbelMapFacet;
import org.apache.commons.lang3.tuple.Pair;

public class RbelHttpFormDataConverter implements RbelConverterPlugin {

    @Override
    public void consumeElement(RbelElement rbelElement, RbelConverter converter) {
        if (isBodyOfFormDataRequest(rbelElement)) {
            final RbelMultiMap formDataMap = Stream.of(rbelElement.getRawStringContent().split("&"))
                .map(param -> param.split("="))
                .filter(params -> params.length == 2)
                .map(paramList -> Pair.of(paramList[0], converter.convertElement(paramList[1], rbelElement)))
                .collect(RbelMultiMap.COLLECTOR);

            rbelElement.addFacet(RbelHttpFormDataFacet.builder()
                .formDataMap(formDataMap)
                .build());
        }
    }

    private boolean isBodyOfFormDataRequest(RbelElement rbelElement) {
        return Optional.ofNullable(rbelElement)
            .map(RbelElement::getParentNode)
            .filter(Objects::nonNull)
            .flatMap(el -> el.getFacet(RbelHttpMessageFacet.class))
            .map(RbelHttpMessageFacet::getHeader)
            .flatMap(el -> el.getFacet(RbelHttpHeaderFacet.class))
            .filter(header -> header.hasValueMatching("Content-Type", "application/x-www-form-urlencoded"))
            .isPresent();
    }
}
