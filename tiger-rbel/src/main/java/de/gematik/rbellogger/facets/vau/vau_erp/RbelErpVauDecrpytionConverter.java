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
package de.gematik.rbellogger.facets.vau.vau_erp;

import static java.nio.charset.StandardCharsets.UTF_8;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.converter.ConverterInfo;
import de.gematik.rbellogger.converter.brainpool.BrainpoolCurves;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelRootFacet;
import de.gematik.rbellogger.exceptions.RbelConversionException;
import de.gematik.rbellogger.key.RbelKey;
import de.gematik.rbellogger.util.CryptoUtils;
import de.gematik.rbellogger.util.RbelContent;
import java.math.BigInteger;
import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.DecoderException;
import org.bouncycastle.util.encoders.Hex;

@ConverterInfo(onlyActivateFor = "erp-vau")
@Slf4j
public class RbelErpVauDecrpytionConverter extends RbelConverterPlugin {

  @Override
  public void consumeElement(RbelElement element, RbelConversionExecutor context) {
    decipherVauMessage(element, context)
        .ifPresent(
            vauMsg -> {
              element.addFacet(vauMsg);
              element.addFacet(new RbelRootFacet<>(vauMsg));
            });
  }

  private Optional<RbelVauErpFacet> decipherVauMessage(
      RbelElement element, RbelConversionExecutor converter) {
    return converter
        .getRbelKeyManager()
        .getAllKeys()
        .filter(key -> key.getKey() instanceof ECPrivateKey || key.getKey() instanceof SecretKey)
        .map(key -> tryToDecipherWithKey(element, converter, key))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .findFirst();
  }

  private Optional<RbelVauErpFacet> tryToDecipherWithKey(
      RbelElement element, RbelConversionExecutor converter, RbelKey rbelKey) {
    var content = element.getContent();
    final Optional<byte[]> decryptedBytes = decrypt(content, rbelKey.getKey());
    if (decryptedBytes.isPresent()) {
      try {
        log.trace(
            "Succesfully deciphered VAU message! ({})", new String(decryptedBytes.get(), UTF_8));
        byte[] rawContent = content.toByteArray();
        if (isVauResponse(decryptedBytes)) {
          return buildVauMessageFromCleartextResponse(
              converter, decryptedBytes.get(), rawContent, rbelKey, element);
        } else {
          return buildVauMessageFromCleartextRequest(
              converter, decryptedBytes.get(), rawContent, rbelKey, element);
        }
      } catch (RuntimeException e) {
        log.error("Exception while deciphering VAU message:", e);
        throw e;
      }
    }
    return Optional.empty();
  }

  private ECPublicKey extractPublicKeyFromVauMessage(RbelContent encMessage)
      throws NoSuchAlgorithmException, InvalidKeySpecException {
    final java.security.spec.ECPoint ecPoint =
        new java.security.spec.ECPoint(
            new BigInteger(1, encMessage.toByteArray(1, 1 + 32)),
            new BigInteger(1, encMessage.toByteArray(1 + 32, 1 + 32 + 32)));
    final ECPublicKeySpec keySpec = new ECPublicKeySpec(ecPoint, BrainpoolCurves.BP256);
    return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(keySpec);
  }

  private boolean isVauResponse(Optional<byte[]> decryptedBytes) {
    return decryptedBytes
        .map(bytes -> new String(bytes, UTF_8))
        .map(s -> s.split("^1 [\\da-f]{32} ").length)
        .map(length -> length > 1)
        .orElse(false);
  }

  private Optional<byte[]> decrypt(RbelContent content, Key key) {
    if (key instanceof ECPrivateKey ecPrivateKey) {
      return decryptPrivateKey(content, ecPrivateKey);
    } else if (key instanceof SecretKey) {
      return CryptoUtils.decrypt(content, key, 96 / 8, 128 / 8);
    } else {
      throw new RbelConversionException(
          "Unexpected key-type encountered '" + key.getClass().getSimpleName() + "'");
    }
  }

  private Optional<byte[]> decryptPrivateKey(RbelContent encMessage, ECPrivateKey secretKey) {
    try {
      if (encMessage.isEmpty() || encMessage.get(0) != 1) {
        return Optional.empty();
      }
      ECPublicKey otherSidePublicKey = extractPublicKeyFromVauMessage(encMessage);
      byte[] sharedSecret = CryptoUtils.ecka(secretKey, otherSidePublicKey);
      byte[] aesKeyBytes = CryptoUtils.hkdf(sharedSecret, "ecies-vau-transport", 16);
      SecretKey aesKey = new SecretKeySpec(aesKeyBytes, "AES");

      var ciphertext = encMessage.subArray(1 + 32 + 32, encMessage.size());

      log.atTrace()
          .addArgument(() -> Base64.getEncoder().encodeToString(aesKeyBytes))
          .addArgument(() -> Base64.getEncoder().encodeToString(ciphertext.toByteArray()))
          .log("Decrypting. AesKey '{}' and ciphertext {}");

      return CryptoUtils.decrypt(ciphertext, aesKey);
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  private Optional<RbelVauErpFacet> buildVauMessageFromCleartextRequest(
      RbelConversionExecutor converter,
      byte[] decryptedBytes,
      byte[] encryptedMessage,
      RbelKey decryptionKey,
      RbelElement parentNode) {
    String[] vauMessageParts = new String(decryptedBytes, UTF_8).split(" ", 5);
    final SecretKeySpec responseKey = buildAesKeyFromHex(vauMessageParts[3]);
    converter.getRbelKeyManager().addKey("VAU Response-Key", responseKey, 0);
    return Optional.of(
        RbelVauErpFacet.builder()
            .message(converter.convertElement(vauMessageParts[4], parentNode))
            .encryptedMessage(RbelElement.wrap(encryptedMessage, parentNode, null))
            .requestId(RbelElement.wrap(parentNode, vauMessageParts[2]))
            .pVersionNumber(RbelElement.wrap(parentNode, Integer.parseInt(vauMessageParts[0])))
            .responseKey(RbelElement.wrap(parentNode, responseKey))
            .keyIdUsed(RbelElement.wrap(parentNode, decryptionKey.getKeyName()))
            .decryptedPString(
                RbelElement.wrap(decryptedBytes, parentNode, new String(decryptedBytes, UTF_8)))
            .keyUsed(Optional.of(decryptionKey))
            .build());
  }

  private Optional<RbelVauErpFacet> buildVauMessageFromCleartextResponse(
      RbelConversionExecutor converter,
      byte[] decryptedBytes,
      byte[] encryptedMessage,
      RbelKey keyUsed,
      RbelElement parentNode) {
    String[] vauMessageParts = new String(decryptedBytes, UTF_8).split(" ", 3);
    return Optional.of(
        RbelVauErpFacet.builder()
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
      throw new RbelConversionException(
          "Error during key decoding from '" + hexEncodedKey + "'", e);
    }
  }
}
