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
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.converter.ConverterInfo;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelRootFacet;
import de.gematik.rbellogger.data.core.RbelValueFacet;
import de.gematik.rbellogger.facets.pki.base64.RbelBase64JsonConverter;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Optional;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jose4j.jca.ProviderContext;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.lang.JoseException;

@ConverterInfo(dependsOn = {RbelBase64JsonConverter.class})
@Slf4j
public class RbelJwtConverter extends RbelConverterPlugin {

  public static final int JWT_DOT_SEPARATOR_COUNT = 2;

  @Override
  @SuppressWarnings({"java:S1135", "java:S108"})
  public void consumeElement(RbelElement rbelElement, RbelConversionExecutor converter) {
    int dotCount =
        rbelElement.getContent().countOccurrencesUpTo((byte) '.', JWT_DOT_SEPARATOR_COUNT + 1);
    if (dotCount != JWT_DOT_SEPARATOR_COUNT) {
      return;
    }
    parseJwt(rbelElement, converter);
  }

  private void parseJwt(RbelElement rbelElement, RbelConversionExecutor converter) {
    try {
      final JsonWebSignature jsonWebSignature = initializeJws(rbelElement);

      final RbelElement headerElement =
          converter.convertElement(
              jsonWebSignature
                  .getHeaders()
                  .getFullHeaderAsJsonString()
                  .getBytes(StandardCharsets.UTF_8),
              rbelElement);
      final RbelElement bodyElement =
          converter.convertElement(jsonWebSignature.getUnverifiedPayloadBytes(), rbelElement);
      final RbelElement signatureElement =
          new RbelElement(
              Base64.getUrlDecoder().decode(jsonWebSignature.getEncodedSignature()), rbelElement);
      signatureElement.addFacet(
          converter
              .getRbelKeyManager()
              .getAllKeys()
              .map(
                  rbelKey ->
                      verifySig(
                          jsonWebSignature,
                          rbelKey.getKey(),
                          rbelKey.getKeyName(),
                          signatureElement))
              .filter(Optional::isPresent)
              .map(Optional::get)
              .findAny()
              .or(
                  () ->
                      tryToGetKeyFromX5cHeaderClaim(jsonWebSignature)
                          .map(
                              key ->
                                  verifySig(
                                      jsonWebSignature,
                                      key,
                                      "x5c-header certificate",
                                      signatureElement))
                          .filter(Optional::isPresent)
                          .map(Optional::get))
              .orElseGet(
                  () ->
                      RbelJwtSignature.builder()
                          .isValid(
                              new RbelElement(signatureElement)
                                  .addFacet(new RbelValueFacet<>(false)))
                          .verifiedUsing(null)
                          .build()));
      final RbelJwtFacet rbelJwtFacet =
          new RbelJwtFacet(headerElement, bodyElement, signatureElement);
      rbelElement.addFacet(rbelJwtFacet);
      rbelElement.addFacet(new RbelRootFacet<>(rbelJwtFacet));
    } catch (JoseException ignored) {
    }
  }

  @SneakyThrows
  private Optional<PublicKey> tryToGetKeyFromX5cHeaderClaim(JsonWebSignature jsonWebSignature) {
    return Optional.ofNullable(jsonWebSignature.getCertificateChainHeaderValue())
        .map(list -> list.get(0))
        .map(X509Certificate::getPublicKey);
  }

  private JsonWebSignature initializeJws(RbelElement rbel) throws JoseException {
    final JsonWebSignature jsonWebSignature = new JsonWebSignature();

    ProviderContext context = new ProviderContext();
    context.getSuppliedKeyProviderContext().setGeneralProvider("BC");
    jsonWebSignature.setProviderContext(context);

    jsonWebSignature.setCompactSerialization(rbel.getRawStringContent());
    return jsonWebSignature;
  }

  private Optional<RbelJwtSignature> verifySig(
      final JsonWebSignature jsonWebSignature,
      final Key key,
      final String keyId,
      final RbelElement signatureElement) {
    try {
      jsonWebSignature.setKey(key);
      tryToGetKeyFromX5cHeaderClaim(jsonWebSignature);
      if (jsonWebSignature.verifySignature()) {
        return Optional.of(
            RbelJwtSignature.builder()
                .isValid(new RbelElement(signatureElement).addFacet(new RbelValueFacet<>(true)))
                .verifiedUsing(
                    new RbelElement(signatureElement).addFacet(new RbelValueFacet<>(keyId)))
                .build());
      } else {
        return Optional.empty();
      }
    } catch (final JoseException e) {
      return Optional.empty();
    }
  }
}
