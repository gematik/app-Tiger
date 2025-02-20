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

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.*;
import de.gematik.rbellogger.util.RbelContent;
import java.util.Base64;
import java.util.Optional;

public class RbelBase64JsonConverter implements RbelConverterPlugin {

  @Override
  public void consumeElement(RbelElement rbel, RbelConverter context) {
    if (rbel.getSize() == 0 || rbel.hasFacet(RbelRootFacet.class)) {
      return;
    }
    var content = rbel.getContent();
    safeConvertBase64Using(Base64.getDecoder(), content, context, rbel)
        .or(() -> safeConvertBase64Using(Base64.getUrlDecoder(), content, context, rbel))
        .ifPresent(rbel::addFacet);
  }

  private Optional<RbelBase64Facet> safeConvertBase64Using(
      Base64.Decoder decoder, RbelContent input, RbelConverter context, RbelElement parentNode) {
    final Optional<RbelElement> rawElement =
        Optional.ofNullable(input)
            .map(
                i -> {
                  try {
                    return RbelContent.from(decoder.wrap(input.toInputStream()));
                  } catch (Exception e) {
                    return null;
                  }
                })
            .map(data -> RbelElement.builder().content(data).parentNode(parentNode).build());
    if (rawElement.isEmpty()) {
      return Optional.empty();
    }
    context.convertElement(rawElement.get());
    if (rawElement.get().hasFacet(RbelRootFacet.class)) {
      final RbelRootFacet<?> rootFacet = rawElement.get().getFacetOrFail(RbelRootFacet.class);
      if (rootFacet.getRootFacet() instanceof RbelJsonFacet
          || rootFacet.getRootFacet() instanceof RbelXmlFacet) {
        return Optional.of(new RbelBase64Facet(rawElement.get()));
      }
    }
    return Optional.empty();
  }
}
