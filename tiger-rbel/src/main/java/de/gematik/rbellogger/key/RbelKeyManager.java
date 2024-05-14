/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.key;

import de.gematik.rbellogger.converter.RbelConverterPlugin;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelJsonFacet;
import java.security.Key;
import java.util.*;
import java.util.stream.Stream;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RbelKeyManager {

  public static final RbelConverterPlugin RBEL_IDP_TOKEN_KEY_LISTENER =
      (element, converter) ->
          Optional.ofNullable(element)
              .filter(el -> el.hasFacet(RbelJsonFacet.class))
              .filter(el -> el.getKey().filter(key -> key.equals("token_key")).isPresent())
              .flatMap(el -> el.getFirst("content"))
              .map(RbelElement::getRawStringContent)
              .map(
                  tokenB64 -> {
                    try {
                      return Base64.getUrlDecoder().decode(tokenB64);
                    } catch (Exception e1) {
                      try {
                        return Base64.getDecoder().decode(tokenB64);
                      } catch (Exception e2) {
                        return null;
                      }
                    }
                  })
              .filter(Objects::nonNull)
              .map(tokenKeyBytes -> new SecretKeySpec(tokenKeyBytes, "AES"))
              .ifPresent(
                  aesKey ->
                      converter
                          .getRbelKeyManager()
                          .addKey("token_key", aesKey, RbelKey.PRECEDENCE_KEY_FOLDER));

  private final List<RbelKey> keyList = new ArrayList<>();

  public synchronized RbelKeyManager addAll(Map<String, RbelKey> keys) {
    keyList.addAll(keys.values());
    return this;
  }

  public synchronized void addKey(RbelKey rbelKey) {
    if (rbelKey.getKey() == null) {
      return;
    }

    if (keyIsPresentInList(rbelKey)) {
      log.trace("Skipping adding key: Key is already known!");
    } else {
      keyList.add(rbelKey);
    }
  }

  public synchronized RbelKey addKey(String keyId, Key key, int precedence) {
    final RbelKey rbelKey =
        RbelKey.builder().keyName(keyId).key(key).precedence(precedence).build();

    if (keyIsPresentInList(rbelKey)) {
      log.trace("Skipping adding key: Key is already known!");
    } else {
      keyList.add(rbelKey);

      log.debug("Added key {} (Now there are {} keys known)", keyId, keyList.size());
    }

    return rbelKey;
  }

  private synchronized boolean keyIsPresentInList(RbelKey key) {
    return keyList.stream().anyMatch(oldKey -> oldKey.equals(key));
  }

  public synchronized Stream<RbelKey> getAllKeys() {
    return new ArrayList<>(keyList).stream().sorted(Comparator.comparing(RbelKey::getPrecedence));
  }

  public synchronized Optional<RbelKey> findCorrespondingPrivateKey(String rbelKey) {
    return getAllKeys()
        .filter(candidate -> candidate.getMatchingPublicKey().isPresent())
        .filter(
            candidate ->
                Objects.equals(candidate.getMatchingPublicKey().get().getKeyName(), rbelKey))
        .findFirst();
  }

  public synchronized Optional<RbelKey> findKeyByName(String keyName) {
    return getAllKeys()
        .filter(candidate -> candidate.getKeyName() != null)
        .filter(candidate -> candidate.getKeyName().equals(keyName))
        .findFirst();
  }
}
