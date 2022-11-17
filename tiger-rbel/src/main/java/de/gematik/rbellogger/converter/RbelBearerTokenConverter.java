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
import de.gematik.rbellogger.data.facet.RbelBearerTokenFacet;

public class RbelBearerTokenConverter implements RbelConverterPlugin {
    public static final String BEARER_TOKEN_PREFIX = "Bearer ";

    @Override
    public void consumeElement(RbelElement rbelElement, RbelConverter converter) {
        if (rbelElement.getRawStringContent().startsWith(BEARER_TOKEN_PREFIX)) {
            final RbelElement bearerTokenElement = converter.convertElement(rbelElement.getRawStringContent().substring(BEARER_TOKEN_PREFIX.length()),
                    rbelElement);
            rbelElement.addFacet(new RbelBearerTokenFacet(bearerTokenElement));
        }
    }
}
