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

import de.gematik.rbellogger.converter.brainpool.BrainpoolCurves;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.elements.RbelJweEncryptionInfo;
import de.gematik.rbellogger.data.facet.RbelJweFacet;
import de.gematik.rbellogger.data.facet.RbelRootFacet;
import de.gematik.rbellogger.key.RbelKey;
import java.util.Optional;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jwe.JsonWebEncryption;
import org.jose4j.lang.JoseException;

@Slf4j
@ConverterInfo(dependsOn = {RbelBase64JsonConverter.class})
public class RbelJweConverter implements RbelConverterPlugin {

  public static final int MAX_JWE_DOT_SEPARATOR_COUNT = 4;

  static {
    BrainpoolCurves.init();
  }

  @Override
  public void consumeElement(final RbelElement rbel, final RbelConverter context) {
    final Optional<JsonWebEncryption> jweOptional = initializeJwe(rbel);
    if (jweOptional.isEmpty()) {
      return;
    }
    final JsonWebEncryption jwe = jweOptional.get();

    final Optional<Pair<String, String>> correctKeyAndPayload =
        findCorrectKeyAndReturnPayload(context, jwe);
    RbelJweFacet jweFacet;
    if (correctKeyAndPayload.isEmpty()) {
      jweFacet =
          RbelJweFacet.builder()
              .header(context.convertElement(jwe.getHeaders().getFullHeaderAsJsonString(), rbel))
              .body(context.convertElement("<Encrypted Payload>", rbel))
              .encryptionInfo(buildEncryptionInfo(false, null, rbel))
              .build();
    } else {
      jweFacet =
          RbelJweFacet.builder()
              .header(context.convertElement(jwe.getHeaders().getFullHeaderAsJsonString(), rbel))
              .body(context.convertElement(correctKeyAndPayload.get().getValue(), rbel))
              .encryptionInfo(buildEncryptionInfo(true, correctKeyAndPayload.get().getKey(), rbel))
              .build();
    }
    rbel.addFacet(jweFacet);
    rbel.addFacet(new RbelRootFacet<>(jweFacet));
  }

  private RbelElement buildEncryptionInfo(
      boolean decryptable, String keyId, RbelElement jweElement) {
    final RbelElement encryptionInfoElement =
        RbelElement.builder().parentNode(jweElement).rawContent(null).build();

    encryptionInfoElement.addFacet(
        RbelJweEncryptionInfo.builder()
            .wasDecryptable(RbelElement.wrap(null, encryptionInfoElement, decryptable))
            .decryptedUsingKeyWithId(RbelElement.wrap(null, encryptionInfoElement, keyId))
            .build());

    return encryptionInfoElement;
  }

  @SuppressWarnings("java:S108")
  private Optional<Pair<String, String>> findCorrectKeyAndReturnPayload(
      RbelConverter context, JsonWebEncryption jwe) {
    for (RbelKey keyEntry : context.getRbelKeyManager().getAllKeys().toList()) {
      try {
        jwe.setKey(keyEntry.getKey());
        return Optional.of(Pair.of(keyEntry.getKeyName(), jwe.getPayload()));
      } catch (Exception ignored) {
      }
    }
    return Optional.empty();
  }

  @SneakyThrows
  private Optional<JsonWebEncryption> initializeJwe(RbelElement rbel) {
    var dotIndexes = ArrayUtils.indexesOf(rbel.getRawContent(), (byte) '.');
    var dotCount = dotIndexes.cardinality();
    if (dotCount < MAX_JWE_DOT_SEPARATOR_COUNT - 2 || dotCount > MAX_JWE_DOT_SEPARATOR_COUNT) {
      return Optional.empty();
    }

    return Optional.ofNullable(rbel.getRawStringContent())
        .map(RbelJweConverter::parseJweEncryption);
  }

  private static JsonWebEncryption parseJweEncryption(String content) {
    try {
      final JsonWebEncryption receiverJwe = new JsonWebEncryption();

      receiverJwe.setDoKeyValidation(false);
      receiverJwe.setAlgorithmConstraints(AlgorithmConstraints.NO_CONSTRAINTS);

      receiverJwe.setCompactSerialization(content);
      return receiverJwe;
    } catch (final JoseException e) {
      return null;
    }
  }
}
