/*
 * Copyright 2024 gematik GmbH
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
 */

package de.gematik.rbellogger.data;

import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.data.facet.*;
import de.gematik.rbellogger.data.util.RbelElementTreePrinter;
import de.gematik.rbellogger.util.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@SuppressWarnings("unchecked")
@Getter
@Slf4j
public class RbelElement extends RbelPathAble {

  static {
    RbelJexlExecutor.initialize();
  }

  private final String uuid;
  private final RbelContent content;

  private final RbelElement parentNode;
  private final Queue<RbelFacet> facets = new ConcurrentLinkedQueue<>();
  @Setter private Optional<Charset> charset;

  private final long size;

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
    return new RbelElement(rawValue, parentNode).addFacet(new RbelValueFacet<>(value));
  }

  public static RbelElement wrap(@NonNull RbelElement parentNode, Object value) {
    return new RbelElement(value.toString().getBytes(parentNode.getElementCharset()), parentNode)
        .addFacet(new RbelValueFacet<>(value));
  }

  public <T> Optional<T> getFacet(@NonNull Class<T> clazz) {
    return getFacetStream()
        .filter(facet -> clazz.isAssignableFrom(facet.getClass()))
        .map(clazz::cast)
        .findFirst();
  }

  public boolean hasFacet(Class<? extends RbelFacet> clazz) {
    return getFacet(clazz).isPresent();
  }

  public RbelElement addFacet(RbelFacet facet) {
    facets.add(facet);
    return this;
  }

  @Override
  public List<RbelElement> getChildNodes() {
    return getFacetStream()
        .map(RbelFacet::getChildElements)
        .map(RbelMultiMap::getValues)
        .flatMap(Collection::stream)
        .map(Map.Entry::getValue)
        .filter(Objects::nonNull)
        .toList();
  }

  private Stream<RbelFacet> getFacetStream() {
    return new ArrayList<>(facets).stream();
  }

  @Override
  public RbelMultiMap<RbelElement> getChildNodesWithKey() {
    return getFacetStream()
        .map(RbelFacet::getChildElements)
        .map(RbelMultiMap::getValues)
        .flatMap(Collection::stream)
        .filter(el -> el.getValue() != null)
        .collect(RbelMultiMap.COLLECTOR);
  }

  public void triggerPostConversionListener(final RbelConverter context) {
    for (RbelElement element : getChildNodes()) {
      element.triggerPostConversionListener(context);
    }
    context.triggerPostConversionListenerFor(this);
  }

  public List<RbelElement> traverseAndReturnNestedMembers() {
    return getChildNodes().stream()
        .map(RbelElement::traverseAndReturnNestedMembersInternal)
        .flatMap(List::stream)
        .toList();
  }

  private List<RbelElement> traverseAndReturnNestedMembersInternal() {
    if (log.isTraceEnabled()) {
      log.trace(
          "Traversing into {}: facets are {}",
          findNodePath(),
          getFacets().stream().map(Object::getClass).map(Class::getSimpleName).toList());
    }
    if (hasFacet(RbelRootFacet.class)) {
      return List.of(this);
    } else {
      return traverseAndReturnNestedMembers();
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

  public Optional<String> findKeyInParentElement() {
    return Optional.of(this).map(RbelElement::getParentNode).stream()
        .flatMap(parent -> parent.getChildNodesWithKey().stream())
        .filter(e -> e.getValue() == this)
        .map(Map.Entry::getKey)
        .findFirst();
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
      return new String(getRawContent(), getElementCharset());
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
    return "["
        + getClass().getSimpleName()
        + "("
        + uuid
        + ")"
        + " at $."
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
    return getFacet(RbelValueFacet.class).map(RbelValueFacet::getValue).map(Object::toString);
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
    for (Map.Entry<String, RbelElement> ptr : parentNode.getChildNodesWithKey().getValues()) {
      if (ptr.getValue() == this) {
        return Optional.ofNullable(ptr.getKey());
      }
    }
    throw new RbelException("Unable to find key for element " + this);
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
    return getFacetStream()
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

  public String printHttpDescription() {
    return getFacet(RbelHttpRequestFacet.class)
            .map(
                req ->
                    "HTTP " + req.getMethod().getRawStringContent() + " " + req.getPathAsString())
            .orElse("")
        + getFacet(RbelHttpResponseFacet.class)
            .map(req -> "HTTP " + req.getResponseCode().getRawStringContent())
            .orElse("")
        + getFacet(RbelHttpMessageFacet.class)
            .map(
                msg ->
                    " with body '"
                        + StringUtils.abbreviate(msg.getBody().getRawStringContent(), 30)
                        + "'")
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

  private static class RbelPathNotUniqueException extends RuntimeException {

    public RbelPathNotUniqueException(String s) {
      super(s);
    }
  }

  public static List<RbelElement> findAllNestedElementsWithFacet(
      RbelElement el, Class<? extends RbelFacet> rbelFacetClass) {
    List<RbelElement> result = new ArrayList<>();
    if (el.hasFacet(rbelFacetClass)) {
      result.add(el);
    }
    el.getChildNodes()
        .forEach(child -> result.addAll(findAllNestedElementsWithFacet(child, rbelFacetClass)));

    return result;
  }
}
