/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.writer;

import de.gematik.rbellogger.writer.tree.RbelContentTreeNode;
import de.gematik.rbellogger.writer.tree.RbelJsonElementToNodeConverter;
import java.util.StringJoiner;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RbelJsonSerializer implements RbelSerializer {

    @Override
    public byte[] render(RbelContentTreeNode node, RbelWriter rbelWriter) {
        return renderToString(node, rbelWriter).getBytes();
    }

    public String renderToString(RbelContentTreeNode node, RbelWriter rbelWriter) {
        if (isPrimitive(node) || !node.hasTypeOptional("JSON").orElse(true)) {
            if (isStringPrimitive(node)) {
                return "\"" + getStringContentForNode(node, rbelWriter) + "\"";
            } else {
                return getStringContentForNode(node, rbelWriter);
            }
        } else if (isJsonObject(node)) {
            StringJoiner joiner = new StringJoiner(",");
            for (RbelContentTreeNode childNode : node.childNodes()) {
                joiner.add("\"" + childNode.getKey() + "\": " + renderToString(childNode, rbelWriter));
            }
            return "{" + joiner + "}";
        } else if (isJsonArray(node)) {
            StringJoiner joiner = new StringJoiner(",");
            for (RbelContentTreeNode childNode : node.childNodes()) {
                joiner.add(renderToString(childNode, rbelWriter));
            }
            return "[" + joiner + "]";
        } else {
            throw new RuntimeException();
        }
    }

    private static String getStringContentForNode(RbelContentTreeNode node, RbelWriter rbelWriter) {
        if (node.childNodes().isEmpty()) {
            return new String(node.getContent(), node.getCharset());
        } else {
            return new String(rbelWriter.renderTree(node), node.getCharset());
        }
    }

    public static boolean isJsonArray(RbelContentTreeNode node) {
        return node.attributes().containsKey(RbelJsonElementToNodeConverter.JSON_ARRAY);
    }

    private boolean isJsonObject(RbelContentTreeNode node) {
        return !node.attributes().containsKey(RbelJsonElementToNodeConverter.JSON_ARRAY);
    }

    private boolean isStringPrimitive(RbelContentTreeNode node) {
        return !node.attributes().containsKey(RbelJsonElementToNodeConverter.JSON_NON_STRING_PRIMITIVE);
    }

    private boolean isPrimitive(RbelContentTreeNode node) {
        return node.attributes().containsKey(RbelJsonElementToNodeConverter.JSON_PRIMITIVE)
            || node.childNodes().isEmpty();
    }
}
