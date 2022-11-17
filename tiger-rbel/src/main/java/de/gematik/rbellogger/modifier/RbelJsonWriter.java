/*
 * Copyright (c) 2022 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.rbellogger.modifier;

import com.google.gson.JsonElement;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelJsonFacet;

import java.util.Map;
import java.util.StringJoiner;

public class RbelJsonWriter implements RbelElementWriter {

    @Override
    public boolean canWrite(RbelElement oldTargetElement) {
        return oldTargetElement.hasFacet(RbelJsonFacet.class);
    }

    @Override
    public byte[] write(RbelElement oldTargetElement, RbelElement oldTargetModifiedChild, byte[] newContent) {
        final JsonElement jsonElement = oldTargetElement.getFacetOrFail(RbelJsonFacet.class).getJsonElement();
        if (jsonElement.isJsonPrimitive()) {
            if (jsonElement.getAsJsonPrimitive().isString()) {
                return ("\"" + new String(newContent, oldTargetElement.getElementCharset()) + "\"")
                    .getBytes(oldTargetElement.getElementCharset());
            } else {
                return newContent;
            }
        } else if (jsonElement.isJsonObject()) {
            StringJoiner joiner = new StringJoiner(",");
            for (Map.Entry<String, JsonElement> entry : jsonElement.getAsJsonObject().entrySet()) {
                if (entry.getValue() == oldTargetModifiedChild.getFacetOrFail(RbelJsonFacet.class).getJsonElement()) {
                    joiner.add("\"" + entry.getKey() + "\": " + new String(newContent, oldTargetElement.getElementCharset()));
                } else {
                    joiner.add("\"" + entry.getKey() + "\": " + entry.getValue().toString());
                }
            }
            return ("{" + joiner + "}").getBytes(oldTargetElement.getElementCharset());
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
            throw new RuntimeException();
        }
    }
}
