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

package de.gematik.rbellogger.modifier;

import com.fasterxml.jackson.databind.JsonNode;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.exceptions.RbelJexlException;
import de.gematik.rbellogger.facets.jackson.RbelJsonFacet;
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
