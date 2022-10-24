/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.facet;

import de.gematik.rbellogger.data.RbelMultiMap;

public class RbelBinaryFacet implements RbelFacet {
    @Override
    public RbelMultiMap getChildElements() {
        return new RbelMultiMap();
    }
}
