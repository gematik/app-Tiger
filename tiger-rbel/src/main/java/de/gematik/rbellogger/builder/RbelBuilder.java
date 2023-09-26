package de.gematik.rbellogger.builder;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.writer.RbelContentTreeConverter;
import de.gematik.rbellogger.writer.RbelWriter;
import de.gematik.rbellogger.writer.tree.RbelContentTreeNode;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.jexl.TigerJexlContext;
import lombok.SneakyThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class RbelBuilder {

    private static RbelLogger rbelLogger;
    private static RbelWriter rbelWriter;

    private final RbelContentTreeNode treeRootNode;

    /**
     * Builder that builds and modifies a RbelContentTreeNode from various sources
     * @param treeRootNode initial treeRootNode
     */
    public RbelBuilder(RbelContentTreeNode treeRootNode) {
        this.treeRootNode = treeRootNode;
    }

    /**
     * Initializes a {@link RbelBuilder} with an object from a given file
     * @param pathName file path of imported object
     * @return the {@link RbelBuilder}
     */
    @SneakyThrows
    public static RbelBuilder fromFile(String pathName) {
        String fileContent = Files.readString(Paths.get(String.valueOf(Path.of(pathName))));
        final String resolvedInput = TigerGlobalConfiguration.resolvePlaceholders(fileContent);
        return fromString(resolvedInput);
    }

    /**
     * Initializes a {@link RbelBuilder}; the first direct child gets its key from the objectName parameter and its content from an object from a given file
     * @param pathName file path of imported object
     * @param objectName key of direct child
     * @return the {@link RbelBuilder}
     */
    @SneakyThrows
    public static RbelBuilder fromFile(String objectName, String pathName) {
        RbelBuilder contentBuilder = fromFile(pathName);
        return fromRbel(objectName, contentBuilder.getTreeRootNode());
    }

    /**
     * Initializes an empty {@link RbelBuilder}
     * @return the {@link RbelBuilder}
     */
    @SneakyThrows
    public static RbelBuilder fromScratch() {
        return fromString("");
    }

    /**
     * reads a formatted String and creates a new {@link RbelBuilder} using the content as its treeRootNode
     * @param content formatted String
     * @return RbelBuilder
     */
    @SneakyThrows
    public static RbelBuilder fromString(String content) {
        RbelContentTreeNode treeRootNode = getContentTreeNodeFromString(content);
        return new RbelBuilder(treeRootNode);
    }

    public RbelContentTreeNode getTreeRootNode() {
        return treeRootNode;
    }

    /**
     * Sets the value at a specific path to a new RbelContentTreeNode
     * @param rbelPath path where RbelContenTreeNode is inserted
     * @param newValue object as formatted String
     */
    public void setObjectAt(String rbelPath, String newValue) {
        var entry = this.treeRootNode.findElement(rbelPath).orElseThrow();
        var newContentTreeNode = getContentTreeNodeFromString(newValue);
        Optional<String> key = entry.getKey();
        if(key.isPresent()) {
            entry.getParentNode().setChildNode(key.get(), newContentTreeNode);
        }
        else {
            throw new NullPointerException("The key of the node which is to be changed is not set in its parent node.");
        }
    }

    /**
     * Sets a String value at a given path
     * @param rbelPath given path
     * @param newValue new String value
     */
    public void setValueAt(String rbelPath, String newValue) {
        var entry = this.treeRootNode.findElement(rbelPath).orElseThrow();
        entry.setContent(newValue.getBytes());
    }

    /**
     * Serializes the treeRootNode into a formatted String
     * @return the formatted String
     */
    public String serialize() {
        return getRbelWriter().serialize(this.treeRootNode, new TigerJexlContext()).getContentAsString();
    }

    private static RbelBuilder fromRbel(String name, RbelContentTreeNode content) {
        RbelMultiMap<RbelContentTreeNode> childNodes = new RbelMultiMap<>();
        childNodes.put(name, content);
        var contentTreeNode = new RbelContentTreeNode(childNodes, null);
        content.setKey(name);
        contentTreeNode.setCharset(content.getElementCharset());
        contentTreeNode.setType(content.getType());
        return new RbelBuilder(contentTreeNode);
    }

    private static RbelConverter getRbelConverter() {
        assureRbelIsInitialized();
        return rbelLogger.getRbelConverter();
    }

    private static RbelWriter getRbelWriter() {
        assureRbelIsInitialized();
        return rbelWriter;
    }

    private static RbelContentTreeNode getContentTreeNodeFromString(String content) {
        final RbelElement input = getRbelConverter().convertElement(content, null);
        return new RbelContentTreeConverter(input, new TigerJexlContext()).convertToContentTree();
    }

    private static void assureRbelIsInitialized() {
        if (rbelLogger == null) {
            rbelLogger = RbelLogger.build();
        }
        if(rbelWriter == null) {
            rbelWriter = new RbelWriter(rbelLogger.getRbelConverter());
        }
    }
}
