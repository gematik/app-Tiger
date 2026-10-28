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
package de.gematik.rbellogger.facets.asn1;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.converter.ConverterInfo;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelRootFacet;
import de.gematik.rbellogger.facets.asn1.Asn1TreeBuilder.Mode;

/**
 * Parses elements that a protocol converter declared as a series of BER data objects (see {@link
 * RbelAsn1DataObjectsFacet}). Active for every parser that sets the marker, so these fields are
 * structured even if {@code asn1} isn't activated.
 */
@ConverterInfo(onlyActivateFor = {"asn1", "sicct"})
public class RbelAsn1DataObjectsConverter extends RbelConverterPlugin {

  @Override
  public void consumeElement(RbelElement element, RbelConversionExecutor context) {
    final RbelElement parent = element.getParentNode();
    if (parent == null || !parent.hasFacet(RbelAsn1DataObjectsFacet.class)) {
      return;
    }
    new Asn1TreeBuilder(context)
        .parse(element, Mode.OBJECTS)
        .ifPresent(facet -> element.addFacet(new RbelRootFacet<>(facet)));
  }
}
