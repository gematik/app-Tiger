/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.writer.tree;

import de.gematik.rbellogger.RbelContent;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.facet.RbelFacet;
import de.gematik.rbellogger.util.RbelPathExecutor;
import de.gematik.rbellogger.writer.RbelContentType;
import lombok.Getter;
import lombok.Setter;

import javax.annotation.Nullable;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class RbelContentTreeNode implements RbelContent {

    private RbelMultiMap<RbelContentTreeNode> childNodes;
    private Map<String, String> attributeMap = new HashMap<>();
    @Setter
    private Charset charset;
    @Setter
    private String key;
    @Getter
    @Setter
    private RbelContentType type;
    @Setter
    private RbelContentTreeNode parentNode;
    @Getter @Setter
    private byte[] content;

    public RbelContentTreeNode(RbelMultiMap<RbelContentTreeNode> childNodes, byte[] content) {
        this.setChildNodes(childNodes);
        this.setContent(content);
    }

    public void setChildNodes(List<RbelContentTreeNode> newChildNodes) {
        childNodes.clear();
        newChildNodes.forEach(node -> {
            childNodes.put(node.getKey().orElseThrow(), node);
            node.setParentNode(this);
        });
    }

    public void setChildNodes(RbelMultiMap<RbelContentTreeNode> childNodes) {
        this.childNodes = childNodes;
        this.childNodes.forEach((k, e) -> e.setParentNode(this));
    }

    public void setChildNode(String key, RbelContentTreeNode newChildNode) {
        childNodes.remove(key);
        childNodes.put(key, newChildNode);
        newChildNode.setParentNode(this);
        newChildNode.setKey(key);
    }

    public Map<String, String> attributes() {
        return attributeMap;
    }

    public Optional<Boolean> hasTypeOptional(RbelContentType typeToCheck) {
        return Optional.ofNullable(getType())
            .map(t -> t == typeToCheck);
    }

    @Override
    public RbelContentTreeNode getParentNode() {
        return parentNode;
    }

    @Override
    public Optional<RbelContentTreeNode> getFirst(String key) {
        return getChildNodesWithKey().stream()
                .filter(entry -> entry.getKey().equals(key))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    @Override
    public List<RbelContentTreeNode> getAll(String key) {
        return getChildNodesWithKey().stream()
                .filter(entry -> entry.getKey().equals(key))
                .map(Map.Entry::getValue)
                .toList();
    }

    @Override
    public List<RbelContentTreeNode> getChildNodes() {
        return childNodes
                .getValues()
                .stream()
                .map(Map.Entry::getValue)
                .toList();
    }

    @Override
    public RbelMultiMap<RbelContentTreeNode> getChildNodesWithKey() {
        return childNodes
                .getValues()
                .stream()
                .collect(RbelMultiMap.COLLECTOR);
    }

    @Override
    public Optional<RbelContentTreeNode> findElement(String rbelPath) {
        final List<RbelContentTreeNode> resultList = findRbelPathMembers(rbelPath);
        if (resultList.isEmpty()) {
            return Optional.empty();
        }
        if (resultList.size() == 1) {
            return Optional.of(resultList.get(0));
        }
        throw new RbelContentTreeNode.RbelPathNotUniqueException(
                "RbelPath '" + rbelPath + "' is not unique! Found " + resultList.size() + " elements, expected only one!");
    }

    @Override
    public RbelContentTreeNode findRootElement() {
        RbelContentTreeNode result = this;
        RbelContentTreeNode newResult = result.getParentNode();
        while (newResult != null) {
            result = newResult;
            newResult = result.getParentNode();
        }
        return result;
    }

    @Override
    public String findNodePath() {
        LinkedList<Optional<String>> keyList = new LinkedList<>();
        final AtomicReference<RbelContentTreeNode> ptr = new AtomicReference<>(this);
        while (ptr.get().getParentNode() != null) {
            keyList.addFirst(
                    ptr.get().getParentNode().getChildNodesWithKey().stream()
                            .filter(entry -> entry.getValue() == ptr.get())
                            .map(Map.Entry::getKey).findFirst());
            ptr.set(ptr.get().getParentNode());
        }
        return keyList.stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.joining("."));
    }

    @Override
    public List<RbelContentTreeNode> findRbelPathMembers(String rbelPath) {
        return new RbelPathExecutor(this, rbelPath)
                .execute(RbelContentTreeNode.class)
                .stream()
                .map(RbelContentTreeNode::castToRbelContentTreeNode)
                .toList();
    }

    @Override
    public Optional<String> findKeyInParentElement() {
        return Optional.of(this)
                .map(RbelContentTreeNode::getParentNode)
                .filter(Objects::nonNull)
                .stream()
                .flatMap(parent -> parent.getChildNodesWithKey().stream())
                .filter(e -> e.getValue() == this)
                .map(Map.Entry::getKey)
                .findFirst();
    }

    @Override
    @Nullable
    public String getRawStringContent() {
        if (content == null) {
            return null;
        } else {
            return new String(content, getElementCharset());
        }
    }

    @Override
    public RbelContentTreeNode findMessage() {
        RbelContentTreeNode position = this;
        while (position.getParentNode() != null) {
            position = position.getParentNode();
        }
        return position;
    }

    @Override
    public <T extends RbelFacet> boolean hasFacet(Class<T> clazz) {
        return false;
    }

    public Optional<RbelContentTreeNode> childNode(String nodeKey) {
        return Optional.ofNullable(childNodes.get(nodeKey));
    }

    @Override
    public Charset getElementCharset() {
        return Optional.ofNullable(charset)
            .orElse(StandardCharsets.UTF_8);
    }

    @Override
    public Optional<String> getKey() {
        return Optional.ofNullable(key);
    }

    private static RbelContentTreeNode castToRbelContentTreeNode(RbelContent rbelContent) {
        if(rbelContent instanceof RbelContentTreeNode asRbelContentTreeNode) {
            return asRbelContentTreeNode;
        }
        else {
            throw new ClassCastException("RbelPath was attempted to illegally be casted to RbelContentTreeNode.");
        }
    }

    private static class RbelPathNotUniqueException extends RuntimeException {

        public RbelPathNotUniqueException(String s) {
            super(s);
        }
    }
}
