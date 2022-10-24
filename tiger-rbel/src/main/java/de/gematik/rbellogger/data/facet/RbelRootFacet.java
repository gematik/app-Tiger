/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.facet;

import de.gematik.rbellogger.data.RbelMultiMap;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
@EqualsAndHashCode
public class RbelRootFacet<T extends RbelFacet> implements RbelFacet {

    private final T rootFacet;

    @Override
    public RbelMultiMap getChildElements() {
        return new RbelMultiMap();
    }
}
