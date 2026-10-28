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
 * Parses ASN.1 structures anywhere in the traffic. To not misread arbitrary bytes, only a single
 * SEQUENCE is accepted, unless the element is nested in another ASN.1 structure or marked with
 * {@link RbelAllowAsn1FragmentsFacet} (then any single data object). Series of data objects are
 * left to the {@link RbelAsn1DataObjectsConverter}.
 */
@ConverterInfo(onlyActivateFor = "asn1")
public class RbelAsn1Converter extends RbelConverterPlugin {

  @Override
  public void consumeElement(RbelElement element, RbelConversionExecutor context) {
    final RbelElement parent = element.getParentNode();
    if (parent != null && parent.hasFacet(RbelAsn1DataObjectsFacet.class)) {
      return;
    }
    final Mode mode =
        parent != null
                && (parent.hasFacet(RbelAsn1Facet.class)
                    || parent.hasFacet(RbelAllowAsn1FragmentsFacet.class))
            ? Mode.SINGLE_OBJECT
            : Mode.SEQUENCE;
    new Asn1TreeBuilder(context)
        .parse(element, mode)
        .ifPresent(facet -> element.addFacet(new RbelRootFacet<>(facet)));
  }
}
