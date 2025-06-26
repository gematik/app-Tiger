/*
 *
 * Copyright 2021-2025 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.rbellogger.builder;

import de.gematik.rbellogger.RbelConverter;
import de.gematik.rbellogger.RbelLogger;
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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class RbelBuilder {

  private static RbelLogger rbelLogger;
  private static RbelWriter rbelWriter;

  private RbelContentTreeNode treeRootNode;

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
   * @param rbelPath path where RbelContentTreeNode is inserted
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
        parentNode.addOrReplaceChild(newKey, newXmlNode.findElement("$." + newKey).orElseThrow());
      } else {
        RbelContentTreeNode newValueNode = getContentTreeNodeFromString(newValue);
        parentNode.addOrReplaceChild(newKey, newValueNode);
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
    if (!isJsonObject(newValue)
        && entry.getParentNode() != null
        && entry.getParentNode().getType() == RbelContentType.JSON) {
      RbelContentTreeNode parentNode = entry.getParentNode();
      String wrappedContent = wrapJsonContentInKey(entry.getKey().orElseThrow(), newValue);
      RbelContentTreeNode modifiedRbelContentTreeNode =
          getContentTreeNodeFromString(wrappedContent);
      if (parentNode.getKey().isPresent()) {
        parentNode.addOrReplaceChild(
            entry.getKey().orElseThrow(), modifiedRbelContentTreeNode.getChildNodes().get(0));
      } else {
        modifiedRbelContentTreeNode.getChildNodesWithKey().stream()
            .forEach(
                childNode ->
                    parentNode.addOrReplaceChild(childNode.getKey(), childNode.getValue()));
      }
    } else {
      RbelContentTreeNode newContentTreeNode = getContentTreeNodeFromString(newValue);
      Optional<String> key = entry.getKey();
      if (key.isPresent()) {
        entry.getParentNode().addOrReplaceChild(key.get(), newContentTreeNode);
      } else {
        this.treeRootNode = newContentTreeNode;
      }
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

  private static String wrapJsonContentInKey(String key, String content) {
    if (isJsonObject(content) || isJsonArray(content)) {
      return String.format("{ \"%s\": %s }", key, content);
    } else {
      return String.format("{ \"%s\": \"%s\" }", key, content);
    }
  }

  private static synchronized void assureRbelIsInitialized() {
    if (rbelLogger == null) {
      rbelLogger = RbelLogger.build();
      rbelWriter = new RbelWriter(rbelLogger.getRbelConverter());
    }
  }

  private static boolean isJsonObject(String json) {
    try {
      new JSONObject(json);
      return true;
    } catch (JSONException e) {
      return false;
    }
  }

  private static boolean isJsonArray(String json) {
    try {
      new JSONObject(json);
    } catch (JSONException e) {
      try {
        new JSONArray(json);
      } catch (JSONException ne) {
        return false;
      }
    }
    return true;
  }
}
