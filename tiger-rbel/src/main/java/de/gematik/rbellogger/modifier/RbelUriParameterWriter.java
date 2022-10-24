/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.modifier;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelUriParameterFacet;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.StringJoiner;

public class RbelUriParameterWriter implements RbelElementWriter {

    @Override
    public boolean canWrite(RbelElement oldTargetElement) {
        return oldTargetElement.hasFacet(RbelUriParameterFacet.class);
    }

    @Override
    public byte[] write(RbelElement oldTargetElement, RbelElement oldTargetModifiedChild, byte[] newContent) {
        final RbelUriParameterFacet uriFacet = oldTargetElement.getFacetOrFail(RbelUriParameterFacet.class);
        StringJoiner result = new StringJoiner("=");
        if (uriFacet.getKey() == oldTargetModifiedChild) {
            result.add(URLEncoder.encode(new String(newContent), StandardCharsets.UTF_8));
        } else {
            result.add(uriFacet.getKeyAsString());
        }

        if (uriFacet.getValue() == oldTargetModifiedChild) {
            result.add(URLEncoder.encode(new String(newContent), StandardCharsets.UTF_8));
        } else {
            result.add(uriFacet.getValue().getRawStringContent());
        }

        return result.toString().getBytes(oldTargetElement.getElementCharset());
    }
}
