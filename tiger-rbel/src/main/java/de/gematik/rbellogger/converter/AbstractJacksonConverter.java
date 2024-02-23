/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.facet.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

/**
 * Abstract converter for structured formats that are parsed via Jackson.
 */
@RequiredArgsConstructor
public abstract class AbstractJacksonConverter<F extends RbelFacet>
    implements RbelConverterPlugin {

  @Getter(AccessLevel.PACKAGE)
  private final ObjectMapper mapper;
  private final Class<F> facetClass;

  private Optional<JsonNode> convertToJacksonNode(final RbelElement target) {
    if (!shouldElementBeConsidered(target)) {
      return Optional.empty();
    }

    try {
      return Optional.of(convertContentUsingJackson(target));
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  JsonNode convertContentUsingJackson(RbelElement target) throws IOException {
    return mapper.readTree(target.getRawContent());
  }

  boolean shouldElementBeConsidered(RbelElement target) {
    return true;
  }

  @Override
  public void consumeElement(RbelElement rbelElement, RbelConverter converter) {
    final Optional<JsonNode> jsonOptional = convertToJacksonNode(rbelElement);
    if (jsonOptional.isEmpty()) {
      return;
    }
    if (jsonOptional.get().isContainerNode()) {
      augmentRbelElementWithFacet(jsonOptional.get(), converter, rbelElement);
      rbelElement.addFacet(new RbelRootFacet<>(rbelElement.getFacetOrFail(facetClass)));
    }
  }

  @SneakyThrows
  private void augmentRbelElementWithFacet(
      final JsonNode node, final RbelConverter context, final RbelElement parentElement) {
    parentElement.addFacet(buildFacetForNode(node));
    if (node.isObject()) {
      convertObject(node, context, parentElement);
    } else if (node.isArray()) {
      convertArray(node, context, parentElement);
    } else if (node.isValueNode()) {
      convertPrimitive(node, context, parentElement);
    } else {
      parentElement.addFacet(RbelValueFacet.builder().value(null).build());
    }
  }

  abstract F buildFacetForNode(JsonNode node);

  private void convertPrimitive(
      JsonNode node, RbelConverter context, RbelElement parentElement) throws IOException {
    if (node.isTextual()) {
      addFacetAndConvertNestedElement(parentElement, node.asText(), context);
    } else if (node.isFloatingPointNumber()) {
      addFacetAndConvertNestedElement(parentElement, node.doubleValue(), context);
    } else if (node.isNumber()) {
      addFacetAndConvertNestedElement(parentElement, node.longValue(), context);
    } else if (node.isBoolean()) {
      addFacetAndConvertNestedElement(parentElement, node.booleanValue(), context);
    } else if (node.isBinary()) {
      final RbelElement nestedElement = new RbelElement(node.binaryValue(), parentElement);
      context.convertElement(nestedElement);
      parentElement.addFacet(new RbelNestedFacet(nestedElement));
    }
  }

  private void convertArray(
      JsonNode node, RbelConverter context, RbelElement parentElement) {
    final ArrayList<RbelElement> elementList = new ArrayList<>();

    parentElement.addFacet(RbelListFacet.builder().childNodes(elementList).build());

    for (Iterator<JsonNode> it = node.elements(); it.hasNext(); ) {
      JsonNode el = it.next();
      RbelElement newChild =
          new RbelElement(el.toString().getBytes(parentElement.getElementCharset()), parentElement);
      augmentRbelElementWithFacet(el, context, newChild);
      elementList.add(newChild);
    }
  }

  private void convertObject(
      JsonNode node, RbelConverter context, RbelElement parentElement) {
    final RbelMultiMap<RbelElement> elementMap = new RbelMultiMap<>();
    parentElement.addFacet(RbelMapFacet.builder().childNodes(elementMap).build());
    for (Iterator<Entry<String, JsonNode>> it = node.fields(); it.hasNext(); ) {
      Entry<String, JsonNode> entry = it.next();
      RbelElement newChild =
          new RbelElement(
              entry.getValue().toString().getBytes(parentElement.getElementCharset()),
              parentElement);
      augmentRbelElementWithFacet(entry.getValue(), context, newChild);
      elementMap.put(entry.getKey(), newChild);
    }
  }

  private void addFacetAndConvertNestedElement(
      RbelElement parentElement, Object value, RbelConverter context) {
    final RbelElement nestedElement = RbelElement.wrap(parentElement, value);
    context.convertElement(nestedElement);
    parentElement.addFacet(new RbelNestedFacet(nestedElement));
  }
}
