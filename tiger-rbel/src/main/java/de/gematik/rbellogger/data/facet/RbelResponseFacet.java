/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.facet;

import de.gematik.rbellogger.data.RbelMultiMap;
import lombok.Data;

/** Empty marker: The element is a response. */
@Data
public class RbelResponseFacet implements RbelFacet {

  /** Short info string describing this request. Will primarily be displayed in the menu. */
  private final String menuInfoString;

  @Override
  public RbelMultiMap getChildElements() {
    return new RbelMultiMap();
  }
}
