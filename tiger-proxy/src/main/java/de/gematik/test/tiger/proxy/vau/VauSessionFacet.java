/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.vau;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelFacet;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.tuple.Pair;

@Data
@Builder(toBuilder = true)
public class VauSessionFacet implements RbelFacet {

    private final RbelElement recordId;

    @Override
    public List<Map.Entry<String, RbelElement>> getChildElements() {
        return List.of(
            Pair.of("recordId", recordId)
        );
    }
}
