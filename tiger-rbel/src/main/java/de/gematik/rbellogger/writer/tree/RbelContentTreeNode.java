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
package de.gematik.rbellogger.writer.tree;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.util.RbelPathAble;
import de.gematik.rbellogger.util.RbelPathExecutor;
import de.gematik.rbellogger.writer.*;
import de.gematik.test.tiger.common.jexl.TigerJexlContext;
import de.gematik.test.tiger.exceptions.GenericTigerException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.tuple.Pair;

public class RbelContentTreeNode extends RbelPathAble {

  private RbelMultiMap<RbelContentTreeNode> childNodes;
  private final Map<String, String> attributeMap = new HashMap<>();
  @Setter private Charset charset;
  @Setter private String key;
  @Getter @Setter private RbelContentType type;
  @Setter private RbelContentTreeNode parentNode;
  @Getter @Setter private byte[] content;

  public RbelContentTreeNode(RbelMultiMap<RbelContentTreeNode> childNodes, byte[] content) {
    this.setContent(content);
    this.setupChildNodes(childNodes);
  }

  public void setRawStringContent(String newContent) {
    setContent(newContent.getBytes(charset != null ? charset : StandardCharsets.UTF_8));
  }

  public void setChildNodes(RbelMultiMap<RbelContentTreeNode> childNodes) {
    setupChildNodes(childNodes);
    updateAncestorContent();
  }

  public void setChildNodes(List<RbelContentTreeNode> childNodes) {
    setupChildNodes(childNodes);
    updateAncestorContent();
  }

  public void setupChildNodes(RbelMultiMap<RbelContentTreeNode> childNodes) {
    if (childNodes == null) {
      this.setChildNodes(new RbelMultiMap<>());
    } else {
      this.childNodes = childNodes;
      this.childNodes.getValues().forEach((e) -> e.getValue().setParentNode(this));
    }
  }

  public void setupChildNodes(List<RbelContentTreeNode> newChildNodes) {
    childNodes.clear();
    newChildNodes.forEach(
        node -> {
          childNodes.put(node.getKey().orElseThrow(), node);
          node.setParentNode(this);
        });
  }

  /**
   * For JSON objects it adds or replaces a unique entry; for all other types it adds the entry
   *
   * @param key key of entry to be added or replaced
   * @param newChildNode new childNode
   */
  public void addOrReplaceChild(String key, RbelContentTreeNode newChildNode) {
    addOrReplaceUniqueEntry(key, newChildNode);
    newChildNode.setParentNode(this);
    newChildNode.setKey(key);
    updateAncestorContent();
  }

  /**
   * Updates a value of a key that exists once; if multiple entries of the keys exist, then it
   * throws an {@link UnsupportedOperationException}; if the key does not exist yet, it adds a new
   * entry
   *
   * @param key key of entry to be replaced
   * @param newChildNode new node
   */
  private void addOrReplaceUniqueEntry(String key, RbelContentTreeNode newChildNode) {
    List<RbelContentTreeNode> matchingEntries = childNodes.getAll(key);
    if (matchingEntries.size() > 1) {
      throw new UnsupportedOperationException(
          "It was attempted to replace a unique key '%s', but multiple entries with that key existed."
              .formatted(key));
    } else if (matchingEntries.size() == 1) {
      childNodes =
          childNodes.stream()
              .map(e -> e.getKey().equals(key) ? Pair.of(key, newChildNode) : e)
              .collect(RbelMultiMap.COLLECTOR);
    } else {
      childNodes.put(key, newChildNode);
    }
  }

  public void addChild(RbelContentTreeNode newChildNode) {
    if (!isListTypeNode()) {
      throw new IllegalArgumentException(
          "The path specified must lead to an array or a list. Currently only JSON arrays are"
              + " supported.");
    }

    int newIndex = 0;
    String indexAsString = "0";
    while (childNodes.containsKey(indexAsString)) {
      newIndex++;
      indexAsString = Integer.toString(newIndex);
    }

    childNodes.put(indexAsString, newChildNode);
    newChildNode.setParentNode(this);
    newChildNode.setKey(indexAsString);
    updateAncestorContent();
  }

  public Map<String, String> attributes() {
    return attributeMap;
  }

  public Optional<Boolean> hasTypeOptional(RbelContentType typeToCheck) {
    return Optional.ofNullable(getType()).map(t -> t == typeToCheck);
  }

  @Override
  public RbelContentTreeNode getParentNode() {
    return parentNode;
  }

  public boolean isRootNode() {
    return parentNode == null;
  }

  @Override
  public Optional<RbelPathAble> getFirst(String key) {
    return getChildNodesWithKey().stream()
        .filter(entry -> entry.getKey().equals(key))
        .map(Map.Entry::getValue)
        .map(RbelPathAble.class::cast)
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
    return childNodes.getValues().stream().map(Map.Entry::getValue).toList();
  }

