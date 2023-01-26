/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.facet.*;
import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.Optional;

public class RbelJsonConverter implements RbelConverterPlugin {

    public Optional<JsonElement> convertToJson(final String content) {
        if ((content.contains("{") && content.contains("}"))
            || (content.contains("[") && content.contains("]"))) {
            try {
                return Optional.of(JsonParser.parseString(content));
            } catch (Exception e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    @Override
    public void consumeElement(RbelElement rbelElement, RbelConverter converter) {
        final Optional<JsonElement> jsonOptional = convertToJson(rbelElement.getRawStringContent());
        if (jsonOptional.isEmpty()) {
            return;
        }
        if (jsonOptional.get().isJsonObject() || jsonOptional.get().isJsonArray()) {
            augmentRbelElementWithJsonFacet(jsonOptional.get(), converter, rbelElement);
            rbelElement.addFacet(new RbelRootFacet(rbelElement.getFacetOrFail(RbelJsonFacet.class)));
        }
    }

    private void augmentRbelElementWithJsonFacet(final JsonElement jsonElement, final RbelConverter context,
        final RbelElement parentElement) {
        parentElement.addFacet(RbelJsonFacet.builder()
            .jsonElement(jsonElement)
            .build());
        if (jsonElement.isJsonObject()) {
            final RbelMultiMap<RbelElement> elementMap = new RbelMultiMap();
            parentElement.addFacet(RbelMapFacet.builder().childNodes(elementMap).build());
            for (Entry<String, JsonElement> entry : jsonElement.getAsJsonObject().entrySet()) {
                RbelElement newChild = new RbelElement(entry.getValue().toString().getBytes(parentElement.getElementCharset()), parentElement);
                augmentRbelElementWithJsonFacet(entry.getValue(), context, newChild);
                elementMap.put(entry.getKey(), newChild);
            }
        } else if (jsonElement.isJsonArray()) {
            final ArrayList<RbelElement> elementList = new ArrayList<>();

            parentElement.addFacet(RbelListFacet.builder()
                .childNodes(elementList)
                .build());

            for (JsonElement el : jsonElement.getAsJsonArray()) {
                RbelElement newChild = new RbelElement(el.toString().getBytes(parentElement.getElementCharset()), parentElement);
                augmentRbelElementWithJsonFacet(el, context, newChild);
                elementList.add(newChild);
            }
        } else if (jsonElement.isJsonPrimitive()) {
            if (jsonElement.getAsJsonPrimitive().isString()) {
                addFacetAndConvertNestedElement(parentElement, jsonElement.getAsString(), context);
            } else if (jsonElement.getAsJsonPrimitive().isNumber()) {
                addFacetAndConvertNestedElement(parentElement, jsonElement.getAsLong(), context);
            } else if (jsonElement.getAsJsonPrimitive().isBoolean()) {
                addFacetAndConvertNestedElement(parentElement, jsonElement.getAsBoolean(), context);
            }
        } else {
            parentElement.addFacet(RbelValueFacet.builder()
                .value(null)
                .build());
        }
    }

    private void addFacetAndConvertNestedElement(RbelElement parentElement, Object value, RbelConverter context) {
        final RbelElement nestedElement = RbelElement.wrap(parentElement, value);
        context.convertElement(nestedElement);
        parentElement.addFacet(new RbelNestedFacet(nestedElement));
    }
}
