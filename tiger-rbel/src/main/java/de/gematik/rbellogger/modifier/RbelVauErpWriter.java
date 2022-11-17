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

package de.gematik.rbellogger.modifier;

import de.gematik.rbellogger.converter.brainpool.BrainpoolCurves;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelVauErpFacet;
import de.gematik.rbellogger.key.RbelKey;
import de.gematik.rbellogger.util.CryptoUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@RequiredArgsConstructor
@Slf4j
public class RbelVauErpWriter implements RbelElementWriter {

    @SneakyThrows
    public static byte[] encrypt(byte[] input, byte[] key) {
        final byte[] iv = new byte[12];
        ThreadLocalRandom.current().nextBytes(iv);//NOSONAR
        SecretKey secretKey = new SecretKeySpec(key, "AES");

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding", "BC");//NOSONAR

        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(16 * 8, iv));

        byte[] cipherTextPlusTag = cipher.doFinal(input);

        byte[] encMessage = Arrays.copyOf(iv, 12 + cipherTextPlusTag.length);
        System.arraycopy(cipherTextPlusTag, 0, encMessage, 12, cipherTextPlusTag.length);

        return encMessage;
    }

    @Override
    public boolean canWrite(RbelElement oldTargetElement) {
        return oldTargetElement.hasFacet(RbelVauErpFacet.class)
            && oldTargetElement.getFacet(RbelVauErpFacet.class)
            .flatMap(RbelVauErpFacet::getKeyUsed)
            .isPresent();
    }

    @SneakyThrows
    @Override
    public byte[] write(RbelElement oldTargetElement, RbelElement oldTargetModifiedChild, byte[] newContent) {
        final Optional<RbelKey> decryptionKey = oldTargetElement.getFacet(RbelVauErpFacet.class)
            .flatMap(RbelVauErpFacet::getKeyUsed);
        if (decryptionKey.isEmpty()) {
            throw new RuntimeException("Error while trying to write VAU Erp Message: No decryption-key found!");
        }
        if (decryptionKey.get().getKey() instanceof ECPrivateKey) {
            return rewriteErpVauRequest(oldTargetElement, newContent, decryptionKey.get());
        } else {
            return rewriteErpVauResponse(oldTargetElement, newContent, decryptionKey.get());
        }
    }

    private byte[] rewriteErpVauResponse(RbelElement oldTargetElement, byte[] newContent, RbelKey decryptionKey) {
        String[] pParts = oldTargetElement.getFacet(RbelVauErpFacet.class)
            .map(RbelVauErpFacet::getDecryptedPString)
            .flatMap(RbelElement::seekValue)
            .get().toString().split(" ");

        return encrypt(
            ArrayUtils.addAll((pParts[0] + " " + pParts[1] + " ").getBytes(StandardCharsets.UTF_8), newContent),
            decryptionKey.getKey().getEncoded());
    }

    @SneakyThrows()
    private byte[] rewriteErpVauRequest(RbelElement oldTargetElement, byte[] newContent, RbelKey decryptionKey) {
        String[] pParts = oldTargetElement.getFacet(RbelVauErpFacet.class)
            .map(RbelVauErpFacet::getDecryptedPString)
            .flatMap(RbelElement::seekValue)
            .get().toString().split(" ");

        ECPublicKey otherSidePublicKey = extractPublicKeyFromVauMessage(oldTargetElement.getRawContent());
        byte[] sharedSecret = CryptoUtils.ecka((PrivateKey) decryptionKey.getKey(), otherSidePublicKey);
        byte[] aesKeyBytes = CryptoUtils.hkdf(sharedSecret, "ecies-vau-transport", 16);

        byte[] newCiphertext = encrypt(
            ArrayUtils.addAll((pParts[0] + " " +
                pParts[1] + " " +
                pParts[2] + " " +
                pParts[3] + " "
            ).getBytes(StandardCharsets.UTF_8), newContent), aesKeyBytes);

        if (log.isTraceEnabled()) {
            log.trace("Encrypting. AesKey '{}' and ciphertext {}",
                Base64.getEncoder().encodeToString(aesKeyBytes),
                Base64.getEncoder().encodeToString(newCiphertext));
        }

        byte[] oldCombinedMessage = oldTargetElement.getFacet(RbelVauErpFacet.class)
            .map(RbelVauErpFacet::getEncryptedMessage)
            .map(RbelElement::getRawContent)
            .orElseThrow();

        return ArrayUtils.addAll(Arrays.copyOfRange(oldCombinedMessage, 0, 1 + 32 + 32), newCiphertext);
    }

    @SneakyThrows
    private ECPublicKey extractPublicKeyFromVauMessage(byte[] encMessage) {
        final java.security.spec.ECPoint ecPoint = new java.security.spec.ECPoint(
            new BigInteger(1, Arrays.copyOfRange(encMessage, 1, 1 + 32)),
            new BigInteger(1, Arrays.copyOfRange(encMessage, 1 + 32, 1 + 32 + 32)));
        final ECPublicKeySpec keySpec = new ECPublicKeySpec(ecPoint, BrainpoolCurves.BP256);
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(keySpec);
    }
}
