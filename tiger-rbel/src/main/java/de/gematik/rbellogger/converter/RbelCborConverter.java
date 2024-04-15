/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import de.gematik.rbellogger.data.facet.*;

/**
 * Converter for CBOR(Concise Binary Object Representation) format.
 *
 * <p>This class utilizes the Jackson library for CBOR serialization and deserialization.
 */
public class RbelCborConverter extends AbstractJacksonConverter<RbelCborFacet> {

  public RbelCborConverter() {
    super(new CBORMapper(), RbelCborFacet.class);
  }

  @Override
  RbelCborFacet buildFacetForNode(JsonNode node) {
    return RbelCborFacet.builder().node(node).build();
  }
}
