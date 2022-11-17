/*
 * Copyright (c) 2022 gematik GmbH
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

import de.gematik.rbellogger.converter.brainpool.BrainpoolCurves;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.elements.RbelJweEncryptionInfo;
import de.gematik.rbellogger.data.facet.RbelJweFacet;
import de.gematik.rbellogger.data.facet.RbelRootFacet;
import de.gematik.rbellogger.key.RbelKey;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.Pair;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jwe.JsonWebEncryption;
import org.jose4j.lang.JoseException;

public class RbelJweConverter implements RbelConverterPlugin {

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

        final Optional<Pair<String, String>> correctKeyAndPayload = findCorrectKeyAndReturnPayload(context, jwe);
        RbelJweFacet jweFacet;
        if (correctKeyAndPayload.isEmpty()) {
            jweFacet = RbelJweFacet.builder()
                .header(context.convertElement(jwe.getHeaders().getFullHeaderAsJsonString(), rbel))
                .body(context.convertElement("<Encrypted Payload>", rbel))
                .encryptionInfo(buildEncryptionInfo(false, null, rbel))
                .build();
        } else {
            jweFacet = RbelJweFacet.builder()
                .header(context.convertElement(jwe.getHeaders().getFullHeaderAsJsonString(), rbel))
                .body(context.convertElement(correctKeyAndPayload.get().getValue(), rbel))
                .encryptionInfo(buildEncryptionInfo(true, correctKeyAndPayload.get().getKey(), rbel))
                .build();
        }
        rbel.addFacet(jweFacet);
        rbel.addFacet(new RbelRootFacet<>(jweFacet));
    }

    private RbelElement buildEncryptionInfo(boolean decryptable, String keyId, RbelElement jweElement) {
        final RbelElement encryptionInfoElement = RbelElement.builder()
            .parentNode(jweElement)
            .rawContent(null)
            .build();

        encryptionInfoElement.addFacet(RbelJweEncryptionInfo.builder()
            .wasDecryptable(RbelElement.wrap(null, encryptionInfoElement, decryptable))
            .decryptedUsingKeyWithId(RbelElement.wrap(null, encryptionInfoElement, keyId))
            .build());

        return encryptionInfoElement;
    }

    private Optional<Pair<String, String>> findCorrectKeyAndReturnPayload(RbelConverter context,
        JsonWebEncryption jwe) {
        for (RbelKey keyEntry : context.getRbelKeyManager().getAllKeys().collect(Collectors.toList())) {
            try {
                jwe.setKey(keyEntry.getKey());
                return Optional.of(Pair.of(keyEntry.getKeyName(), jwe.getPayload()));
            } catch (Exception e) {
                continue;
            }
        }
        return Optional.empty();
    }

    @SneakyThrows
    private Optional<JsonWebEncryption> initializeJwe(RbelElement rbel) {
        final JsonWebEncryption receiverJwe = new JsonWebEncryption();

        receiverJwe.setDoKeyValidation(false);
        receiverJwe.setAlgorithmConstraints(AlgorithmConstraints.NO_CONSTRAINTS);

        try {
            receiverJwe.setCompactSerialization(rbel.getRawStringContent());
            receiverJwe.getHeaders();
            return Optional.ofNullable(receiverJwe);
        } catch (final JoseException e) {
            return Optional.empty();
        }
    }
}
