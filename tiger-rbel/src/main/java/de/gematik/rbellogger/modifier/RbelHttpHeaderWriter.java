/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.modifier;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelHttpHeaderFacet;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.StringJoiner;

import static java.nio.charset.StandardCharsets.UTF_8;

public class RbelHttpHeaderWriter implements RbelElementWriter {
    @Override
    public boolean canWrite(RbelElement oldTargetElement) {
        return oldTargetElement.hasFacet(RbelHttpHeaderFacet.class);
    }

    @Override
    public byte[] write(RbelElement oldTargetElement, RbelElement oldTargetModifiedChild, byte[] newContent) {
        StringJoiner joiner = new StringJoiner("\r\n");
        for (Map.Entry<String, RbelElement> entry : oldTargetElement.getFacetOrFail(RbelHttpHeaderFacet.class).entries()) {
            if (entry.getValue() == oldTargetModifiedChild) {
                joiner.add(entry.getKey() + ": " + new String(newContent, UTF_8));
            } else {
                joiner.add(entry.getKey() + ": " + entry.getValue().getRawStringContent());
            }
        }
        return joiner.toString().getBytes(UTF_8);
    }
}
