/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelJsonFacet;
import de.gematik.rbellogger.data.facet.RbelNoteFacet;
import de.gematik.rbellogger.data.facet.RbelNoteFacet.NoteStyling;
import de.gematik.rbellogger.key.RbelKey;
import de.gematik.rbellogger.key.RbelVauKey;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.crypto.DataLengthException;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.HKDFParameters;
import org.bouncycastle.jce.spec.ECNamedCurveSpec;
import org.bouncycastle.util.encoders.Hex;

@Slf4j
public class RbelVauKeyDeriver implements RbelConverterPlugin {

    private static final String KEY_ID = "KeyID";
    private static final String AES_256_GCM_KEY = "AES-256-GCM-Key";
    private static final String AES_256_GCM_KEY_SERVER_TO_CLIENT = "AES-256-GCM-Key-Server-to-Client";
    private static final String AES_256_GCM_KEY_CLIENT_TO_SERVER = "AES-256-GCM-Key-Client-to-Server";

    @Override
    public void consumeElement(RbelElement rbelElement, RbelConverter converter) {
        final Optional<PublicKey> otherSidePublicKey = Optional.ofNullable(rbelElement)
            .filter(el -> el.hasFacet(RbelJsonFacet.class))
            .flatMap(json -> json.getFirst("PublicKey"))
            .flatMap(this::publicKeyFromJsonKey);
        if (otherSidePublicKey.isEmpty()) {
            return;
        }
        log.trace("Found otherside public key");

        for (Iterator<RbelKey> it = converter.getRbelKeyManager().getAllKeys().iterator(); it.hasNext(); ) {
            RbelKey rbelKey = it.next();
            final Optional<PrivateKey> privateKey = rbelKey.retrieveCorrespondingKeyPair()
                .map(KeyPair::getPrivate)
                .filter(PrivateKey.class::isInstance)
                .map(PrivateKey.class::cast)
                .or(() -> Optional.of(rbelKey.getKey())
                    .filter(PrivateKey.class::isInstance)
                    .map(PrivateKey.class::cast));
            if (privateKey.isEmpty()) {
                continue;
            }
            log.debug("Trying key derivation with {}...", rbelKey.getKeyName());
            final List<RbelKey> derivedKeys = keyDerivation(otherSidePublicKey.get(), privateKey.get(), rbelKey);
            if (derivedKeys.isEmpty()) {
                continue;
            }
            for (RbelKey derivedKey : derivedKeys) {
                if (converter.getRbelKeyManager()
                    .findKeyByName(derivedKey.getKeyName())
                    .isEmpty()) {
                    log.trace("Adding VAU key");
                    converter.getRbelKeyManager().addKey(derivedKey);
                }
            }
            rbelElement.addFacet(RbelNoteFacet.builder()
                .style(NoteStyling.INFO)
                .value(rbelElement.getUuid() + " Added keys with name '" + rbelKey.getKeyName() + "'")
                .build());
        }
    }

    private Optional<PublicKey> publicKeyFromJsonKey(RbelElement element) {
        try {
            return Optional.ofNullable(
                KeyFactory.getInstance("ECDSA", "BC")
                    .generatePublic(new X509EncodedKeySpec(extractBinaryDataFromElement(element))));
        } catch (Exception e) {
            log.debug("Exception while converting Public Key {}:", element.getRawStringContent(), e);
            return Optional.empty();
        }
    }

    private byte[] extractBinaryDataFromElement(RbelElement element) {
        return Base64.getDecoder().decode(element.getRawStringContent()
            .replace("\"", ""));
    }

    private List<RbelKey> keyDerivation(PublicKey otherSidePublicKey, PrivateKey privateKey, RbelKey parentKey) {
        try {
            if (!(otherSidePublicKey instanceof ECPublicKey)) {
                return List.of();
            }
            ECPublicKey ephemeralPublicKeyClientBC = (ECPublicKey) otherSidePublicKey;
            ECNamedCurveSpec spec = (ECNamedCurveSpec) ephemeralPublicKeyClientBC.getParams();
            if (!"brainpoolP256r1".equals(spec.getName())) {
                return List.of();
            }
            log.trace("Performing ECKA with {} and {}",
                Base64.getEncoder().encodeToString(privateKey.getEncoded()),
                Base64.getEncoder().encodeToString(otherSidePublicKey.getEncoded()));
            byte[] sharedSecret = ecka(privateKey, otherSidePublicKey);
            log.trace("shared secret: " + Hex.toHexString(sharedSecret));
            byte[] keyId = hkdf(sharedSecret, KEY_ID, 256);
            log.trace("keyID: " + Hex.toHexString(keyId));
            return List.of(
                mapToRbelKey(AES_256_GCM_KEY_CLIENT_TO_SERVER, "_client", keyId, sharedSecret, parentKey),
                mapToRbelKey(AES_256_GCM_KEY_SERVER_TO_CLIENT, "_server", keyId, sharedSecret, parentKey),
                mapToRbelKey(AES_256_GCM_KEY, "_old", keyId, sharedSecret, parentKey));
        } catch (Exception e) {
            return List.of();
        }
    }

    private RbelKey mapToRbelKey(String deriver, String suffix, byte[] keyId, byte[] sharedSecret, RbelKey parentKey) {
        var keyRawBytes = hkdf(sharedSecret, deriver, 256);
        log.trace("symKey: {}", Hex.toHexString(keyRawBytes));
        return new RbelVauKey(new SecretKeySpec(keyRawBytes, "AES"),
            Hex.toHexString(keyId) + suffix, 0, parentKey);
    }

    private byte[] ecka(PrivateKey prk, PublicKey puk) throws Exception {
        byte[] sharedSecret;
        KeyAgreement ka = KeyAgreement.getInstance("ECDH", "BC");
        ka.init(prk);
        ka.doPhase(puk, true);
        sharedSecret = ka.generateSecret();
        return sharedSecret;
    }

    private byte[] hkdf(byte[] ikm, String info, int length) throws IllegalArgumentException, DataLengthException {
        return hkdf(ikm, info.getBytes(StandardCharsets.UTF_8), length);
    }

    private byte[] hkdf(byte[] ikm, byte[] info, int length) throws IllegalArgumentException, DataLengthException {
        HKDFBytesGenerator hkdf = new HKDFBytesGenerator(new SHA256Digest());
        hkdf.init(new HKDFParameters(ikm, null, info));
        byte[] okm = new byte[length / 8];
        hkdf.generateBytes(okm, 0, length / 8);
        return okm;
    }
}
