/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.writer.tree;

import de.gematik.rbellogger.data.RbelMultiMap;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

@RequiredArgsConstructor
public abstract class RbelContentTreeNode {


    private final RbelMultiMap<RbelContentTreeNode> childNodes;
    private Map<String, String> attributeMap = new HashMap<>();
    @Getter
    @Setter
    private Charset charset;
    @Getter
    @Setter
    private String key;
    @Getter
    @Setter
    private String type;
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

    public Optional<Boolean> hasTypeOptional(String typeToCheck) {
        return Optional.ofNullable(getType())
            .map(t -> StringUtils.equals(t, typeToCheck));
    }

    public Optional<RbelContentTreeNode> getParentNode() {
        return Optional.ofNullable(parentNode);
    }
}
