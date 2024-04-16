/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.writer.tree;

import static de.gematik.rbellogger.writer.RbelContentTreeConverter.ENCODE_AS;

import com.fasterxml.jackson.databind.JsonNode;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.facet.RbelJsonFacet;
import de.gematik.rbellogger.data.facet.RbelNestedFacet;
import de.gematik.rbellogger.writer.RbelContentTreeConverter;
import de.gematik.rbellogger.writer.RbelContentType;
import de.gematik.test.tiger.common.config.TigerConfigurationLoader;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

public class RbelJsonElementToNodeConverter implements RbelElementToContentTreeNodeConverter {

  public static final String JSON_PRIMITIVE = "jsonPrimitive";
  public static final String JSON_NON_STRING_PRIMITIVE = "jsonNonStringPrimitive";
  public static final String JSON_ARRAY = "jsonArray";

  @Override
  public boolean shouldConvert(RbelElement target) {
    return target.hasFacet(RbelJsonFacet.class);
  }

  @Override
  public RbelContentTreeNode convert(
      RbelElement el, TigerConfigurationLoader context, RbelContentTreeConverter converter) {
    final RbelMultiMap<RbelContentTreeNode> map =
        el.getChildNodesWithKey().stream()
            .flatMap(
                entry ->
                    convertNode(entry.getValue(), entry.getKey(), context, converter).stream()
                        .map(childNode -> Pair.of(entry.getKey(), childNode)))
            .collect(RbelMultiMap.COLLECTOR);
    final RbelStrictOrderContentTreeNode result =
        new RbelStrictOrderContentTreeNode(map, el.getRawContent());
    result.setType(
        context
            .readStringOptional(ENCODE_AS)
            .map(RbelContentType::seekValueFor)
            .orElse(RbelContentType.JSON));
    if (el.getFacet(RbelJsonFacet.class)
      .map(facet -> facet.getJsonElement().isArray())
      .orElse(false)) {
      result.attributes().put(JSON_ARRAY, "true");
    }
    return result;
  }

  private List<RbelContentTreeNode> convertNode(
      RbelElement value,
      String key,
      TigerConfigurationLoader context,
      RbelContentTreeConverter converter) {
    if (nodeHasTgrAttribute(value, JSON_PRIMITIVE)
        || nodeHasTgrAttribute(value, JSON_NON_STRING_PRIMITIVE)) {
      final RbelElement valueElement = value.getFirst("value").orElseThrow();
      final List<RbelContentTreeNode> nodes = convertNode(valueElement, key, context, converter);
      populatePrimitiveNode(valueElement, nodes);
      if (nodeHasTgrAttribute(value, JSON_NON_STRING_PRIMITIVE)) {
        nodes.forEach(node -> node.attributes().putIfAbsent(JSON_NON_STRING_PRIMITIVE, "true"));
      }
      return nodes;
    }
    if (value.hasFacet(RbelJsonFacet.class) && value.hasFacet(RbelNestedFacet.class)) {
      final List<RbelContentTreeNode> nodes =
          convertNode(
              value.getFacetOrFail(RbelNestedFacet.class).getNestedElement(),
              key,
              context,
              converter);
      populatePrimitiveNode(value, nodes);
      return nodes;
    }
    final List<RbelContentTreeNode> result = converter.convertNode(value, key, context);
    return result;
  }

  private void populatePrimitiveNode(RbelElement valueElement, List<RbelContentTreeNode> nodes) {
    nodes.forEach(node -> node.attributes().put(JSON_PRIMITIVE, "true"));
    if (!valueElement
        .getFacet(RbelJsonFacet.class)
        .map(RbelJsonFacet::getJsonElement)
        .map(JsonNode::isTextual)
        .orElse(false)) {
      nodes.forEach(node -> node.attributes().put(JSON_NON_STRING_PRIMITIVE, "true"));
    }
  }

  private boolean nodeHasTgrAttribute(RbelElement value, String attributeValueToBeChecked) {
    return value
        .findElement("$.tgrAttributes.[?(content=='\"" + attributeValueToBeChecked + "\"')]")
        .isPresent();
  }
}
