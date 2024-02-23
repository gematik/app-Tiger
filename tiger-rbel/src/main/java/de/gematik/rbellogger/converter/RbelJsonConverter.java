/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.*;

public class RbelJsonConverter extends AbstractJacksonConverter<RbelJsonFacet> {

  public RbelJsonConverter() {
    super(
        new ObjectMapper().configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true),
        RbelJsonFacet.class);
  }

  @Override
  JsonNode convertContentUsingJackson(RbelElement target) throws JsonProcessingException {
    return getMapper().readTree(target.getRawStringContent());
  }

  @Override
  RbelJsonFacet buildFacetForNode(JsonNode node) {
    return RbelJsonFacet.builder().jsonElement(node).build();
  }

  @Override
  boolean shouldElementBeConsidered(RbelElement target) {
    String content = target.getRawStringContent();
    return content != null
        && ((content.contains("{") && content.contains("}"))
            || (content.contains("[") && content.contains("]")));
  }
}
