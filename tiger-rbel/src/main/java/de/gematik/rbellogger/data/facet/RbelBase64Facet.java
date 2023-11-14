/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.facet;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import lombok.Data;

@Data
public class RbelBase64Facet implements RbelFacet {

  private final RbelElement child;

  @Override
  public RbelMultiMap getChildElements() {
    return new RbelMultiMap().with("decoded", child);
  }
}
