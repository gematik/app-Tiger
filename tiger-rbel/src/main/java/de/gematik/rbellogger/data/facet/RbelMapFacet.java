/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.facet;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@RequiredArgsConstructor
@Builder(toBuilder = true)
public class RbelMapFacet implements RbelFacet {

  private final RbelMultiMap<RbelElement> childNodes;

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return childNodes;
  }
}
