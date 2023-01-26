/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.writer;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.util.GenericPrettyPrinter;
import de.gematik.rbellogger.writer.tree.RbelContentTreeNode;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class RbelWriter {

    private final Map<String, RbelSerializer> serializerMap = Map.of(
        "xml", new RbelXmlSerializer(),
        "json", new RbelJsonSerializer());

    public byte[] serialize(RbelElement input) {
        final RbelContentTreeNode treeRootNode = new RbelContentTreeConverter(input).convertToContentTree();
        printTreeStructure(treeRootNode);
        return renderTree(treeRootNode);
    }

    public byte[] renderTree(RbelContentTreeNode treeRootNode) {
        if (StringUtils.isEmpty(treeRootNode.getType())) {
            return treeRootNode.getContent();
        }
        return serializerMap.get(treeRootNode.getType())
            .render(treeRootNode, this);
    }

    private static void printTreeStructure(RbelContentTreeNode treeRootNode) {
        if (log.isDebugEnabled()) {
            GenericPrettyPrinter<RbelContentTreeNode> printer = new GenericPrettyPrinter<>(
                node -> node.childNodes().isEmpty(),
                node -> node.getKey() + ": " + new String(node.getContent()).trim(),
                node -> node.childNodes().stream()
            );
            printer.setNodeIntroPrinter(node -> node.getKey() + " (" + node.getType() + ") ");
            log.debug("Serializing the following tree: \n{}", printer.prettyPrint(treeRootNode));
        }
    }
}
