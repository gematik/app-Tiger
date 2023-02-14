/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.writer;

import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.key.RbelKeyManager;
import de.gematik.rbellogger.util.GenericPrettyPrinter;
import de.gematik.rbellogger.writer.tree.RbelContentTreeNode;
import de.gematik.test.tiger.common.jexl.InlineJexlToolbox;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.crypto.spec.OAEPParameterSpec;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

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
        RbelContentType.URL, new RbelUrlSerializer());

    public byte[] serializeWithEnforcedContentType(RbelElement input, RbelContentType enforcedContentType) {
        return new RbelWriterInstance(Optional.ofNullable(enforcedContentType), rbelKeyManager)
            .serialize(input);
    }

    public byte[] serialize(RbelElement input) {
        return new RbelWriterInstance(Optional.empty(), rbelKeyManager)
            .serialize(input);
    }

    private static void printTreeStructure(RbelContentTreeNode treeRootNode) {
        if (log.isDebugEnabled() || 1 < 2) {
            GenericPrettyPrinter<RbelContentTreeNode> printer = new GenericPrettyPrinter<>(
                node -> node.childNodes().isEmpty(),
                node -> printNodeContent(node),
                node -> node.childNodes().stream()
            );
            printer.setNodeIntroPrinter(node -> node.getKey() + " (" + node.getType() + ") ");
            log.info("Serializing the following tree: \n{}", printer.prettyPrint(treeRootNode));
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

        public byte[] serialize(RbelElement input) {
            final RbelContentTreeNode treeRootNode = new RbelContentTreeConverter(input).convertToContentTree();
            printTreeStructure(treeRootNode);
            return renderTree(treeRootNode);
        }

        public byte[] renderTree(RbelContentTreeNode treeRootNode) {
            if (treeRootNode.getType() == null) {
                return treeRootNode.getContent();
            }
            final RbelContentType determinedType = determineContentType(treeRootNode);
            treeRootNode.setType(determinedType);
            final RbelSerializer rbelSerializer = serializerMap.get(determinedType);
            if (rbelSerializer == null) {
                throw new RbelSerializationException("Could not find serializer for content-type '" + treeRootNode.getType() + "'");
            }
            return rbelSerializer.render(treeRootNode, this);
        }

        private RbelContentType determineContentType(RbelContentTreeNode treeRootNode) {
            return fixedContentType
                .orElseGet(treeRootNode::getType);
        }
    }
}
