/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.key;

import java.security.Key;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
public class RbelKey {

    public static final int PRECEDENCE_X5C_HEADER_VALUE = 100;
    public static final int PRECEDENCE_KEY_FOLDER = 110;
    public static final int PRECEDENCE_JWK_VALUE = 200;

    private final Key key;
    private final String keyName;
    /**
     * The importance of this particular key. Higher value means it will be considered before potentially matching keys
     * with lower precedence.
     */
    private final int precedence;
    private final Optional<RbelKey> matchingPublicKey;

    @Builder
    public RbelKey(Key key, String keyName, int precedence, RbelKey matchingPublicKey) {
        this.key = key;
        this.keyName = keyName;
        this.precedence = precedence;
        this.matchingPublicKey = Optional.ofNullable(matchingPublicKey);
    }

    public RbelKey(Key key, String keyName, int precedence) {
        this.key = key;
        this.keyName = keyName;
        this.precedence = precedence;
        this.matchingPublicKey = Optional.empty();
    }

    public Optional<KeyPair> retrieveCorrespondingKeyPair() {
        if (key instanceof PrivateKey) {
            return matchingPublicKey
                .map(RbelKey::getKey)
                .filter(PublicKey.class::isInstance)
                .map(PublicKey.class::cast)
                .map(pubKey -> new KeyPair(pubKey, (PrivateKey) key));
        } else {
            return Optional.empty();
        }
    }
}
