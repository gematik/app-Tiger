/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.facet;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class RbelUriParameterFacet implements RbelFacet {

  private final RbelElement key;
  private final RbelElement value;

  @Override
  public RbelMultiMap getChildElements() {
    return new RbelMultiMap().with("key", key).with("value", value);
  }

  public String getKeyAsString() {
    return key.seekValue(String.class).orElseThrow();
  }
}
