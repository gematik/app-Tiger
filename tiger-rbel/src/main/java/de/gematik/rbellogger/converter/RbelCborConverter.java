/*
 * Copyright (c) 2024 gematik GmbH
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

package de.gematik.rbellogger.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import de.gematik.rbellogger.data.facet.*;

/**
 * Converter for CBOR(Concise Binary Object Representation) format.
 * <p>
 * This class utilizes the Jackson library for CBOR serialization and deserialization.
 * </p>
 */
public class RbelCborConverter extends AbstractJacksonConverter<RbelCborFacet> {

  public RbelCborConverter() {
    super(new CBORMapper(), RbelCborFacet.class);
  }

  @Override
  RbelCborFacet buildFacetForNode(JsonNode node) {
    return RbelCborFacet.builder().node(node).build();
  }
}
