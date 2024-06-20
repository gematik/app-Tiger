/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.facet;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class RbelDecryptedEmailFacet implements RbelFacet {

  private final RbelElement decrypted;

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<RbelElement>().with("decrypted", decrypted);
  }
}
