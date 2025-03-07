package de.gematik.test.tiger.lib.json;

import com.fasterxml.jackson.databind.JsonNode;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelCborFacet;
import de.gematik.rbellogger.data.facet.RbelJsonFacet;
import de.gematik.test.tiger.lib.rbel.RbelContentValidator;

public abstract class AbstractRbelJsonChecker implements RbelContentValidator {

  public String getAsJsonString(RbelElement target) {
    if (target.hasFacet(RbelJsonFacet.class)) {
      return target.getRawStringContent();
    } else if (target.hasFacet(RbelCborFacet.class)) {
      return target
          .getFacet(RbelCborFacet.class)
          .map(RbelCborFacet::getNode)
          .map(JsonNode::toString)
          .orElse("");
    } else {
      throw new AssertionError("Node is neither JSON nor CBOR, can not match with JSON");
    }
  }
}
