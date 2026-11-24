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
package de.gematik.rbellogger.data;

import de.gematik.rbellogger.RbelConversionPhase;
import de.gematik.rbellogger.data.core.*;
import de.gematik.rbellogger.data.util.RbelElementTreePrinter;
import de.gematik.rbellogger.facets.jackson.RbelCborFacet;
import de.gematik.rbellogger.facets.jackson.RbelJsonFacet;
import de.gematik.rbellogger.util.*;
import de.gematik.test.tiger.common.util.ObservableQueue;
import de.gematik.test.tiger.common.util.RecursiveTreeIterator;
import de.gematik.test.tiger.exceptions.GenericTigerException;
import java.lang.ref.WeakReference;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.bouncycastle.util.encoders.Hex;
import org.jetbrains.annotations.NotNull;

@Getter
@Slf4j
public class RbelElement extends RbelPathAble<RbelElement> {

  static {
    RbelJexlExecutor.initialize();
  }

  private static final AtomicReferenceFieldUpdater<RbelElement, String> UUID_FIELD =
      AtomicReferenceFieldUpdater.newUpdater(RbelElement.class, String.class, "uuid");

  private volatile String uuid;
  public Optional<Long> sequenceNumber = Optional.empty();
  private final RbelContent content;
  private WeakReference<Triple<String, Charset, Integer>> rawStringContent;

  private final RbelElement parentNode;
  private final Queue<RbelFacet> facets = new ObservableQueue<>(this::triggerFacetMetadataUpdate);
  @Setter private Optional<Charset> charset;
  @Setter private RbelConversionPhase conversionPhase = RbelConversionPhase.UNPARSED;

  private long size;
  @Setter private long conversionTimeInNanos = 0;

  // only the root of a tree is ever listened to, so the others never create the list
  @Getter(AccessLevel.NONE)
  private CopyOnWriteArrayList<Runnable> facetMetadataUpdateListeners;

  public synchronized void addFacetMetadataUpdateListener(Runnable listener) {
    if (facetMetadataUpdateListeners == null) {
      facetMetadataUpdateListeners = new CopyOnWriteArrayList<>();
    }
    facetMetadataUpdateListeners.add(listener);
  }

  public synchronized void removeFacetMetadataUpdateListener(Runnable listener) {
    if (facetMetadataUpdateListeners != null) {
      facetMetadataUpdateListeners.remove(listener);
    }
  }

  public void triggerFacetMetadataUpdate() {
    if (parentNode != null) {
      parentNode.triggerFacetMetadataUpdate();
      return;
    }
    final List<Runnable> listeners;
    synchronized (this) {
      listeners = facetMetadataUpdateListeners;
    }
    if (listeners != null) {
      listeners.forEach(Runnable::run);
    }
  }

  public byte[] getRawContent() {
    return content.isNull() ? null : content.toByteArray();
  }

  public RbelElement() {
    this(null, null);
  }

  public RbelElement(RbelElement parentNode) {
    this(null, parentNode);
  }

  public RbelElement(byte[] rawContent, RbelElement parentNode) {
    this(null, rawContent, parentNode, Optional.empty());
  }

  public RbelElement(RbelContent rawContent) {
    this(null, rawContent, null, Optional.empty());
  }

  public RbelElement(byte[] rawContent, RbelElement parentNode, Optional<Charset> charset) {
    this(null, rawContent, parentNode, charset);
  }

  public RbelElement(
      String uuid, byte[] rawContent, RbelElement parentNode, Optional<Charset> charset) {
    this(uuid, RbelContent.of(rawContent), parentNode, charset);
  }

  public static RbelElement create(RbelContent content, RbelElement parentNode) {
    return new RbelElement(null, content, parentNode, Optional.empty());
  }

