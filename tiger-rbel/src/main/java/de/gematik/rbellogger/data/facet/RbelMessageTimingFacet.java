/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.facet;

import de.gematik.rbellogger.data.RbelMultiMap;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.ZonedDateTime;

@Slf4j
@Data
@RequiredArgsConstructor
@Builder(toBuilder = true)
public class RbelMessageTimingFacet implements RbelFacet {

    @NonNull
    private final ZonedDateTime transmissionTime;

    @Override
    public RbelMultiMap getChildElements() {
        return new RbelMultiMap();
    }
}