  @Override
  public RbelMultiMap<RbelContentTreeNode> getChildNodesWithKey() {
    return childNodes.getValues().stream().collect(RbelMultiMap.COLLECTOR);
  }

  public Optional<RbelContentTreeNode> findElement(String rbelPath) {
    final List<RbelContentTreeNode> resultList = findRbelPathMembers(rbelPath);
    if (resultList.isEmpty()) {
      return Optional.empty();
    }
    if (resultList.size() == 1) {
      return Optional.of(resultList.get(0));
    }
    throw new RbelContentTreeNode.RbelPathNotUniqueException(
        "RbelPath '"
            + rbelPath
            + "' is not unique! Found "
            + resultList.size()
            + " elements, expected only one!");
  }

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
              .map(Map.Entry::getKey)
              .findFirst());
      ptr.set(ptr.get().getParentNode());
    }
    return keyList.stream()
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.joining("."));
  }

  @Override
  public List<RbelContentTreeNode> findRbelPathMembers(String rbelPath) {
    return new RbelPathExecutor<>(this, rbelPath).execute();
  }

  public Optional<String> findKeyInParentElement() {
    return Optional.of(this).map(RbelContentTreeNode::getParentNode).stream()
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

  public Optional<RbelContentTreeNode> childNode(String nodeKey) {
    return Optional.ofNullable(childNodes.get(nodeKey));
  }

  public Charset getElementCharset() {
    return Optional.ofNullable(charset).orElse(StandardCharsets.UTF_8);
  }

  @Override
  public Optional<String> getKey() {
    return Optional.ofNullable(key);
  }

  private void updateAncestorContent() {
    updateContent();
    if (parentNode != null) {
      parentNode.updateAncestorContent();
    }
  }

  private void updateContent() {
    if (type == RbelContentType.XML) {
      var formerParent = this.parentNode;
      var formerKey = this.getKey();

      var asChildNodes = new RbelMultiMap<RbelContentTreeNode>();
      asChildNodes.put("root", this);
      asChildNodes.get("root").setKey("root");
      var tempRootNode = new RbelContentTreeNode(asChildNodes, new byte[0]);
      tempRootNode.setType(RbelContentType.XML);

      String serialization = executeSerialisation(tempRootNode, formerParent != null);

      if (formerKey.isPresent()) {
        serialization = serialization.replaceFirst("root", formerKey.get());
        serialization = replaceLast(serialization, "root", formerKey.get());
      } else {
        serialization = serialization.replaceFirst("<root>", "");
        serialization = replaceLast(serialization, "</root>", "");
      }

      RbelElement updatedRbelElement = new RbelElement(serialization.getBytes(), null);
      var updatedContentTreeNode =
          new RbelContentTreeConverter(updatedRbelElement, new TigerJexlContext())
              .convertToContentTree();

      this.content = updatedContentTreeNode.getContent();

      this.setParentNode(formerParent);

      if (formerKey.isPresent()) {
        this.setKey(formerKey.get());
        if (formerParent != null) {
          formerParent.getChildNodesWithKey().put(formerKey.get(), this);
        }
      } else {
        setKey(null);
      }
    } else {
      String serialization = executeSerialisation(this, parentNode != null);

      RbelElement updatedRbelElement = new RbelElement(serialization.getBytes(), null);
      var updatedContentTreeNode =
          new RbelContentTreeConverter(updatedRbelElement, new TigerJexlContext())
              .convertToContentTree();

      this.content = updatedContentTreeNode.getContent();
    }
  }

  private boolean isListTypeNode() {
    return attributeMap.containsKey(RbelJsonElementToNodeConverter.JSON_ARRAY);
  }

  private static class RbelPathNotUniqueException extends GenericTigerException {

    public RbelPathNotUniqueException(String s) {
      super(s);
    }
  }

  private String replaceLast(String stringToModify, String replacedString, String replacement) {
    var lastIndex = stringToModify.lastIndexOf(replacedString);
    var firstPart = stringToModify.substring(0, lastIndex);
    var lastPart = stringToModify.substring(lastIndex + replacedString.length());
    return firstPart + replacement + lastPart;
  }

  private String executeSerialisation(RbelContentTreeNode node, boolean parentNodePresent) {
    RbelSerializationResult serializationResult;
    if (parentNodePresent) {
      serializationResult =
          new RbelWriter(RbelLogger.build().getRbelConverter())
              .renderNode(node, new TigerJexlContext());
    } else {
      serializationResult =
          new RbelWriter(RbelLogger.build().getRbelConverter())
              .serialize(node, new TigerJexlContext());
    }

    if (serializationResult == null) {
      throw new RbelSerializationException("Error when updating RbelContentTreeNode.");
    }
    String serialization = serializationResult.getContentAsString();
    if (serialization == null) {
      throw new RbelSerializationException("Error when updating RbelContentTreeNode.");
    }
    return serialization;
  }
}
