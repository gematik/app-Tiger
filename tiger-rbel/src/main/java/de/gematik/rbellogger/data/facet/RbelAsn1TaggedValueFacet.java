/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.facet;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import lombok.Data;

@Data
public class RbelAsn1TaggedValueFacet implements RbelFacet {

  private final RbelElement tag;
  private final RbelElement nestedElement;

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    if (nestedElement == null) {
      return new RbelMultiMap<>();
    } else {
      return new RbelMultiMap<RbelElement>().with("content", nestedElement).with("tag", tag);
    }
  }
}
