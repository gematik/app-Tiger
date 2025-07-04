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
package de.gematik.rbellogger.facets.http.formdata;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.facets.http.RbelHttpHeaderFacet;
import de.gematik.rbellogger.facets.http.RbelHttpMessageFacet;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;

public class RbelHttpFormDataConverter extends RbelConverterPlugin {

  @Override
  public void consumeElement(RbelElement rbelElement, RbelConversionExecutor converter) {
    if (isBodyOfFormDataRequest(rbelElement)) {
      final RbelMultiMap<RbelElement> formDataMap =
          Stream.of(rbelElement.getRawStringContent().split("&"))
              .map(param -> param.split("="))
              .filter(params -> params.length == 2)
              .map(
                  paramList ->
                      Pair.of(paramList[0], converter.convertElement(paramList[1], rbelElement)))
              .collect(RbelMultiMap.COLLECTOR);

      rbelElement.addFacet(RbelHttpFormDataFacet.builder().formDataMap(formDataMap).build());
    }
  }

  private boolean isBodyOfFormDataRequest(RbelElement rbelElement) {
    return Optional.ofNullable(rbelElement)
        .map(RbelElement::getParentNode)
        .flatMap(el -> el.getFacet(RbelHttpMessageFacet.class))
        .map(RbelHttpMessageFacet::getHeader)
        .flatMap(el -> el.getFacet(RbelHttpHeaderFacet.class))
        .filter(
            header -> header.hasValueMatching("Content-Type", "application/x-www-form-urlencoded"))
        .isPresent();
  }
}
