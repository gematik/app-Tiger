/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.facet;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class RbelListFacet implements RbelFacet {

    private final List<RbelElement> childNodes;

    @Override
    public RbelMultiMap getChildElements() {
        RbelMultiMap result = new RbelMultiMap();
        AtomicInteger index = new AtomicInteger();
        childNodes.forEach(element -> result.put(String.valueOf(index.getAndIncrement()), element));
        return result;
    }
}
