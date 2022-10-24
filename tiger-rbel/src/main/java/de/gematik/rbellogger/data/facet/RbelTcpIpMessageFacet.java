/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.facet;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.facet.RbelFacet;
import de.gematik.rbellogger.data.facet.RbelHostnameFacet;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelMessageRenderer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import javax.annotation.Nullable;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
public class RbelTcpIpMessageFacet implements RbelFacet {

    static {
        RbelHtmlRenderer.registerFacetRenderer(new RbelMessageRenderer());
    }

    private final long sequenceNumber;
    private final RbelElement sender;
    private final RbelElement receiver;

    @Override
    public RbelMultiMap getChildElements() {
        return new RbelMultiMap()
            .with("sender", sender)
            .with("receiver", receiver);
    }

    public RbelHostname getSenderHostname() {
        return sender.getFacetOrFail(RbelHostnameFacet.class).toRbelHostname();
    }

    public RbelHostname getReceiverHostname() {
        return receiver.getFacetOrFail(RbelHostnameFacet.class).toRbelHostname();
    }
}
