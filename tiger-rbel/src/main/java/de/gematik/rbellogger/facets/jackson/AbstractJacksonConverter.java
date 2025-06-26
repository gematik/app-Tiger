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
package de.gematik.rbellogger.facets.jackson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.*;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

/** Abstract converter for structured formats that are parsed via Jackson. */
@RequiredArgsConstructor
public abstract class AbstractJacksonConverter<F extends RbelFacet> extends RbelConverterPlugin {

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
    return mapper.readTree(
        new InputStreamReader(target.getContent().toInputStream(), target.getElementCharset()));
  }

  boolean shouldElementBeConsidered(RbelElement target) {
    return true;
  }

  @Override
  public boolean skipParsingOversizedContent() {
    return true;
  }

  @Override
  public void consumeElement(RbelElement rbelElement, RbelConversionExecutor converter) {
    if (rbelElement.getContent().isEmpty()) {
      return;
    }
    convertToJacksonNode(rbelElement)
        .filter(json -> json.isContainerNode() && !json.isEmpty())
        .ifPresent(
            json -> {
              augmentRbelElementWithFacet(json, converter, rbelElement);
              rbelElement.addFacet(new RbelRootFacet<>(rbelElement.getFacetOrFail(facetClass)));
            });
  }

  @SneakyThrows
  private void augmentRbelElementWithFacet(
      final JsonNode node, final RbelConversionExecutor context, final RbelElement parentElement) {
    parentElement.addFacet(buildFacetForNode(node));
    if (node.isObject()) {
      convertObject(node, context, parentElement);
    } else if (node.isArray()) {
      convertArray(node, context, parentElement);
    } else if (node.isValueNode()) {
      convertPrimitive(node, context, parentElement);
    } else {
      parentElement.addFacet(RbelValueFacet.of(null));
    }
  }

  abstract F buildFacetForNode(JsonNode node);

  private void convertPrimitive(
      JsonNode node, RbelConversionExecutor context, RbelElement parentElement) throws IOException {
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
      nestedElement.addFacet(new RbelBinaryFacet());
      nestedElement.addFacet(new RbelNoteFacet("base64 encoded binary content"));
      context.convertElement(nestedElement);
      parentElement.addFacet(new RbelNestedFacet(nestedElement));
    }
  }

  private void convertArray(
      JsonNode node, RbelConversionExecutor context, RbelElement parentElement) {
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
      JsonNode node, RbelConversionExecutor context, RbelElement parentElement) {
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
      RbelElement parentElement, Object value, RbelConversionExecutor context) {
    final RbelElement nestedElement = RbelElement.wrap(parentElement, value);
    context.convertElement(nestedElement);
    parentElement.addFacet(new RbelNestedFacet(nestedElement));
  }
}
