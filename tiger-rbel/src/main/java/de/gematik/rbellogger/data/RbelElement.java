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
import de.gematik.test.tiger.exceptions.GenericTigerException;
import java.lang.ref.WeakReference;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.bouncycastle.util.encoders.Hex;

@Getter
@Slf4j
public class RbelElement extends RbelPathAble {

  static {
    RbelJexlExecutor.initialize();
  }

  private final String uuid;
  private final RbelContent content;
  private WeakReference<Triple<String, Charset, Integer>> rawStringContent =
      new WeakReference<>(null);

  private final RbelElement parentNode;
  private final Queue<RbelFacet> facets = new ConcurrentLinkedQueue<>();
  @Setter private Optional<Charset> charset;
  @Setter private RbelConversionPhase conversionPhase = RbelConversionPhase.UNPARSED;

  private long size;
  @Setter private long conversionTimeInNanos = 0;

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
    if (StringUtils.isNotEmpty(uuid)) {
      this.uuid = uuid;
    } else {
      this.uuid = UUID.randomUUID().toString();
    }
    this.content = content;
    this.parentNode = parentNode;
    this.charset = Objects.requireNonNullElseGet(charset, Optional::empty);
    this.size = content.size();
  }

  public static Builder builder() {
    return new Builder();
  }

  public Builder toBuilder() {
    return new Builder()
        .uuid(uuid)
        .content(content)
        .parentNode(parentNode)
        .charset(charset.orElse(null));
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
  public List<RbelElement> getChildNodes() {
    // please do not convert into for-each: highly performance critical method both for rendering
    // and conversion.
    // for-each is not massively slower, BUT IT IS! leave as is if you don't do performance
    // verification.
    val result = new LinkedList<RbelElement>();
    for (Iterator<RbelFacet> facetIterator = facets.iterator();
        facetIterator.hasNext(); ) { // NOSONAR
      var facet = facetIterator.next();
      for (Iterator<Entry<String, RbelElement>> childElementIterator =
              facet.getChildElements().iterator();
          childElementIterator.hasNext(); ) {
        final Entry<?, ?> next = childElementIterator.next();
        final RbelElement child = ((RbelElement) next.getValue());
        if (child != null) {
          result.add(child);
        }
      }
    }
    return result;
  }

  @Override
  public RbelMultiMap<RbelElement> getChildNodesWithKey() {
    // please do not convert into for-each: highly performance critical method both for rendering
    // and conversion.
    // for-each is not massively slower, BUT IT IS! leave as is if you don't do performance
    // verification.
    val result = new RbelMultiMap<RbelElement>();
    for (Iterator<RbelFacet> facetIterator = facets.iterator();
        facetIterator.hasNext(); ) { // NOSONAR
      val facet = facetIterator.next();
      for (Iterator<Entry<String, RbelElement>> childElementIterator =
              facet.getChildElements().iterator();
          childElementIterator.hasNext(); ) {
        final Entry<String, RbelElement> childElement = childElementIterator.next();
        if (childElement.getValue() != null) {
          result.put(childElement);
        }
      }
    }
    return result;
  }

  public List<RbelElement> traverseAndReturnNestedMembers() {
    val result = new ArrayList<RbelElement>();
    traverseAndReturnNestedMembers(result);
    return result;
  }

  public void traverseAndReturnNestedMembers(List<RbelElement> result) {
    // please do not convert into for-each: highly performance critical method both for rendering
    // and conversion.
    // for-each is not massively slower, BUT IT IS! leave as is if you don't do performance
    // verification.
    for (Iterator<RbelElement> iterator = getChildNodes().iterator(); iterator.hasNext(); ) {
      RbelElement element = iterator.next();
      if (element != null) {
        element.traverseAndReturnNestedMembersInternal(result);
      }
    }
  }

  private void traverseAndReturnNestedMembersInternal(List<RbelElement> result) {
    log.atTrace()
        .addArgument(this::findNodePath)
        .addArgument(() -> facets.stream().map(Object::getClass).map(Class::getSimpleName).toList())
        .log("Traversing into {}: facets are {}");
    if (hasFacet(RbelRootFacet.class)) {
      result.add(this);
    } else {
      traverseAndReturnNestedMembers(result);
    }
  }

  @Override
  public Optional<RbelElement> getFirst(String key) {
    return getChildNodesWithKey().stream()
        .filter(entry -> entry.getKey().equals(key))
        .map(Map.Entry::getValue)
        .findFirst();
  }

  @Override
  public List<RbelElement> getAll(String key) {
    return getChildNodesWithKey().stream()
        .filter(entry -> entry.getKey().equals(key))
        .map(Map.Entry::getValue)
        .toList();
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
        Triple<String, Charset, Integer> cachedValue = rawStringContent.get();
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
        + uuid
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
    getFacet(facet.getClass()).ifPresent(facets::remove);
    facets.add(facet);
  }

  public void removeFacetsOfType(Class<? extends RbelFacet> facetClass) {
    final List<RbelFacet> facetsToBeRemoved =
        facets.stream().filter(facetClass::isInstance).toList();
    facetsToBeRemoved.forEach(facets::remove);
    facetsToBeRemoved.forEach(facet -> facet.facetRemovedCallback(this));
  }

  public void removeFacet(RbelFacet facet) {
    facets.remove(facet);
    facet.facetRemovedCallback(this);
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
  public List<RbelPathAble> descendToContentNodeIfAdvised() {
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
    return getChildNodes().stream()
        .map(child -> findAllNestedElementsWithFacet(child, rbelFacetClass))
        .flatMap(List::stream)
        .map(RbelElement::getFacets)
        .flatMap(Queue::stream)
        .filter(rbelFacetClass::isInstance)
        .map(rbelFacetClass::cast)
        .toList();
  }

  private static List<RbelElement> findAllNestedElementsWithFacet(
      RbelElement target, Class<? extends RbelFacet> rbelFacetClass) {
    List<RbelElement> result = new ArrayList<>();
    if (target.hasFacet(rbelFacetClass)) {
      result.add(target);
    }
    target
        .getChildNodes()
        .forEach(child -> result.addAll(findAllNestedElementsWithFacet(child, rbelFacetClass)));

    return result;
  }

  public RbelConversionPhase getConversionPhase() {
    if (parentNode == null) {
      return conversionPhase;
    } else {
      return parentNode.getConversionPhase();
    }
  }
}
