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

package de.gematik.rbellogger.key;

import de.gematik.rbellogger.converter.RbelConverterPlugin;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelJsonFacet;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.util.*;
import java.util.stream.Stream;

@Slf4j
public class RbelKeyManager {

    public static final RbelConverterPlugin RBEL_IDP_TOKEN_KEY_LISTENER = (element, converter) ->
            Optional.ofNullable(element)
                    .filter(el -> el.hasFacet(RbelJsonFacet.class))
                    .filter(el -> el.getKey().filter(key -> key.equals("token_key")).isPresent())
                    .flatMap(el -> el.getFirst("content"))
                    .map(RbelElement::getRawStringContent)
                    .map(tokenB64 -> {
                        try {
                            return Base64.getDecoder().decode(tokenB64);
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .map(tokenKeyBytes -> new SecretKeySpec(tokenKeyBytes, "AES"))
                    .ifPresent(
                            aesKey -> converter.getRbelKeyManager().addKey("token_key", aesKey, RbelKey.PRECEDENCE_KEY_FOLDER));

    private final List<RbelKey> keyList = new ArrayList<>();

    public RbelKeyManager addAll(Map<String, RbelKey> keys) {
        keyList.addAll(keys.values());
        return this;
    }

    public void addKey(RbelKey rbelKey) {
        if (rbelKey.getKey() == null) {
            return;
        }
        
        if (keyIsPresentInList(rbelKey.getKey())) {
            log.trace("Skipping adding key: Key is already known!");
        }

        keyList.add(rbelKey);
    }

    public RbelKey addKey(String keyId, Key key, int precedence) {
        if (keyIsPresentInList(key)) {
            log.trace("Skipping adding key: Key is already known!");
        }

        final RbelKey rbelKey = RbelKey.builder()
            .keyName(keyId)
            .key(key)
            .precedence(precedence)
            .build();

        keyList.add(rbelKey);

        log.debug("Added key {} (Now there are {} keys known)", keyId, keyList.size());

        return rbelKey;
    }

    private boolean keyIsPresentInList(Key key) {
        return keyList.stream()
                .map(RbelKey::getKey)
                .map(Key::getEncoded)
                .anyMatch(oldKey -> Arrays.equals(oldKey, key.getEncoded()));
    }

    public Stream<RbelKey> getAllKeys() {
        return keyList
                .stream()
                .sorted(Comparator.comparing(RbelKey::getPrecedence));
    }

    public Optional<RbelKey> findCorrespondingPrivateKey(String rbelKey) {
        return getAllKeys()
            .filter(candidate -> candidate.getMatchingPublicKey().isPresent())
            .filter(candidate -> Objects.equals(candidate.getMatchingPublicKey().get().getKeyName(), rbelKey))
            .findFirst();
    }

    public Optional<RbelKey> findKeyByName(String keyName) {
        return getAllKeys()
                .filter(candidate -> candidate.getKeyName() != null)
                .filter(candidate -> candidate.getKeyName().equals(keyName))
                .findFirst();
    }
}
