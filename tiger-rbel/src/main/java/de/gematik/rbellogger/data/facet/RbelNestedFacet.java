/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.facet;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Builder
@RequiredArgsConstructor
@Data
public class RbelNestedFacet implements RbelFacet {

    private final RbelElement nestedElement;

    @Override
    public RbelMultiMap getChildElements() {
        return new RbelMultiMap()
            .with("content", nestedElement);
    }
}
