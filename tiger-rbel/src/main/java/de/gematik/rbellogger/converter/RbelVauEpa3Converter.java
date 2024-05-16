/*
 * Copyright (c) 2024 gematik GmbH
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

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.facet.*;
import de.gematik.rbellogger.key.RbelKey;
import de.gematik.rbellogger.key.RbelKeyManager;
import de.gematik.rbellogger.util.CryptoUtils;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.*;
import java.util.*;
import java.util.stream.Stream;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.pqc.crypto.crystals.kyber.KyberParameters;
import org.bouncycastle.pqc.crypto.crystals.kyber.KyberPublicKeyParameters;
import org.bouncycastle.pqc.jcajce.provider.kyber.BCKyberPublicKey;

@Slf4j
public class RbelVauEpa3Converter implements RbelConverterPlugin {

  private static final String VAU_3_HANDSHAKE_S_K1_C2S = "vau3_handshake_s_k1_c2s_";
  private static final String VAU_DEBUG_K1_C2S = "VAU-DEBUG-S_K1_c2s";
  private static final String VAU_DEBUG_K1_S2C = "VAU-DEBUG-S_K1_s2c";
  private static final String K1_S2C_NOTE = "S_K1_s2c, absent in a real-life implementation";
  private static final String VAU_3_PAYLOAD_KEYS = "vau_non_pu_tracing_";
  private static final String AEAD_CT_KEY_CONFIRMATION_NOTE =
      "Decrypted AEAD_ct_key_confirmation. This is the server's transcript hash. Decryption for"
          + " clarification purposes only. In a real-life implementation, this would be done by the"
          + " client.";

  @Override
  public void consumeElement(RbelElement element, RbelConverter context) {
    if (log.isTraceEnabled()) {
      log.trace("Trying to decipher '{}'...", element.getRawStringContent());
    }
    context.waitForAllElementsBeforeGivenToBeParsed(element.findRootElement());
    if (element.hasFacet(RbelCborFacet.class)) {
      tryToParseVauEpa3HandshakeMessage(element, context);
    } else if (element.getParentNode() != null
        && element.getParentNode().hasFacet(RbelHttpMessageFacet.class)) {
      tryToExtractVauNonPuTracingKeys(element, context);
      tryToParseVauEpa3Message(element, context);
    }
  }

  private void tryToParseVauEpa3Message(RbelElement element, RbelConverter context) {
    context
        .getRbelKeyManager()
        .getAllKeys()
        .filter(key -> key.getKey() instanceof SecretKeySpec)
        .filter(key -> key.getKey().getAlgorithm().equals("AES"))
        .filter(key -> key.getKeyName().startsWith(VAU_3_PAYLOAD_KEYS))
        .anyMatch(key -> decryptEpa3VauSuccessfull(element, key.getKey(), context)); //NOSONAR
  }

  private boolean decryptEpa3VauSuccessfull(RbelElement element, Key key, RbelConverter context) {
    try {
      final byte[] rawContent = element.getRawContent();
      byte[] header = ArrayUtils.subarray(rawContent, 0, 43);
      byte[] iv = ArrayUtils.subarray(rawContent, 43, 43 + 12);
      byte[] ct = ArrayUtils.subarray(rawContent, 55, rawContent.length);
      final byte[] cleartext = performActualDecryption(key, iv, ct, header);
      if (log.isTraceEnabled()) {
        log.trace("Decrypted VAU EPA3: {}", new String(cleartext));
      }
      final RbelElement headerElement = context.convertElement(header, element);
      final byte[] reqCounterBytes = Arrays.copyOfRange(header, 3, 3 + 8);
      headerElement.addFacet(
          new RbelMapFacet(
              new RbelMultiMap<RbelElement>()
                  .with("version", new RbelElement(Arrays.copyOfRange(header, 0, 1), headerElement))
                  .with("pu", new RbelElement(Arrays.copyOfRange(header, 1, 2), headerElement))
                  .with("req", new RbelElement(Arrays.copyOfRange(header, 2, 3), headerElement))
                  .with(
                      "reqCtr",
                      RbelElement.wrap(
                          reqCounterBytes,
                          headerElement,
                          ByteBuffer.wrap(reqCounterBytes).getInt()))
                  .with(
                      "keyId",
                      new RbelElement(Arrays.copyOfRange(header, 11, 43), headerElement))));
      final RbelElement cleartextElement = context.convertElement(cleartext, element);
      element.addFacet(new RbelVau3EncryptionFacet(cleartextElement, headerElement));
      return true;
    } catch (Exception e) {
      log.trace("Failed to parse VAU EPA3: ", e);
      return false;
    }
  }

  @SneakyThrows
  private static byte[] performActualDecryption(Key key, byte[] iv, byte[] ciphertext, byte[] ad) {
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); // NOSONAR
    cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
    cipher.updateAAD(ad);
    return cipher.doFinal(ciphertext);
  }

  private void tryToExtractVauNonPuTracingKeys(RbelElement element, RbelConverter context) {
    Optional.ofNullable(element.getParentNode())
        .flatMap(el -> el.getFacet(RbelHttpMessageFacet.class))
        .map(RbelHttpMessageFacet::getHeader)
        .flatMap(header -> header.getFirst("VAU-nonPU-Tracing"))
        .map(RbelElement::getRawStringContent)
        .map(keyString -> keyString.split(" "))
        .stream()
        .flatMap(Stream::of)
        .map(Base64.getDecoder()::decode)
        .map(key -> new SecretKeySpec(key, "AES"))
        .map(key -> new RbelKey(key, VAU_3_PAYLOAD_KEYS + UUID.randomUUID(), 0))
        .forEach(key -> context.getRbelKeyManager().addKey(key));
  }

  private void tryToParseVauEpa3HandshakeMessage(RbelElement element, RbelConverter context) {
    try {
      final Optional<RbelElement> messageType = element.getFirst("MessageType");
      if (messageType.isPresent()) {
        String messageTypeContent =
            messageType.get().getFirst("content").map(RbelElement::getRawStringContent).orElse("");
        switch (messageTypeContent) {
          case "M1" -> parseM1(element, context);
          case "M2" -> parseM2(element, context);
          case "M3" -> parseM3(element, context);
          case "M4" -> parseM4(element, context);
          default ->
              element.addFacet(
                  new RbelNoteFacet("Unknown VAU EPA3 message type: " + messageTypeContent));
        }
      }
    } catch (RuntimeException e) {
      log.trace("Failed to parse VAU EPA3: {}", e.getMessage());
    }
  }

  private void parseM3(RbelElement element, RbelConverter context) {
    final Optional<RbelElement> aeadCtKeyConfirmation =
        element.getFirst("AEAD_ct_key_confirmation");
    final Optional<RbelElement> aeadCt = element.getFirst("AEAD_ct");
    if (aeadCtKeyConfirmation.isEmpty() || aeadCt.isEmpty()) {
      return;
    }
    aeadCtKeyConfirmation.get().addFacet(new RbelNoteFacet("aead_ciphertext_key_confirmation"));
    aeadCt.get().addFacet(new RbelNoteFacet("aead_ciphertext_msg_3"));
    for (RbelKey key : context.getRbelKeyManager().getAllKeys().toList())
      if (key.getKey() instanceof SecretKeySpec secretKeySpec
          && key.getKey().getAlgorithm().equals("AES")
          && key.getKeyName().startsWith(VAU_3_HANDSHAKE_S_K1_C2S)
          && tryToDecipherAeadCt(aeadCt.get(), context, secretKeySpec)) {
        break;
      }
  }

  private void parseM2(RbelElement element, RbelConverter context) {
    final Optional<RbelElement> ecdhCt = element.getFirst("ECDH_ct");
    final Optional<RbelElement> kyber768Ct = element.getFirst("Kyber768_ct");
    final Optional<RbelElement> aeadCt = element.getFirst("AEAD_ct");
    if (ecdhCt.isEmpty() || kyber768Ct.isEmpty() || aeadCt.isEmpty()) {
      return;
    }
    ecdhCt.get().addFacet(new RbelNoteFacet("ECC ciphertext of the server for this handshake"));
    kyber768Ct
        .get()
        .addFacet(new RbelNoteFacet("Kyber768 ciphertext of the server for this handshake"));
    aeadCt.get().addFacet(new RbelNoteFacet("aead_ciphertext_msg_2"));
    final Optional<byte[]> s2cKey =
        extractKeyFromHttpHeader(aeadCt.get(), VAU_DEBUG_K1_S2C, K1_S2C_NOTE);
    if (s2cKey.isEmpty()) return;

    tryToDecipherAeadCt(aeadCt.get(), context, new SecretKeySpec(s2cKey.get(), "AES"));
    manageClientToServerKey(element, context);
  }

  private void parseM4(RbelElement element, RbelConverter context) {
    final RbelElement ctKeyConfirmation =
        element.getFirst("AEAD_ct_key_confirmation").orElseThrow();

    final byte[] rawEncodedHashBytes =
        ctKeyConfirmation.getFirst("content").map(RbelElement::getRawContent).orElseThrow();

    extractKeyFromHttpHeader(element, "VAU-DEBUG-S_K2_s2c_keyConfirmation", K1_S2C_NOTE)
        .map(key -> new SecretKeySpec(key, "AES"))
        .flatMap(key -> CryptoUtils.decrypt(rawEncodedHashBytes, key))
        .ifPresent(
            decrypted -> {
              final var printableHashFacet =
                  new RbelPrintableHashFacet("SHA-256 Hash of the transcript");
              ctKeyConfirmation.addFacet(
                  new RbelNestedFacet(
                      new RbelElement(decrypted, ctKeyConfirmation)
                          .addFacet(printableHashFacet)
                          .addFacet(new RbelRootFacet<>(printableHashFacet)),
                      "decrypted"));
              ctKeyConfirmation.addFacet(new RbelNoteFacet(AEAD_CT_KEY_CONFIRMATION_NOTE));
            });
  }

  private static void manageClientToServerKey(RbelElement element, RbelConverter context) {
    extractKeyFromHttpHeader(
            element, VAU_DEBUG_K1_C2S, "S_K1_c2s, absent in a real-life implementation")
        .map(key -> new SecretKeySpec(key, "AES"))
        .map(key -> new RbelKey(key, VAU_3_HANDSHAKE_S_K1_C2S + UUID.randomUUID(), 0))
        .ifPresent(rbelKey -> context.getRbelKeyManager().addKey(rbelKey));
  }

  private boolean tryToDecipherAeadCt(
      RbelElement rbelElement, RbelConverter context, SecretKeySpec aesKey) {
    final Optional<RbelElement> contentNode = rbelElement.getFirst("content");
    if (contentNode.isEmpty()) {
      return false;
    }
    final Optional<byte[]> decrypt =
        CryptoUtils.decrypt(contentNode.get().getRawContent(), aesKey, 12, 16);
    if (decrypt.isEmpty()) {
      return false;
    }
    final RbelElement nestedElement = context.convertElement(decrypt.get(), rbelElement);
    nestedElement.addFacet(
        new RbelNoteFacet(
            "Decrypted AEAD_ct element. Decryption for clarification purposes only. "
                + "In a real-life implementation, this would be done by the client."));
    rbelElement.addFacet(new RbelNestedFacet(nestedElement, "decrypted_content"));
    return true;
  }

  private static Optional<byte[]> extractKeyFromHttpHeader(
      RbelElement rbelElement, String keyName, String keyNote) {
    return rbelElement
        .findRootElement()
        .getFacet(RbelHttpMessageFacet.class)
        .map(RbelHttpMessageFacet::getHeader)
        .flatMap(e -> e.getFirst(keyName))
        .map(e -> e.addFacet(new RbelNoteFacet(keyNote)))
        .map(RbelElement::getRawContent)
        .map(Base64.getDecoder()::decode);
  }

  private void parseM1(RbelElement element, RbelConverter context) {
    final Optional<RbelElement> ecdhPk = element.getFirst("ECDH_PK");
    final Optional<RbelElement> kyber768Pk = element.getFirst("Kyber768_PK");
    if (ecdhPk.isEmpty() || kyber768Pk.isEmpty()) {
      return;
    }
    ecdhPk.get().addFacet(new RbelNoteFacet("ECC public key of the client for this handshake"));
    kyber768Pk
        .get()
        .addFacet(
            new RbelNoteFacet(
                "Kyber public key of the client for this handshake (Concatenation of the two kyber"
                    + " parameters)"));
    tryToAddEccKeyToKeyManager(element, context);
  }

  private void tryToAddEccKeyToKeyManager(RbelElement element, RbelConverter context) {
    final RbelKeyManager keyManager = context.getRbelKeyManager();
    String uuid =
        UUID.randomUUID()
            .toString(); // Random qualifier to avoid key collisions. Bears no similarity to the IDs
    // in the actual handshake.
    element
        .getFirst("ECDH_PK")
        .map(this::toEcPublicKey)
        .ifPresent(key -> keyManager.addKey("vau3_handshake_client_ecdh_pk_" + uuid, key, 0));
    element
        .getFirst("Kyber768_PK")
        .map(this::toKyberPublicKey)
        .ifPresent(key -> keyManager.addKey("vau3_handshake_client_kyber_pk" + uuid, key, 0));
  }

  private Key toKyberPublicKey(RbelElement element) {
    return new BCKyberPublicKey(
        new KyberPublicKeyParameters(KyberParameters.kyber768, element.getRawContent()));
  }

  @SneakyThrows
  public ECPublicKey toEcPublicKey(RbelElement element) {
    final RbelElement xElement = element.getFirst("x").orElseThrow();
    final RbelElement yElement = element.getFirst("y").orElseThrow();
    var x = new BigInteger(1, xElement.getRawContent(), 0, xElement.getRawContent().length);
    var y = new BigInteger(1, yElement.getRawContent(), 0, yElement.getRawContent().length);
    X9ECParameters ecp = SECNamedCurves.getByName("secp256r1");
    ECCurve ecCurve = ecp.getCurve();
    ECPoint ecPoint = ecCurve.createPoint(x, y);

    ECParameterSpec ecParameterSpec = ECNamedCurveTable.getParameterSpec("secp256r1");
    ECPublicKeySpec ecKeySpec = new ECPublicKeySpec(ecPoint, ecParameterSpec);
    KeyFactory keyFactory = KeyFactory.getInstance("ECDH", "BC");
    return (ECPublicKey) keyFactory.generatePublic(ecKeySpec);
  }
}
