/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.modifier;

import com.google.gson.JsonElement;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelJsonFacet;
import de.gematik.rbellogger.exceptions.RbelJexlException;
import java.util.Map;
import java.util.StringJoiner;

public class RbelJsonWriter implements RbelElementWriter {

  @Override
  public boolean canWrite(RbelElement oldTargetElement) {
    return oldTargetElement.hasFacet(RbelJsonFacet.class);
  }

  @Override
  public byte[] write(
      RbelElement oldTargetElement, RbelElement oldTargetModifiedChild, byte[] newContent) {
    final JsonElement jsonElement =
        oldTargetElement.getFacetOrFail(RbelJsonFacet.class).getJsonElement();
    if (jsonElement.isJsonPrimitive()) {
      if (jsonElement.getAsJsonPrimitive().isString()) {
        return (quote(new String(newContent, oldTargetElement.getElementCharset())))
            .getBytes(oldTargetElement.getElementCharset());
      } else {
        return newContent;
      }
    } else if (jsonElement.isJsonObject()) {
      return writeJsonObject(oldTargetElement, oldTargetModifiedChild, newContent, jsonElement);
    } else if (jsonElement.isJsonArray()) {
      StringJoiner joiner = new StringJoiner(",");
      for (JsonElement entry : jsonElement.getAsJsonArray()) {
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
      JsonElement jsonElement) {
    StringJoiner joiner = new StringJoiner(",");
    for (Map.Entry<String, JsonElement> entry : jsonElement.getAsJsonObject().entrySet()) {
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
