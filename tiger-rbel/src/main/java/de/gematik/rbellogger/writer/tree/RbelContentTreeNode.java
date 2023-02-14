/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.writer.tree;

import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.writer.RbelContentType;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.With;
import org.apache.commons.lang3.StringUtils;

@RequiredArgsConstructor
public class RbelContentTreeNode {


    private final RbelMultiMap<RbelContentTreeNode> childNodes;
    private Map<String, String> attributeMap = new HashMap<>();
    @Setter
    private Charset charset;
    @Getter
    @Setter
    private String key;
    @Getter
    @Setter
    private RbelContentType type;
    @Setter
    private RbelContentTreeNode parentNode;
    @Getter @Setter
    private byte[] content;

    public List<RbelContentTreeNode> childNodes() {
        return childNodes.values();
    }

    public void setChildNodes(List<RbelContentTreeNode> newChildNodes) {
        childNodes.clear();
        newChildNodes.forEach(node -> childNodes.put(node.getKey(), node));
    }

    public Map<String, String> attributes() {
        return attributeMap;
    }

    public Optional<Boolean> hasTypeOptional(RbelContentType typeToCheck) {
        return Optional.ofNullable(getType())
            .map(t -> t == typeToCheck);
    }

    public Optional<RbelContentTreeNode> getParentNode() {
        return Optional.ofNullable(parentNode);
    }

    public Optional<RbelContentTreeNode> childNode(String nodeKey) {
        return Optional.ofNullable(childNodes.get(nodeKey));
    }

    public String getContentAsString() {
        return new String(getContent(), getCharset());
    }

    public Charset getCharset() {
        return Optional.ofNullable(charset)
            .orElse(StandardCharsets.UTF_8);
    }
}
