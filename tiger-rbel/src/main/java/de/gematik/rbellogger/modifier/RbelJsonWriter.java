/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.modifier;

import com.fasterxml.jackson.databind.JsonNode;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelJsonFacet;
import de.gematik.rbellogger.exceptions.RbelJexlException;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.StringJoiner;

public class RbelJsonWriter implements RbelElementWriter {

  @Override
  public boolean canWrite(RbelElement oldTargetElement) {
    return oldTargetElement.hasFacet(RbelJsonFacet.class);
  }

  @Override
  public byte[] write(
      RbelElement oldTargetElement, RbelElement oldTargetModifiedChild, byte[] newContent) {
    final JsonNode jsonElement =
        oldTargetElement.getFacetOrFail(RbelJsonFacet.class).getJsonElement();
    if (jsonElement.isValueNode()) {
      if (jsonElement.isTextual()) {
        return (quote(new String(newContent, oldTargetElement.getElementCharset())))
            .getBytes(oldTargetElement.getElementCharset());
      } else {
        return newContent;
      }
    } else if (jsonElement.isObject()) {
      return writeJsonObject(oldTargetElement, oldTargetModifiedChild, newContent, jsonElement);
    } else if (jsonElement.isArray()) {
      StringJoiner joiner = new StringJoiner(",");
      for (Iterator<JsonNode> it = jsonElement.elements(); it.hasNext(); ) {
        JsonNode entry = it.next();
        if (entry == oldTargetModifiedChild.getFacetOrFail(RbelJsonFacet.class).getJsonElement()) {
          joiner.add(new String(newContent, oldTargetElement.getElementCharset()));
        } else {
          joiner.add(entry.toString());
        }
      }
      return ("[" + joiner + "]").getBytes(oldTargetElement.getElementCharset());
    } else {
      throw new RbelJexlException(
          "Unable to write element that has no Json facet: "
              + oldTargetElement.printTreeStructure());
    }
  }

  private byte[] writeJsonObject(
      RbelElement oldTargetElement,
      RbelElement oldTargetModifiedChild,
      byte[] newContent,
      JsonNode jsonElement) {
    StringJoiner joiner = new StringJoiner(",");
    for (Iterator<Entry<String, JsonNode>> it = jsonElement.fields(); it.hasNext(); ) {
      Entry<String, JsonNode> entry = it.next();
      if (entry.getValue()
          == oldTargetModifiedChild.getFacetOrFail(RbelJsonFacet.class).getJsonElement()) {
        joiner.add(
            quote(entry.getKey())
                + ": "
                + new String(newContent, oldTargetElement.getElementCharset()));
      } else {
        joiner.add(quote(entry.getKey()) + ": " + entry.getValue().toString());
      }
    }
    return ("{" + joiner + "}").getBytes(oldTargetElement.getElementCharset());
  }

  private String quote(String str) {
    return "\"" + str + "\"";
  }
}
