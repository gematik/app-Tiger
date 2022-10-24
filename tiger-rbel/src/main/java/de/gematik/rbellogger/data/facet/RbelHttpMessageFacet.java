/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.facet;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;

import java.util.ArrayList;
import java.util.List;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class RbelHttpMessageFacet implements RbelFacet {

    private final RbelElement header;
    private final RbelElement body;

    @Builder(toBuilder = true)
    public RbelHttpMessageFacet(RbelElement header, RbelElement body) {
        this.header = header;
        this.body = body;
    }

    @Override
    public RbelMultiMap getChildElements() {
        return new RbelMultiMap()
            .with("body", body)
            .with("header", header);
    }
}
