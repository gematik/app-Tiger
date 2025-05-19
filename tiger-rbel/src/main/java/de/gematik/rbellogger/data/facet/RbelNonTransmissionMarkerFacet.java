package de.gematik.rbellogger.data.facet;

import de.gematik.rbellogger.data.core.RbelFacet;

/**
 * Marker facet for messages (or chunks), that should be NOT transmitted! (and also not be saved to
 * file)
 */
public class RbelNonTransmissionMarkerFacet implements RbelFacet {}
