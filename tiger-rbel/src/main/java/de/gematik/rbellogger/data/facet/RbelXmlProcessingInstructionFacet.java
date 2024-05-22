/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.facet;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import lombok.Value;

@Value
public class RbelXmlProcessingInstructionFacet implements RbelFacet {

  String target;

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<>();
  }
}