  public RbelElement(
      @Nullable String uuid,
      RbelContent content,
      RbelElement parentNode,
      Optional<Charset> charset) {
    this.uuid = StringUtils.isNotEmpty(uuid) ? uuid : null;
    this.content = content;
    this.parentNode = parentNode;
    this.charset = Objects.requireNonNullElseGet(charset, Optional::empty);
    this.size = content.size();
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Only a message has a uuid that anybody needs, so the others get theirs when asked. */
  public String getUuid() {
    String result = uuid;
    if (result == null) {
      UUID_FIELD.compareAndSet(this, null, UUID.randomUUID().toString());
      result = uuid;
    }
    return result;
  }

  public Builder toBuilder() {
    return new Builder()
        .uuid(getUuid())
        .content(content)
        .parentNode(parentNode)
        .charset(charset.orElse(null));
  }

  public Optional<Long> getSequenceNumber() {
    if (parentNode != null) {
      return parentNode.getSequenceNumber();
    }
    return sequenceNumber;
  }

  public void setSequenceNumber(Long sequenceNumber) {
    this.sequenceNumber = Optional.of(sequenceNumber);
  }

  public void setUsedBytes(int usedBytes) {
    content.truncate(usedBytes);
    this.size = usedBytes;
  }

  public int getDepth() {
    int depth = 1;
    RbelElement current = this;
    while ((current = current.getParentNode()) != null) {
      depth++;
    }
    return depth;
  }

  public static class Builder {
    String uuid;
    RbelContent content = RbelContent.builder().build();
    RbelElement parentNode;
    Optional<Charset> charset;

    public Builder uuid(String uuid) {
      this.uuid = uuid;
      return this;
    }

    public Builder rawContent(byte[] rawContent) {
      if (rawContent != null) {
        this.content = RbelContent.of(rawContent);
      } else {
        this.content = RbelContent.builder().build();
      }
      return this;
    }

    public Builder content(RbelContent content) {
      if (content != null) {
        this.content = content;
      } else {
        this.content = RbelContent.builder().build();
      }
      return this;
    }

    public Builder parentNode(RbelElement parentNode) {
      this.parentNode = parentNode;
      return this;
    }

    public Builder charset(Charset charset) {
      this.charset = Optional.ofNullable(charset);
      return this;
    }

    public Builder charset(Optional<Charset> charset) {
      this.charset = charset;
      return this;
    }

    public RbelElement build() {
      return new RbelElement(uuid, content, parentNode, charset);
    }
  }

  public static RbelElement wrap(byte[] rawValue, @NonNull RbelElement parentNode, Object value) {
    return new RbelElement(rawValue, parentNode).addFacet(RbelValueFacet.of(value));
  }

  public static RbelElement wrap(@NonNull RbelElement parentNode, Object value) {
    return new RbelElement(value.toString().getBytes(parentNode.getElementCharset()), parentNode)
        .addFacet(RbelValueFacet.of(value));
  }

  public <T> Optional<T> getFacet(@NonNull Class<T> clazz) {
    for (val facet : facets) {
      if (clazz.isAssignableFrom(facet.getClass())) {
        return Optional.of(clazz.cast(facet));
      }
    }
    return Optional.empty();
  }

  public boolean hasFacet(Class<? extends RbelFacet> clazz) {
    // please do not convert into for-each: highly performance critical method both for rendering
    // and conversion.
    // for-each is not massively slower, BUT IT IS! leave as is if you don't do performance
    // verification.
    for (Iterator<RbelFacet> iter = facets.iterator(); iter.hasNext(); ) {
      var entry = iter.next();
      if (entry != null && clazz.isAssignableFrom(entry.getClass())) {
        return true;
      }
    }
    return false;
  }

  public RbelElement addFacet(RbelFacet facet) {
    facets.add(facet);
    return this;
  }

  @Override
  public @NotNull Stream<Entry<String, RbelElement>> getChildNodesWithKeyStream() {
    return facets.stream()
        .flatMap(facet -> facet.getChildElements().stream())
        .filter(e -> e.getValue() != null);
  }

  @Override
  public List<RbelElement> getChildNodes() {
    List<RbelElement> children = null;
    for (RbelFacet facet : facets) {
      for (Entry<String, RbelElement> child : facet.getChildElements().entries()) {
        if (child.getValue() != null) {
          if (children == null) {
            children = new ArrayList<>();
          }
          children.add(child.getValue());
        }
      }
    }
    return children == null ? List.of() : Collections.unmodifiableList(children);
  }

  public Stream<RbelElement> traverseNestedMembers() {
    return new RecursiveTreeIterator<RbelElement>(
            getChildNodes().iterator(),
            el -> {
              log.atTrace()
                  .addArgument(el::findNodePath)
                  .addArgument(
                      () ->
                          el.facets.stream()
                              .map(Object::getClass)
                              .map(Class::getSimpleName)
                              .toList())
                  .log("Traversing into {}: facets are {}");
              return el.hasFacet(RbelRootFacet.class)
                  ? Collections.emptyIterator()
                  : el.getChildNodes().iterator();
            })
        .stream().filter(el -> el.hasFacet(RbelRootFacet.class));
  }

  public List<RbelElement> traverseAndReturnNestedMembers() {
    return traverseNestedMembers().toList();
  }

  @Override
  public Optional<RbelElement> getFirst(String key) {
    return facets.stream()
        .map(facet -> facet.getChildElements().get(key))
        .filter(Objects::nonNull)
        .findFirst();
  }

  public Optional<RbelElement> getFirstIgnoringCase(String key) {
    return getChildNodesWithKeyStream()
        .filter(entry -> entry.getKey().equalsIgnoreCase(key))
        .map(Map.Entry::getValue)
        .findFirst();
  }

  @Override
  public List<RbelElement> getAll(String key) {
    return facets.stream()
        .flatMap(facet -> facet.getChildElements().getAll(key).stream())
        .filter(Objects::nonNull)
        .toList();
  }

  /**
   * What {@code findRbelPathMembers("$..<key>")} finds, in the same order: the descendants in
   * document order, each with the children it has under the key, and this element's own last. It is
   * found by walking the tree once instead of evaluating the RbelPath step by step on a list of all
   * nodes.
   */
  public List<RbelElement> findAllInTreeWithKey(String key) {
    return findAllInTreeWithKey(key, element -> true);
  }

  /**
   * Like {@link #findAllInTreeWithKey(String)}, but the walk only enters the subtree of an element
   * if {@code descendInto} accepts it. The element itself is still looked at.
   */
  public List<RbelElement> findAllInTreeWithKey(String key, Predicate<RbelElement> descendInto) {
    final Set<RbelElement> found = new LinkedHashSet<>();
    RecursiveTreeIterator.forEachInPreOrder(
        this,
        (node, addChild) -> {
          if (node == this) {
            node.forEachChild((childKey, child) -> addChild.accept(child));
          } else if (descendInto.test(node)) {
            node.forEachChild(
                (childKey, child) -> {
                  collectIfKeyMatches(key, childKey, child, found);
                  addChild.accept(child);
                });
          }
        },
        node -> {});
    forEachChild((childKey, child) -> collectIfKeyMatches(key, childKey, child, found));
    return found.stream().filter(RbelElement::shouldElementBeKeptInFinalResult).toList();
  }

  private static void collectIfKeyMatches(
      String key, String childKey, RbelElement child, Set<RbelElement> found) {
    if (childKey.equals(key)) {
      found.addAll(child.descendToContentNodeIfAdvised());
    }
  }

  /** Hands over each child with its key without building a list of them. */
  private void forEachChild(BiConsumer<String, RbelElement> action) {
    for (RbelFacet facet : facets) {
      for (Entry<String, RbelElement> child : facet.getChildElements().entries()) {
        if (child.getValue() != null) {
          action.accept(child.getKey(), child.getValue());
        }
      }
    }
  }

  @Override
  public List<RbelElement> findRbelPathMembers(String rbelPath) {
    return new RbelPathExecutor<>(this, rbelPath).execute();
  }

  @Override
  @Nullable
  public String getRawStringContent() {
    if (content.isNull()) {
      return null;
    } else {
      synchronized (content) {
        Triple<String, Charset, Integer> cachedValue =
            rawStringContent == null ? null : rawStringContent.get();
        var elementCharset = getElementCharset();
        if (cachedValue == null
            || !elementCharset.equals(cachedValue.getMiddle())
            || cachedValue.getRight() != content.size()) {
          final byte[] rawContent = getRawContent();
          cachedValue =
              Triple.of(new String(rawContent, elementCharset), elementCharset, rawContent.length);
          rawStringContent = new WeakReference<>(cachedValue);
        }
        return cachedValue.getLeft();
      }
    }
  }

  public Charset getElementCharset() {
    return charset
        .or(() -> Optional.ofNullable(parentNode).map(RbelElement::getElementCharset))
        .orElse(StandardCharsets.UTF_8);
  }

  public <T extends RbelFacet> T getFacetOrFail(Class<T> facetClass) {
    return getFacet(facetClass)
        .orElseThrow(() -> new RbelException("Facet not found: " + facetClass.getSimpleName()));
  }

  @Override
  public String toString() {
    return "[RbelElement ("
        + getUuid()
        + ") with "
        + Optional.ofNullable(content)
            .map(RbelContent::size)
            .map(FileUtils::byteCountToDisplaySize)
            .orElse("null")
        + " bytes at $."
        + findNodePath()
        + " with facets "
        + facets.stream()
            .map(Object::getClass)
            .map(Class::getSimpleName)
            .collect(Collectors.joining(","))
        + "]";
  }

  public Optional<Object> seekValue() {
    return getFacet(RbelValueFacet.class).map(RbelValueFacet::getValue);
  }

  public Optional<String> printValue() {
    return getFacet(RbelValueFacet.class)
        .map(RbelValueFacet::getValue)
        .map(value -> value instanceof byte[] ar ? Hex.toHexString(ar) : value.toString());
  }

  public <T> Optional<T> seekValue(Class<T> clazz) {
    return getFacet(RbelValueFacet.class)
        .map(RbelValueFacet::getValue)
        .filter(clazz::isInstance)
        .map(clazz::cast);
  }

  @Override
  public Optional<String> getKey() {
    if (parentNode == null) {
      return Optional.empty();
    }
    return findKeyInParentElement()
        .map(Optional::of)
        .orElseThrow(() -> new RbelException("Unable to find key for element " + this));
  }

  public void addOrReplaceFacet(RbelFacet facet) {
    getFacet(facet.getClass()).ifPresent(this::dropFacetInstance);
    facets.add(facet);
  }

  public void removeFacetsOfType(Class<? extends RbelFacet> facetClass) {
    final List<RbelFacet> facetsToBeRemoved =
        facets.stream().filter(facetClass::isInstance).toList();
    facetsToBeRemoved.forEach(this::dropFacetInstance);
    facetsToBeRemoved.forEach(facet -> facet.facetRemovedCallback(this));
  }

  public void removeFacet(RbelFacet facet) {
    dropFacetInstance(facet);
    facet.facetRemovedCallback(this);
  }

  /**
   * Identity-based on purpose: facets compare by content, so an element can hold two that are equal
   * but distinct, and {@code Queue#remove} would drop the first rather than the one passed in.
   */
  private void dropFacetInstance(RbelFacet facet) {
    facets.removeIf(candidate -> candidate == facet);
  }

  public Optional<RbelElement> findElement(String rbelPath) {
    final List<RbelElement> resultList = findRbelPathMembers(rbelPath);
    if (resultList.isEmpty()) {
      return Optional.empty();
    }
    if (resultList.size() == 1) {
      return Optional.of(resultList.get(0));
    }
    throw new RbelPathNotUniqueException(
        "RbelPath '"
            + rbelPath
            + "' is not unique! Found "
            + resultList.size()
            + " elements, expected only one!");
  }

  public String printTreeStructureWithoutColors() {
    return RbelElementTreePrinter.builder().rootElement(this).printColors(false).build().execute();
  }

  public String printTreeStructure() {
    return RbelElementTreePrinter.builder().rootElement(this).build().execute();
  }

  public String printTreeStructure(int maximumLevels, boolean printKeys) {
    return RbelElementTreePrinter.builder()
        .rootElement(this)
        .printKeys(printKeys)
        .maximumLevels(maximumLevels)
        .build()
        .execute();
  }

  public List<RbelNoteFacet> getNotes() {
    return facets.stream()
        .flatMap(
            facet -> {
              if (facet instanceof RbelNestedFacet asRbelNestedFacet) {
                if (asRbelNestedFacet.getNestedElement().hasFacet(RbelRootFacet.class)) {
                  return Stream.empty();
                } else {
                  return asRbelNestedFacet.getNestedElement().getFacets().stream();
                }
              } else {
                return Stream.of(facet);
              }
            })
        .filter(RbelNoteFacet.class::isInstance)
        .map(RbelNoteFacet.class::cast)
        .toList();
  }

  public RbelElement findMessage() {
    RbelElement position = this;
    while (position.getParentNode() != null) {
      position = position.getParentNode();
    }
    return position;
  }

  @Override
  public List<RbelElement> descendToContentNodeIfAdvised() {
    if ((hasFacet(RbelJsonFacet.class) || hasFacet(RbelCborFacet.class))
        && hasFacet(RbelNestedFacet.class)) {
      return List.of(
          getFacet(RbelNestedFacet.class).map(RbelNestedFacet::getNestedElement).orElseThrow(),
          this);
    } else {
      return List.of(this);
    }
  }

  @Override
  public boolean shouldElementBeKeptInFinalResult() {
    return !((hasFacet(RbelJsonFacet.class) || hasFacet(RbelCborFacet.class))
        && hasFacet(RbelNestedFacet.class));
  }

  public String printShortDescription() {
    return facets.stream()
        .map(facet -> facet.printShortDescription(this))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .findFirst()
        .orElse("");
  }

  public RbelElement findRootElement() {
    RbelElement result = this;
    RbelElement newResult = result.getParentNode();
    while (newResult != null) {
      result = newResult;
      newResult = result.getParentNode();
    }
    return result;
  }

  private static class RbelPathNotUniqueException extends GenericTigerException {

    public RbelPathNotUniqueException(String s) {
      super(s);
    }
  }

  public <F extends RbelFacet> List<F> findAllNestedFacets(Class<F> rbelFacetClass) {
    return findAllNestedFacetsStream(rbelFacetClass).toList();
  }

  public <F extends RbelFacet> Stream<F> findAllNestedFacetsStream(Class<F> rbelFacetClass) {
    return getChildNodesStream()
        .flatMap(child -> findAllNestedElementsWithFacet(child, rbelFacetClass))
        .map(RbelElement::getFacets)
        .flatMap(Queue::stream)
        .filter(rbelFacetClass::isInstance)
        .map(rbelFacetClass::cast);
  }

  private static Stream<RbelElement> findAllNestedElementsWithFacet(
      RbelElement target, Class<? extends RbelFacet> rbelFacetClass) {
    return new RecursiveTreeIterator<>(target, e -> e.getChildNodes().iterator())
        .stream().filter(e -> e.hasFacet(rbelFacetClass));
  }

  public RbelConversionPhase getConversionPhase() {
    if (parentNode == null) {
      return conversionPhase;
    } else {
      return parentNode.getConversionPhase();
    }
  }
}
