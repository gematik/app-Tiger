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

package de.gematik.rbellogger.facets.jose;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConversionPhase;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.facets.jackson.RbelJsonFacet;
import de.gematik.rbellogger.key.RbelKey;
import de.gematik.rbellogger.util.CryptoLoader;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RbelX5cKeyReader extends RbelConverterPlugin {

  @Override
  public RbelConversionPhase getPhase() {
    return RbelConversionPhase.CONTENT_ENRICHMENT;
  }

  @Override
  public void consumeElement(RbelElement rbelElement, RbelConversionExecutor converter) {
    final List<RbelElement> elementList =
        rbelElement.findRbelPathMembers("$..x5c").stream()
            .filter(el -> el.hasFacet(RbelJsonFacet.class))
            .toList();
    for (RbelElement x5cElement : elementList) {
      final Optional<byte[]> certificateData = getX509Certificate(x5cElement);
      final Optional<String> keyId = getKeyId(x5cElement);
      if (keyId.isPresent() && certificateData.isPresent()) {
        try {
          final X509Certificate certificate =
              CryptoLoader.getCertificateFromPem(certificateData.get());
          converter
              .getRbelKeyManager()
              .addKey(keyId.get(), certificate.getPublicKey(), RbelKey.PRECEDENCE_X5C_HEADER_VALUE)
              .ifPresent(newKey -> log.info("Added new key from JKS ({})", newKey.getKeyName()));
        } catch (Exception e) {
          log.trace("Exception while extracting X5C", e);
        }
      }
    }
  }

  private Optional<String> getKeyId(RbelElement x5cElement) {
    return Optional.ofNullable(x5cElement.getParentNode())
        .flatMap(el -> el.getFirst("kid"))
        .flatMap(el -> el.getFirst("content"))
        .map(RbelElement::getRawStringContent);
  }

  private Optional<byte[]> getX509Certificate(RbelElement x5cElement) {
    try {
      return x5cElement
          .getFirst("0")
          .flatMap(el -> el.getFirst("content"))
          .map(RbelElement::getRawStringContent)
          .map(Base64.getDecoder()::decode);
    } catch (Exception e) {
      return Optional.empty();
    }
  }
}
