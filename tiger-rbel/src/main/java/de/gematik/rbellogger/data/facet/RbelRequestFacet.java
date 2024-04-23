/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.facet;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.experimental.SuperBuilder;

/** Empty marker: The element is a request. */
@Data
@SuperBuilder
@RequiredArgsConstructor
public class RbelRequestFacet implements RbelFacet {

  /** Short info string describing this request. Will primarily be displayed in the menu. */
  private final String menuInfoString;

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<>();
  }
}
