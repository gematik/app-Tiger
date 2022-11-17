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
import de.gematik.rbellogger.data.facet.RbelRootFacet;
import de.gematik.rbellogger.data.facet.RbelVauErpFacet;
import de.gematik.rbellogger.key.RbelKey;
import de.gematik.rbellogger.util.CryptoUtils;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.DecoderException;
import org.bouncycastle.util.encoders.Hex;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
public class RbelErpVauDecrpytionConverter implements RbelConverterPlugin {

    @Override
    public void consumeElement(RbelElement element, RbelConverter context) {
        log.trace("Trying to decipher '{}'...", element.getRawStringContent());
        decipherVauMessage(element, context)
            .ifPresent(vauMsg -> {
                element.addFacet(vauMsg);
                element.addFacet(new RbelRootFacet<>(vauMsg));
            });
    }

    private Optional<byte[]> decrypt(byte[] encMessage, ECPrivateKey secretKey) {
        try {
            if (encMessage.length < 1 || encMessage[0] != 1) {
                return Optional.empty();
            }
            ECPublicKey otherSidePublicKey = extractPublicKeyFromVauMessage(encMessage);
            byte[] sharedSecret = CryptoUtils.ecka(secretKey, otherSidePublicKey);
            byte[] aesKeyBytes = CryptoUtils.hkdf(sharedSecret, "ecies-vau-transport", 16);
            SecretKey aesKey = new SecretKeySpec(aesKeyBytes, "AES");

            final byte[] ciphertext = Arrays.copyOfRange(encMessage, 1 + 32 + 32, encMessage.length);

            log.trace("Decrypting. AesKey '{}' and ciphertext {}",
                Base64.getEncoder().encodeToString(aesKeyBytes),
                Base64.getEncoder().encodeToString(ciphertext));

            return CryptoUtils.decrypt(ciphertext, aesKey);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private ECPublicKey extractPublicKeyFromVauMessage(byte[] encMessage)
        throws NoSuchAlgorithmException, InvalidKeySpecException {
        final java.security.spec.ECPoint ecPoint = new java.security.spec.ECPoint(
            new BigInteger(1, Arrays.copyOfRange(encMessage, 1, 1 + 32)),
            new BigInteger(1, Arrays.copyOfRange(encMessage, 1 + 32, 1 + 32 + 32)));
        final ECPublicKeySpec keySpec = new ECPublicKeySpec(ecPoint, BrainpoolCurves.BP256);
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(keySpec);
    }

    private Optional<RbelVauErpFacet> decipherVauMessage(RbelElement element, RbelConverter converter) {
        final List<RbelKey> potentialVauKeys = converter.getRbelKeyManager().getAllKeys()
            .filter(key -> key.getKey() instanceof ECPrivateKey
                || key.getKey() instanceof SecretKey)
            .collect(Collectors.toList());
        for (RbelKey rbelKey : potentialVauKeys) {
            final Optional<byte[]> decryptedBytes = decrypt(element.getRawContent(), rbelKey.getKey());
            if (decryptedBytes.isPresent()) {
                try {
                    log.trace("Succesfully deciphered VAU message! ({})", new String(decryptedBytes.get(), UTF_8));
                    if (isVauResponse(decryptedBytes)) {
                        return buildVauMessageFromCleartextResponse(converter, decryptedBytes.get(),
                            element.getRawContent(), rbelKey, element);
                    } else {
                        return buildVauMessageFromCleartextRequest(converter, decryptedBytes.get(),
                            element.getRawContent(), rbelKey, element);
                    }
                } catch (RuntimeException e) {
                    log.error("Exception while deciphering VAU message:", e);
                    throw e;
                }
            }
        }
        return Optional.empty();
    }

    private boolean isVauResponse(Optional<byte[]> decryptedBytes) {
        return decryptedBytes
            .map(bytes -> new String(bytes, UTF_8))
            .map(s -> s.split("1 [\\da-f]{32} ").length)
            .map(length -> length > 1)
            .orElse(false);
    }

    private Optional<byte[]> decrypt(byte[] content, Key key) {
        if (key instanceof ECPrivateKey) {
            return decrypt(content, (ECPrivateKey) key);
        } else if (key instanceof SecretKey) {
            return CryptoUtils.decrypt(content, key, 96 / 8, 128 / 8);
        } else {
            throw new RuntimeException("Unexpected key-type encountered (" + key.getClass().getSimpleName() + ")");
        }
    }

    private Optional<RbelVauErpFacet> buildVauMessageFromCleartextRequest(RbelConverter converter,
                                                                          byte[] decryptedBytes, byte[] encryptedMessage, RbelKey decryptionKey, RbelElement parentNode) {
        String[] vauMessageParts = new String(decryptedBytes, UTF_8).split(" ", 5);
        final SecretKeySpec responseKey = buildAesKeyFromHex(vauMessageParts[3]);
        converter.getRbelKeyManager().addKey("VAU Response-Key", responseKey, 0);
        return Optional.of(RbelVauErpFacet.builder()
            .message(converter.convertElement(vauMessageParts[4], parentNode))
            .encryptedMessage(RbelElement.wrap(encryptedMessage, parentNode, null))
            .requestId(RbelElement.wrap(parentNode, vauMessageParts[2]))
            .pVersionNumber(RbelElement.wrap(parentNode, Integer.parseInt(vauMessageParts[0])))
            .responseKey(RbelElement.wrap(parentNode, responseKey))
            .keyIdUsed(RbelElement.wrap(parentNode, decryptionKey.getKeyName()))
            .decryptedPString(RbelElement.wrap(decryptedBytes, parentNode, new String(decryptedBytes, UTF_8)))
            .keyUsed(Optional.of(decryptionKey))
            .build());
    }

    private Optional<RbelVauErpFacet> buildVauMessageFromCleartextResponse(
        RbelConverter converter, byte[] decryptedBytes, byte[] encryptedMessage, RbelKey keyUsed, RbelElement parentNode) {
        String[] vauMessageParts = new String(decryptedBytes, UTF_8).split(" ", 3);
        return Optional.of(RbelVauErpFacet.builder()
            .message(converter.convertElement(vauMessageParts[2], parentNode))
            .encryptedMessage(RbelElement.wrap(parentNode, encryptedMessage))
            .requestId(RbelElement.wrap(parentNode, vauMessageParts[1]))
            .pVersionNumber(RbelElement.wrap(parentNode, Integer.parseInt(vauMessageParts[0])))
            .keyIdUsed(RbelElement.wrap(parentNode, keyUsed.getKeyName()))
            .keyUsed(Optional.of(keyUsed))
            .decryptedPString(RbelElement.wrap(parentNode, new String(decryptedBytes, UTF_8)))
            .build());
    }

    private SecretKeySpec buildAesKeyFromHex(String hexEncodedKey) {
        try {
            return new SecretKeySpec(Hex.decode(hexEncodedKey), "AES");
        } catch (DecoderException e) {
            throw new RuntimeException("Error during Key decoding from '" + hexEncodedKey + "'", e);
        }
    }
}
