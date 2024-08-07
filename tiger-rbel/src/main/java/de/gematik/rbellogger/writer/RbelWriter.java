/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.writer;

import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.key.RbelKeyManager;
import de.gematik.rbellogger.writer.tree.RbelContentTreeNode;
import de.gematik.test.tiger.common.jexl.TigerJexlContext;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RbelWriter {

  @Getter private final RbelKeyManager rbelKeyManager;

  public RbelWriter(RbelConverter rbelConverter) {
    this.rbelKeyManager = rbelConverter.getRbelKeyManager();
  }

  private final Map<RbelContentType, RbelSerializer> serializerMap =
      Map.of(
          RbelContentType.XML, new RbelXmlSerializer(),
          RbelContentType.JSON, new RbelJsonSerializer(),
          RbelContentType.JWT, new RbelJwtSerializer(),
          RbelContentType.JWE, new RbelJweSerializer(),
          RbelContentType.URL, new RbelUrlSerializer(),
          RbelContentType.BEARER_TOKEN, new RbelBearerTokenSerializer());

  public RbelSerializationResult serializeWithEnforcedContentType(
      RbelElement input, RbelContentType enforcedContentType, TigerJexlContext jexlContext) {
    return new RbelWriterInstance(
            Optional.ofNullable(enforcedContentType), rbelKeyManager, jexlContext)
        .serialize(input);
  }

  public RbelSerializationResult serialize(RbelElement input, TigerJexlContext jexlContext) {
    return new RbelWriterInstance(Optional.empty(), rbelKeyManager, jexlContext).serialize(input);
  }

  public RbelSerializationResult serialize(
      RbelContentTreeNode input, TigerJexlContext jexlContext) {
    return new RbelWriterInstance(Optional.empty(), rbelKeyManager, jexlContext).renderTree(input);
  }

  public RbelSerializationResult renderNode(
      RbelContentTreeNode input, TigerJexlContext jexlContext) {
    return new RbelWriterInstance(Optional.empty(), rbelKeyManager, jexlContext).renderNode(input);
  }

  @RequiredArgsConstructor
  public class RbelWriterInstance {

    private final Optional<RbelContentType> fixedContentType;
    @Getter private final RbelKeyManager rbelKeyManager;
    @Getter private final TigerJexlContext jexlContext;

    public RbelSerializationResult serialize(RbelElement input) {
      final RbelContentTreeNode treeRootNode =
          new RbelContentTreeConverter(input, jexlContext).convertToContentTree();
      return renderTree(treeRootNode);
    }

    public RbelSerializationResult renderTree(RbelContentTreeNode treeRootNode) {
      if (treeRootNode.getType() == null) {
        return RbelSerializationResult.of(treeRootNode);
      }
      final RbelSerializer rbelSerializer = prepareSerializer(treeRootNode);
      return RbelSerializationResult.of(
          rbelSerializer.render(treeRootNode, this),
          treeRootNode.getType(),
          treeRootNode.getElementCharset());
    }

    public RbelSerializationResult renderNode(RbelContentTreeNode treeRootNode) {
      if (treeRootNode.getType() == null) {
        return RbelSerializationResult.of(treeRootNode);
      }
      final RbelSerializer rbelSerializer = prepareSerializer(treeRootNode);
      return RbelSerializationResult.of(
          rbelSerializer.renderNode(treeRootNode, this),
          treeRootNode.getType(),
          treeRootNode.getElementCharset());
    }

    private RbelSerializer prepareSerializer(RbelContentTreeNode treeRootNode) {
      final RbelContentType determinedType = determineContentType(treeRootNode);
      treeRootNode.setType(determinedType);
      final RbelSerializer rbelSerializer = serializerMap.get(determinedType);
      if (rbelSerializer == null) {
        throw new RbelSerializationException(
            "Could not find serializer for content-type '" + treeRootNode.getType() + "'");
      }
      return rbelSerializer;
    }

    private RbelContentType determineContentType(RbelContentTreeNode treeRootNode) {
      return fixedContentType
          .filter(f -> treeRootNode.isRootNode() || f.isTransitive())
          .orElseGet(treeRootNode::getType);
    }
  }
}
