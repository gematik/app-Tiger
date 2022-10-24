/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.facet;

import de.gematik.rbellogger.data.RbelMultiMap;
import java.util.List;

public interface RbelFacet {

    RbelMultiMap getChildElements();
}
