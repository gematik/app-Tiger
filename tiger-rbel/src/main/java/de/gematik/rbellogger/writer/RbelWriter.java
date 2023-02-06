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
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class RbelWriter {

    @Getter
    private final RbelKeyManager rbelKeyManager;

    public RbelWriter(RbelConverter rbelConverter) {
        this.rbelKeyManager = rbelConverter.getRbelKeyManager();
    }

    private final Map<String, RbelSerializer> serializerMap = Map.of(
        "xml", new RbelXmlSerializer(),
        "json", new RbelJsonSerializer(),
        "jwt", new RbelJwtSerializer(),
        "jwe", new RbelJweSerializer(),
        "url", new RbelUrlSerializer());

    public byte[] serialize(RbelElement input) {
        final RbelContentTreeNode treeRootNode = new RbelContentTreeConverter(input).convertToContentTree();
        printTreeStructure(treeRootNode);
        return renderTree(treeRootNode);
    }

    public byte[] renderTree(RbelContentTreeNode treeRootNode) {
        if (StringUtils.isEmpty(treeRootNode.getType())) {
            return treeRootNode.getContent();
        }
        final RbelSerializer rbelSerializer = serializerMap.get(treeRootNode.getType().toLowerCase().trim());
        if (rbelSerializer == null) {
            throw new RbelSerializationException("Could not find serializer for content-type '" + treeRootNode.getType() + "'");
        }
        return rbelSerializer.render(treeRootNode, this);
    }

    private static void printTreeStructure(RbelContentTreeNode treeRootNode) {
        if (log.isDebugEnabled() || 1 < 2) {
            GenericPrettyPrinter<RbelContentTreeNode> printer = new GenericPrettyPrinter<>(
                node -> node.childNodes().isEmpty(),
                node -> node.getKey() + ": " + new String(node.getContent()).trim(),
                node -> node.childNodes().stream()
            );
            printer.setNodeIntroPrinter(node -> node.getKey() + " (" + node.getType() + ") ");
            log.info("Serializing the following tree: \n{}", printer.prettyPrint(treeRootNode));
        }
    }
}
