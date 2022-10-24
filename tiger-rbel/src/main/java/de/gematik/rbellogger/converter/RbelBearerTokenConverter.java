/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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
