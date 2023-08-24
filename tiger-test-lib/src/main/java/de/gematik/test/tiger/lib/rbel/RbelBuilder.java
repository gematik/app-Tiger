package de.gematik.test.tiger.lib.rbel;

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

public class RbelBuilder {

    private static RbelLogger rbelLogger;
    private static RbelWriter rbelWriter;
    private RbelContentTreeNode treeRootNode;

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
     * Initializes a {@link RbelBuilder}; the first direct child gets its key from the objectName parameter and its content from the content parameter
     * @param objectName key of direct child
     * @param content content of direct child; is converted to {@link RbelContentTreeNode}
     * @return the {@link RbelBuilder}
     */
    @SneakyThrows
    public static RbelBuilder fromString(String objectName, String content) {
        RbelBuilder contentBuilder = fromString(content);
        return RbelBuilder.fromRbel(objectName, contentBuilder.getTreeRootNode());
    }

    /**
     * Initializes an empty {@link RbelBuilder}
     * @return the {@link RbelBuilder}
     */
    @SneakyThrows
    public static RbelBuilder fromScratch() {
        return fromString("");
    }
    
    @SneakyThrows
    private static RbelBuilder fromString(String content) {
        final RbelElement input = getRbelConverter().convertElement(content, null);
        RbelContentTreeNode treeRootNode = new RbelContentTreeConverter(input, new TigerJexlContext()).convertToContentTree();
        return new RbelBuilder(treeRootNode);
    }

    private static RbelBuilder fromRbel(String name, RbelContentTreeNode content) {
        RbelMultiMap<RbelContentTreeNode> childNodes = new RbelMultiMap<>();
        childNodes.put(name, content);
        var contentTreeNode = new RbelContentTreeNode(childNodes);
        return new RbelBuilder(contentTreeNode);
    }

    public RbelContentTreeNode getTreeRootNode() {
        return treeRootNode;
    }



    private static RbelConverter getRbelConverter() {
        assureRbelIsInitialized();
        return rbelLogger.getRbelConverter();
    }

    private static RbelWriter getRbelWriter() {
        assureRbelIsInitialized();
        return rbelWriter;
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
