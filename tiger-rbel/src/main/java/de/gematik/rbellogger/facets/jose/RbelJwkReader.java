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
package de.gematik.rbellogger.facets.jose;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConversionPhase;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelListFacet;
import de.gematik.rbellogger.data.core.RbelNoteFacet;
import de.gematik.rbellogger.data.core.RbelNoteFacet.NoteStyling;
import de.gematik.rbellogger.facets.jackson.RbelJsonFacet;
import de.gematik.rbellogger.key.RbelKey;
import java.util.List;
import java.util.Optional;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.JsonWebKey.Factory;
import org.jose4j.lang.JoseException;

public class RbelJwkReader extends RbelConverterPlugin {

  @Override
  public RbelConversionPhase getPhase() {
    return RbelConversionPhase.CONTENT_ENRICHMENT;
  }

  @Override
  public void consumeElement(RbelElement rbelElement, RbelConversionExecutor converter) {
    final List<RbelElement> keysList =
        rbelElement.findRbelPathMembers("$..keys").stream()
            .filter(element -> element.hasFacet(RbelJsonFacet.class))
            .filter(el -> el.hasFacet(RbelJsonFacet.class))
            .map(el -> el.getFacet(RbelListFacet.class))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(RbelListFacet::getChildNodes)
            .flatMap(List::stream)
            .toList();

    for (RbelElement keyElement : keysList) {
      tryToConvertKeyAndAddToKeyManager(keyElement, converter);
    }
  }

  private void tryToConvertKeyAndAddToKeyManager(
      RbelElement keyElement, RbelConversionExecutor converter) {
    try {
      final JsonWebKey jwk = Factory.newJwk(keyElement.getRawStringContent());
      converter
          .getRbelKeyManager()
          .addKey(
              RbelKey.builder()
                  .key(jwk.getKey())
                  .keyName(jwk.getKeyId())
                  .precedence(RbelKey.PRECEDENCE_JWK_VALUE)
                  .build());
    } catch (RuntimeException | JoseException e) {
      keyElement.addFacet(
          RbelNoteFacet.builder()
              .value("Unable to parse key: " + e.getMessage())
              .style(NoteStyling.WARN)
              .build());
    }
  }
}
