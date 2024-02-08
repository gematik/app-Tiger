/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.facet;

import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import lombok.RequiredArgsConstructor;

/** Marker facet to indicate that the parsing of this message is not complete */
@RequiredArgsConstructor
public class RbelParsingNotCompleteFacet implements RbelFacet {

  private final RbelConverter rbelConverter;

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<>();
  }

  @Override
  public void facetRemovedCallback(RbelElement element) {
    rbelConverter.signalMessageParsingIsComplete(element);
  }
}
