/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.modifier;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelUriFacet;

import java.util.StringJoiner;

public class RbelUriWriter implements RbelElementWriter {

    @Override
    public boolean canWrite(RbelElement oldTargetElement) {
        return oldTargetElement.hasFacet(RbelUriFacet.class);
    }

    @Override
    public byte[] write(RbelElement oldTargetElement, RbelElement oldTargetModifiedChild, byte[] newContent) {
        final RbelUriFacet uriFacet = oldTargetElement.getFacetOrFail(RbelUriFacet.class);

        StringBuilder resultBuilder = new StringBuilder();
        if (uriFacet.getBasicPath() == oldTargetModifiedChild) {
            resultBuilder.append(newContent);
        } else {
            resultBuilder.append(uriFacet.getBasicPathString());
        }
        if (!uriFacet.getQueryParameters().isEmpty()) {
            StringJoiner joiner = new StringJoiner("&");
            for (RbelElement queryParameter : uriFacet.getQueryParameters()) {
                if (queryParameter == oldTargetModifiedChild) {
                    joiner.add(new String(newContent, oldTargetElement.getElementCharset()));
                } else {
                    joiner.add(queryParameter.getRawStringContent());
                }
            }
            resultBuilder.append("?");
            resultBuilder.append(joiner);
        }
        return resultBuilder.toString().getBytes(oldTargetElement.getElementCharset());
    }
}
