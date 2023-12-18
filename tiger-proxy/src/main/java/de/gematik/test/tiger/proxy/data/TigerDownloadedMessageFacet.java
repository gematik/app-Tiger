/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.data;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.facet.RbelFacet;

public class TigerDownloadedMessageFacet implements RbelFacet {

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<>();
  }
}
