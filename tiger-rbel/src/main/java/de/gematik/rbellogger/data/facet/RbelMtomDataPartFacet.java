package de.gematik.rbellogger.data.facet;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import lombok.Data;

@Data
public class RbelMtomDataPartFacet implements RbelFacet {

    private final RbelElement content;
    private final RbelElement xpath;

    @Override
    public RbelMultiMap<RbelElement> getChildElements() {
        return new RbelMultiMap<>()
            .with("xpath", xpath)
            .with("content", content);
    }
}
