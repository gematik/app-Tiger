package de.gematik.rbellogger.builder;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.exceptions.RbelPathException;
import de.gematik.rbellogger.writer.RbelContentTreeConverter;
import de.gematik.rbellogger.writer.RbelContentType;
import de.gematik.rbellogger.writer.RbelWriter;
import de.gematik.rbellogger.writer.tree.RbelContentTreeNode;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.jexl.TigerJexlContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import lombok.SneakyThrows;

public class RbelBuilder {

  private static RbelLogger rbelLogger;
  private static RbelWriter rbelWriter;

  private final RbelContentTreeNode treeRootNode;

  /**
   * Builder that builds and modifies a RbelContentTreeNode from various sources
   *
   * @param treeRootNode initial treeRootNode
   */
  public RbelBuilder(RbelContentTreeNode treeRootNode) {
    this.treeRootNode = treeRootNode;
  }

  /**
   * Initializes a {@link RbelBuilder} with an object from a given file
   *
   * @param pathName file path of imported object
   * @return this
   */
  @SneakyThrows
  public static RbelBuilder fromFile(String pathName) {
    String fileContent = Files.readString(Paths.get(String.valueOf(Path.of(pathName))));
    final String resolvedInput = TigerGlobalConfiguration.resolvePlaceholders(fileContent);
    return fromString(resolvedInput);
  }

  /**
   * Initializes a {@link RbelBuilder}; the first direct child gets its key from the objectName
   * parameter and its content from an object from a given file
   *
   * @param pathName file path of imported object
   * @param objectName key of direct child
   * @return this
   */
  @SneakyThrows
  public static RbelBuilder fromFile(String objectName, String pathName) {
    RbelBuilder contentBuilder = fromFile(pathName);
    return fromRbel(objectName, contentBuilder.getTreeRootNode());
  }

  /**
   * Initializes an empty {@link RbelBuilder}
   *
   * @param type Rbel content type of treeNode
   * @return this
   */
  @SneakyThrows
  public static RbelBuilder fromScratch(RbelContentType type) {
    RbelContentTreeNode root = new RbelContentTreeNode(null, new byte[0]);
    root.setType(type);
    return new RbelBuilder(root);
  }

  /**
   * reads a formatted String and creates a new {@link RbelBuilder} using the content as its
   * treeRootNode
   *
   * @param content formatted String
   * @return this
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
   * Sets the value at a specific path to a new RbelContentTreeNode; the path or its parent must
   * exist
   *
   * @param rbelPath path where RbelContenTreeNode is inserted
   * @param newValue primitive String; or object as formatted String
   * @throws RbelPathException if path is not a proper Rbel path or if path and its parent do not
   *     exist
   * @return this
   */
  public RbelBuilder setValueAt(String rbelPath, String newValue) {
    Optional<RbelContentTreeNode> entryOptional = this.treeRootNode.findElement(rbelPath);
    if (entryOptional.isPresent()) {
      return setValueAt(entryOptional.get(), newValue);
    } else {
      ArrayList<String> steps = new ArrayList<>(Arrays.asList(rbelPath.split("\\.")));
      if (steps.isEmpty() || !steps.contains("$")) {
        throw new RbelPathException("RbelPath must at least contain '$' and one node");
      }
      RbelContentTreeNode parentNode;
      String newKey = steps.remove(steps.size() - 1);
      if (steps.size() == 1) {
        parentNode = treeRootNode;
      } else {
        String parentPath = String.join(".", steps);
        Optional<RbelContentTreeNode> parentOptional = this.treeRootNode.findElement(parentPath);
        if (parentOptional.isEmpty()) {
          throw new RbelPathException(
              "Neither the path '%s' nor its parent does exist.".formatted(rbelPath));
        }
        parentNode = parentOptional.get();
      }
      if (parentNode.getType() == RbelContentType.XML) {
        String newXmlValue = String.format("<%s>%s</%s>", newKey, newValue, newKey);
        RbelContentTreeNode newXmlNode = getContentTreeNodeFromString(newXmlValue);
        parentNode.setChildNode(
            newKey, newXmlNode.findElement("$.%s".formatted(newKey)).orElseThrow());
      } else {
        RbelContentTreeNode newValueNode = getContentTreeNodeFromString(newValue);
        parentNode.setChildNode(newKey, newValueNode);
      }
      return this;
    }
  }

  /**
   * Adds a new entry at a list or array at a specific path
   *
   * @param rbelPath path of array/list
   * @param newValue value to be added
   * @return this
   */
  public RbelBuilder addEntryAt(String rbelPath, String newValue) {
    RbelContentTreeNode entry = this.treeRootNode.findElement(rbelPath).orElseThrow();

    RbelContentTreeNode newChild = getContentTreeNodeFromString(newValue);
    entry.addChild(newChild);
    return this;
  }

  private RbelBuilder setValueAt(RbelContentTreeNode entry, String newValue) {
    RbelContentTreeNode newContentTreeNode = getContentTreeNodeFromString(newValue);
    Optional<String> key = entry.getKey();
    if (key.isPresent()) {
      entry.getParentNode().setChildNode(key.get(), newContentTreeNode);
    } else {
      throw new NullPointerException(
          "The key of the node which is to be changed is not set in its parent node.");
    }
    return this;
  }

  /**
   * Serializes the treeRootNode into a formatted String
   *
   * @return the formatted String
   */
  public String serialize() {
    return getRbelWriter()
        .serialize(this.treeRootNode, new TigerJexlContext())
        .getContentAsString();
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
    if (rbelWriter == null) {
      rbelWriter = new RbelWriter(rbelLogger.getRbelConverter());
    }
  }
}
