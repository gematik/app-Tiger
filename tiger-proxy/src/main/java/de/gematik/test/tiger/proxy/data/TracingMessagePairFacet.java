/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.data;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.facet.RbelFacet;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TracingMessagePairFacet implements RbelFacet {

    private final RbelElement response;
    private final RbelElement request;

    @Override
    public RbelMultiMap getChildElements() {
        return new RbelMultiMap();
    }
}
