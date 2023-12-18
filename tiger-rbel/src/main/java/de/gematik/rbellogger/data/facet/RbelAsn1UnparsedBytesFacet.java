/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.facet;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import lombok.Data;

@Data
public class RbelAsn1UnparsedBytesFacet implements RbelFacet {

  private final RbelElement unparsedBytes;

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<RbelElement>().with("unparsedBytes", unparsedBytes);
  }
}
