package de.gematik.rbellogger.data.facet;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;

/** Marker-facet indicating a non-paired-message. Used mainly for mesh-setup. */
public class TigerNonPairedMessageFacet implements RbelFacet {

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<>();
  }
}
