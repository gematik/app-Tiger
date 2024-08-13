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

package de.gematik.rbellogger.converter;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.*;

public class RbelJsonConverter extends AbstractJacksonConverter<RbelJsonFacet> {

  public RbelJsonConverter() {
    super(
        new ObjectMapper().configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true),
        RbelJsonFacet.class);
  }

  @Override
  JsonNode convertContentUsingJackson(RbelElement target) throws JsonProcessingException {
    return getMapper().readTree(target.getRawStringContent());
  }

  @Override
  RbelJsonFacet buildFacetForNode(JsonNode node) {
    return RbelJsonFacet.builder().jsonElement(node).build();
  }

  @Override
  boolean shouldElementBeConsidered(RbelElement target) {
    String content = target.getRawStringContent();
    return content != null
        && ((content.contains("{") && content.contains("}"))
            || (content.contains("[") && content.contains("]")));
  }
}
