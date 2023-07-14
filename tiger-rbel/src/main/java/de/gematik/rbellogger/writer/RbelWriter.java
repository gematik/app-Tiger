/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.writer;

import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.key.RbelKeyManager;
import de.gematik.rbellogger.util.GenericPrettyPrinter;
import de.gematik.rbellogger.writer.tree.RbelContentTreeNode;
import de.gematik.test.tiger.common.jexl.TigerJexlContext;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RbelWriter {

    @Getter
    private final RbelKeyManager rbelKeyManager;

    public RbelWriter(RbelConverter rbelConverter) {
        this.rbelKeyManager = rbelConverter.getRbelKeyManager();
    }

    private final Map<RbelContentType, RbelSerializer> serializerMap = Map.of(
        RbelContentType.XML, new RbelXmlSerializer(),
        RbelContentType.JSON, new RbelJsonSerializer(),
        RbelContentType.JWT, new RbelJwtSerializer(),
        RbelContentType.JWE, new RbelJweSerializer(),
        RbelContentType.URL, new RbelUrlSerializer(),
        RbelContentType.BEARER_TOKEN, new RbelBearerTokenSerializer());

    public RbelSerializationResult serializeWithEnforcedContentType(RbelElement input, RbelContentType enforcedContentType, TigerJexlContext jexlContext) {
        return new RbelWriterInstance(Optional.ofNullable(enforcedContentType), rbelKeyManager, jexlContext)
            .serialize(input);
    }

    public RbelSerializationResult serialize(RbelElement input, TigerJexlContext jexlContext) {
        return new RbelWriterInstance(Optional.empty(), rbelKeyManager, jexlContext)
            .serialize(input);
    }

    private static void printTreeStructure(RbelContentTreeNode treeRootNode) {
        if (log.isDebugEnabled()) {
            GenericPrettyPrinter<RbelContentTreeNode> printer = new GenericPrettyPrinter<>(
                node -> node.childNodes().isEmpty(),
                node -> printNodeContent(node),
                node -> node.childNodes().stream()
            );
            printer.setNodeIntroPrinter(node -> node.getKey() + " (" + node.getType() + ") ");
            log.debug("Serializing the following tree: \n{}", printer.prettyPrint(treeRootNode));
        }
    }

    private static String printNodeContent(RbelContentTreeNode node) {
        if (node.getContent() == null) {
            return node.getKey() + ": <null>";
        } else {
            return node.getKey() + ": " + new String(node.getContent()).trim();
        }
    }

    @RequiredArgsConstructor
    public class RbelWriterInstance {

        private final Optional<RbelContentType> fixedContentType;
        @Getter
        private final RbelKeyManager rbelKeyManager;
        @Getter
        private final TigerJexlContext jexlContext;

        public RbelSerializationResult serialize(RbelElement input) {
            final RbelContentTreeNode treeRootNode = new RbelContentTreeConverter(input, jexlContext)
                .convertToContentTree();
            return renderTree(treeRootNode);
        }

        public RbelSerializationResult renderTree(RbelContentTreeNode treeRootNode) {
            if (treeRootNode.getType() == null) {
                return RbelSerializationResult.of(treeRootNode);
            }
            final RbelContentType determinedType = determineContentType(treeRootNode);
            treeRootNode.setType(determinedType);
            final RbelSerializer rbelSerializer = serializerMap.get(determinedType);
            if (rbelSerializer == null) {
                throw new RbelSerializationException("Could not find serializer for content-type '" + treeRootNode.getType() + "'");
            }
            return RbelSerializationResult.of(
                rbelSerializer.render(treeRootNode, this),
                treeRootNode.getType());
        }

        private RbelContentType determineContentType(RbelContentTreeNode treeRootNode) {
            return fixedContentType
                .orElseGet(treeRootNode::getType);
        }
    }
}
