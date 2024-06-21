/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.facet;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;

public class RbelMimeHeaderFacet extends RbelMultiMap<RbelElement> implements RbelFacet {

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return this;
  }
}
