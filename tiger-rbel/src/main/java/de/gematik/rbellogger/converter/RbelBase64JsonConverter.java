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
import de.gematik.rbellogger.data.facet.RbelBase64Facet;
import de.gematik.rbellogger.data.facet.RbelJsonFacet;
import de.gematik.rbellogger.data.facet.RbelRootFacet;
import de.gematik.rbellogger.data.facet.RbelXmlFacet;

import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.Objects;
import java.util.Optional;

public class RbelBase64JsonConverter extends RbelJsonConverter {

    @Override
    public void consumeElement(RbelElement rbel, RbelConverter context) {
        if (rbel.getRawStringContent().isEmpty()) {
            return;
        }
        safeConvertBase64Using(rbel.getRawStringContent(), Base64.getDecoder(), context, rbel)
            .or(() -> safeConvertBase64Using(rbel.getRawStringContent(), Base64.getUrlDecoder(), context, rbel))
            .ifPresent(rbel::addFacet);
    }

    private Optional<RbelBase64Facet> safeConvertBase64Using(String input, Decoder decoder, RbelConverter context,
                                                             RbelElement parentNode) {
        return Optional.ofNullable(input)
            .map(i -> {
                try {
                    return decoder.decode(i);
                } catch (IllegalArgumentException e) {
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .map(data -> new RbelElement(data, parentNode))
            .stream()
            .peek(innerNode -> context.convertElement(innerNode))
            .filter(innerNode -> innerNode.hasFacet(RbelRootFacet.class))
            .filter(innerNode -> innerNode.getFacetOrFail(RbelRootFacet.class).getRootFacet() instanceof RbelJsonFacet
                || innerNode.getFacetOrFail(RbelRootFacet.class).getRootFacet() instanceof RbelXmlFacet)
            .map(child -> new RbelBase64Facet(child))
            .findAny();
    }
}
