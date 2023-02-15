package de.gematik.rbellogger.key;

import de.gematik.test.tiger.common.pki.TigerPkiIdentity;
import java.security.Key;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.RandomStringUtils;

public class IdentityBackedRbelKey extends RbelKey {

    private final TigerPkiIdentity identity;

    public static List<IdentityBackedRbelKey> generateRbelKeyPairForIdentity(TigerPkiIdentity identity) {
        final String keyId = identity.getKeyId()
            .orElseGet(() -> RandomStringUtils.randomAlphabetic(8)); //NOSONAR
        final IdentityBackedRbelKey pubKey = new IdentityBackedRbelKey(identity.getCertificate().getPublicKey(),
            "puk_" + keyId,
            RbelKey.PRECEDENCE_KEY_FOLDER, Optional.empty(), identity);
        final IdentityBackedRbelKey prvKey = new IdentityBackedRbelKey(identity.getPrivateKey(),
            "prk_" + keyId,
            RbelKey.PRECEDENCE_KEY_FOLDER, Optional.of(pubKey), identity);
        return List.of(pubKey, prvKey);
    }

    private IdentityBackedRbelKey(Key key, String keyName, int precedence, Optional<RbelKey> matchingPublicKey, TigerPkiIdentity identity) {
        super(key, keyName, precedence, matchingPublicKey);
        this.identity = identity;
    }

    public X509Certificate getCertificate() {
        return identity.getCertificate();
    }

    public PrivateKey getPrivateKey() {
        return identity.getPrivateKey();
    }

    public PublicKey getPublicKey() {
        return identity.getCertificate().getPublicKey();
    }
}
