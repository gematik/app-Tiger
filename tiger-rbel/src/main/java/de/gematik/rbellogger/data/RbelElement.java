/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data;

import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.data.facet.*;
import de.gematik.rbellogger.data.util.RbelElementTreePrinter;
import de.gematik.rbellogger.util.RbelException;
import de.gematik.rbellogger.util.RbelPathExecutor;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;

@Getter
@Slf4j
public class RbelElement {

    private final String uuid;
    private final byte[] rawContent;
    private final transient RbelElement parentNode;
    private final List<RbelFacet> facets = new ArrayList<>();
    @Setter
    @Getter(AccessLevel.PRIVATE)
    @Builder.Default
    private Optional<Charset> charset = Optional.empty();

    private final long size;

    public RbelElement(byte[] rawContent, RbelElement parentNode) {
        this(null, rawContent, parentNode, Optional.empty());
    }

    public RbelElement(byte[] rawContent, RbelElement parentNode, Optional<Charset> charset) {
        this(null, rawContent, parentNode, charset);
    }

    @Builder(toBuilder = true)
    public RbelElement(@Nullable String uuid, byte[] rawContent, RbelElement parentNode,  Optional<Charset> charset) {
        if (StringUtils.isNotEmpty(uuid)) {
            this.uuid = uuid;
        } else {
            this.uuid = UUID.randomUUID().toString();
        }
        this.rawContent = rawContent;
        this.parentNode = parentNode;
        if (charset == null) {
            this.charset = Optional.empty();
        } else {
            this.charset = charset;
        }
        if (rawContent != null) {
            this.size = rawContent.length;
        } else {
            this.size = 0L;
        }
    }

    public static RbelElement wrap(byte[] rawValue, @NonNull RbelElement parentNode, Object value) {
        return new RbelElement(rawValue, parentNode)
            .addFacet(new RbelValueFacet<>(value));
    }

    public static RbelElement wrap(@NonNull RbelElement parentNode, Object value) {
        return new RbelElement(value.toString().getBytes(parentNode.getElementCharset()), parentNode)
            .addFacet(new RbelValueFacet<>(value));
    }

    public <T> Optional<T> getFacet(@NonNull Class<T> clazz) {
        return Collections.unmodifiableList(facets).stream()
            .filter(facet -> clazz.isAssignableFrom(facet.getClass()))
            .map(clazz::cast)
            .findFirst();
    }

    public <T extends RbelFacet> boolean hasFacet(Class<T> clazz) {
        return getFacet(clazz).isPresent();
    }

    public RbelElement addFacet(RbelFacet facet) {
        synchronized (facets) {
            facets.add(facet);
        }
        return this;
    }

    public List<RbelElement> getChildNodes() {
        return Collections.unmodifiableList(facets).stream()
            .map(RbelFacet::getChildElements)
            .map(RbelMultiMap::getValues)
            .flatMap(Collection::stream)
            .map(Map.Entry::getValue)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    public RbelMultiMap getChildNodesWithKey() {
        return Collections.unmodifiableList(facets).stream()
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
            .collect(Collectors.toList());
    }

    // Yes, default-visibility (is called recursively)
    List<RbelElement> traverseAndReturnNestedMembersInternal() {
        log.trace("Traversing into {}: facets are {}", findNodePath(), getFacets().stream()
            .map(Object::getClass).map(Class::getSimpleName).collect(Collectors.toList()));
        if (hasFacet(RbelRootFacet.class)) {
            return List.of(this);
        } else {
            return getChildNodes().stream()
                .map(RbelElement::traverseAndReturnNestedMembersInternal)
                .flatMap(List::stream)
                .collect(Collectors.toList());
        }
    }

    public boolean isStructuralHelperElement() {
        return false;
    }

    public String findNodePath() {
        LinkedList<Optional<String>> keyList = new LinkedList<>();
        final AtomicReference<RbelElement> ptr = new AtomicReference(this);
        while (!(ptr.get().getParentNode() == null)) {
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

    public Optional<RbelElement> getFirst(String key) {
        return getChildNodesWithKey().stream()
            .filter(entry -> entry.getKey().equals(key))
            .map(Map.Entry::getValue)
            .findFirst();
    }

    public List<RbelElement> getAll(String key) {
        return getChildNodesWithKey().stream()
            .filter(entry -> entry.getKey().equals(key))
            .map(Map.Entry::getValue)
            .collect(Collectors.toList());
    }

    public Optional<String> findKeyInParentElement() {
        return Optional.of(this)
            .map(RbelElement::getParentNode)
            .filter(Objects::nonNull)
            .stream()
            .flatMap(parent -> parent.getChildNodesWithKey().stream())
            .filter(e -> e.getValue() == this)
            .map(Map.Entry::getKey)
            .findFirst();
    }

    public List<RbelElement> findRbelPathMembers(String rbelPath) {
        return new RbelPathExecutor(this, rbelPath)
            .execute();
    }

    @Nullable
    public String getRawStringContent() {
        if (rawContent == null) {
            return null;
        } else {
            return new String(rawContent, getElementCharset());
        }
    }

    public Charset getElementCharset() {
        return charset
            .or(() -> Optional.ofNullable(parentNode)
                .filter(Objects::nonNull)
                .map(RbelElement::getElementCharset))
            .orElse(StandardCharsets.UTF_8);
    }

    public <T extends RbelFacet> T getFacetOrFail(Class<T> facetClass) {
        return getFacet(facetClass).orElseThrow();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
            .append("uuid", uuid)
            .append("facets", facets)
            .append("path", findNodePath())
            .toString();
    }

    public Optional<Object> seekValue() {
        return getFacet(RbelValueFacet.class)
            .map(RbelValueFacet::getValue)
            .filter(Objects::nonNull);
    }

    public Optional<String> printValue() {
        return getFacet(RbelValueFacet.class)
            .map(RbelValueFacet::getValue)
            .filter(Objects::nonNull)
            .map(Object::toString);
    }

    public <T> Optional<T> seekValue(Class<T> clazz) {
        return getFacet(RbelValueFacet.class)
            .map(RbelValueFacet::getValue)
            .filter(Objects::nonNull)
            .filter(clazz::isInstance)
            .map(clazz::cast);
    }

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
        synchronized (facets) {
            getFacet(facet.getClass())
                .ifPresent(facets::remove);
            facets.add(facet);
        }
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
            "RbelPath '" + rbelPath + "' is not unique! Found " + resultList.size() + " elements, expected only one!");
    }

    public String printTreeStructureWithoutColors() {
        return RbelElementTreePrinter.builder()
            .rootElement(this)
            .printColors(false)
            .build()
            .execute();
    }

    public String printTreeStructure() {
        return RbelElementTreePrinter.builder()
            .rootElement(this)
            .build()
            .execute();
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
        return Collections.unmodifiableList(facets).stream()
            .flatMap(facet -> {
                if (facet instanceof RbelNestedFacet) {
                    return ((RbelNestedFacet) facet).getNestedElement().getFacets().stream();
                } else {
                    return Stream.of(facet);
                }
            })
            .filter(RbelNoteFacet.class::isInstance)
            .map(RbelNoteFacet.class::cast)
            .collect(Collectors.toUnmodifiableList());
    }

    public RbelElement findMessage() {
        RbelElement position = this;
        while (position.getParentNode() != null) {
            position = position.getParentNode();
        }
        return position;
    }

    private static class RbelPathNotUniqueException extends RuntimeException {

        public RbelPathNotUniqueException(String s) {
            super(s);
        }
    }
}
