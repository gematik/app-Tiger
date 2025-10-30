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
package de.gematik.rbellogger.facets.vau.vau3;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.converter.ConverterInfo;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.RbelMapFacet;
import de.gematik.rbellogger.data.core.RbelNestedFacet;
import de.gematik.rbellogger.data.core.RbelNoteFacet;
import de.gematik.rbellogger.data.core.RbelRootFacet;
import de.gematik.rbellogger.facets.http.RbelHttpMessageFacet;
import de.gematik.rbellogger.facets.jackson.RbelCborFacet;
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
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.pqc.jcajce.provider.kyber.BCKyberPublicKey;
import org.testcontainers.shaded.org.bouncycastle.pqc.crypto.crystals.kyber.KyberParameters;
import org.testcontainers.shaded.org.bouncycastle.pqc.crypto.crystals.kyber.KyberPublicKeyParameters;

@ConverterInfo(onlyActivateFor = "epa3-vau")
@Slf4j
public class RbelVauEpa3Converter extends RbelConverterPlugin {

  // Constants for complete VAU cipher message: (see A_24628 to A_24633 in gemSpec_Krypt V2.30.0)
  private static final int HEADER_VERSION_INDEX = 0;
  private static final int HEADER_PU_INDEX = 1;
  private static final int HEADER_REQ_INDEX = 2;
  private static final int HEADER_REQ_COUNTER_INDEX = 3;
  private static final int HEADER_REQ_COUNTER_LENGTH = 8;
  private static final int HEADER_KEY_ID_INDEX =
      HEADER_REQ_COUNTER_INDEX + HEADER_REQ_COUNTER_LENGTH;
  private static final int HEADER_KEY_ID_LENGTH = 32;

  private static final int BODY_INDEX = HEADER_KEY_ID_INDEX + HEADER_KEY_ID_LENGTH;
  private static final int BODY_IV_LENGTH = 12;
  private static final int BODY_CT_INDEX = BODY_INDEX + BODY_IV_LENGTH;

  private static final String VAU_3_HANDSHAKE_S_K1_C2S = "vau3_handshake_s_k1_c2s_";
  private static final String VAU_DEBUG_K1_C2S = "VAU-DEBUG-S_K1_c2s";
  private static final String VAU_DEBUG_K1_S2C = "VAU-DEBUG-S_K1_s2c";
  private static final String K1_S2C_NOTE = "S_K1_s2c, absent in a real-life implementation";
  private static final String VAU_3_PAYLOAD_KEYS = "vau_non_pu_tracing_";
  private static final String AEAD_CT_KEY_CONFIRMATION_NOTE =
      "Decrypted AEAD_ct_key_confirmation. This is the server's transcript hash. Decryption for"
          + " clarification purposes only. In a real-life implementation, this would be done by the"
          + " client.";
  public static final String CONTENT = "content";

  @Override
  public void consumeElement(RbelElement element, RbelConversionExecutor context) {
    context.waitForAllElementsBeforeGivenToBeParsed(element.findRootElement());
    if (element.hasFacet(RbelCborFacet.class)) {
      tryToParseVauEpa3HandshakeMessage(element, context);
    } else if (element.getParentNode() != null
        && element.getParentNode().hasFacet(RbelHttpMessageFacet.class)) {
      tryToExtractVauNonPuTracingKeys(element, context);
      tryToParseVauEpa3Message(element, context);
    }
  }

  private void tryToParseVauEpa3Message(RbelElement element, RbelConversionExecutor context) {
    context
        .getRbelKeyManager()
        .getAllKeys()
        .filter(key -> key.getKey() instanceof SecretKeySpec)
        .filter(key -> key.getKey().getAlgorithm().equals("AES"))
        .filter(key -> key.getKeyName().startsWith(VAU_3_PAYLOAD_KEYS))
        .forEach(key -> decryptEpa3VauSuccessfull(element, key.getKey(), context)); // NOSONAR
  }

  private boolean decryptEpa3VauSuccessfull(
      RbelElement element, Key key, RbelConversionExecutor context) {
    try {
      final byte[] rawContent = element.getRawContent();
      // These numbers are derived from A_24628 to A_24633 in gemSpec_Krypt V2.30.0
      byte[] header = ArrayUtils.subarray(rawContent, 0, BODY_INDEX);
      byte[] iv = ArrayUtils.subarray(rawContent, BODY_INDEX, BODY_INDEX + BODY_IV_LENGTH);
      byte[] ct = ArrayUtils.subarray(rawContent, BODY_CT_INDEX, rawContent.length);
      final byte[] cleartext = performActualDecryption(key, iv, ct, header);
      if (log.isTraceEnabled()) {
        log.trace("Decrypted VAU EPA3: {}", new String(cleartext));
      }
      final RbelElement headerElement = context.convertElement(header, element);
      final byte[] reqCounterBytes =
          Arrays.copyOfRange(
              header,
              HEADER_REQ_COUNTER_INDEX,
              HEADER_REQ_COUNTER_INDEX + HEADER_REQ_COUNTER_LENGTH);
      headerElement.addFacet(
          new RbelMapFacet(
              new RbelMultiMap<RbelElement>()
                  .with(
                      "version", RbelElement.wrap(new byte[] {header[0]}, headerElement, header[0]))
                  .with(
                      "pu",
                      RbelElement.wrap(
                          new byte[] {header[HEADER_PU_INDEX]},
                          headerElement,
                          header[HEADER_PU_INDEX]))
                  .with(
                      "req",
                      RbelElement.wrap(
                          new byte[] {header[HEADER_REQ_INDEX]},
                          headerElement,
                          header[HEADER_REQ_INDEX]))
                  .with(
                      "reqCtr",
                      RbelElement.wrap(
                          reqCounterBytes,
                          headerElement,
                          ByteBuffer.wrap(reqCounterBytes).getLong()))
                  .with(
                      "keyId",
                      RbelElement.wrap(
                          Arrays.copyOfRange(
                              header,
                              HEADER_KEY_ID_INDEX,
                              HEADER_KEY_ID_INDEX + HEADER_KEY_ID_LENGTH),
                          headerElement,
                          new BigInteger(
                              Arrays.copyOfRange(
                                  header,
                                  HEADER_KEY_ID_INDEX,
                                  HEADER_KEY_ID_INDEX + HEADER_KEY_ID_LENGTH))))));
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

  private void tryToExtractVauNonPuTracingKeys(
      RbelElement element, RbelConversionExecutor context) {
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

  private void tryToParseVauEpa3HandshakeMessage(
      RbelElement element, RbelConversionExecutor context) {
    try {
      final Optional<RbelElement> messageType = element.getFirst("MessageType");
      if (messageType.isPresent()) {
        String messageTypeContent =
            messageType.get().getFirst(CONTENT).map(RbelElement::getRawStringContent).orElse("");
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

  private void parseM3(RbelElement element, RbelConversionExecutor context) {
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

  private void parseM2(RbelElement element, RbelConversionExecutor context) {
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

  private void parseM4(RbelElement element, RbelConversionExecutor context) {
    final RbelElement ctKeyConfirmation =
        element.getFirst("AEAD_ct_key_confirmation").orElseThrow();

    final var encodedHashBytes =
        ctKeyConfirmation.getFirst(CONTENT).map(RbelElement::getContent).orElseThrow();

    extractKeyFromHttpHeader(element, "VAU-DEBUG-S_K2_s2c_keyConfirmation", K1_S2C_NOTE)
        .map(key -> new SecretKeySpec(key, "AES"))
        .flatMap(key -> CryptoUtils.decrypt(encodedHashBytes, key))
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

  private static void manageClientToServerKey(RbelElement element, RbelConversionExecutor context) {
    extractKeyFromHttpHeader(
            element, VAU_DEBUG_K1_C2S, "S_K1_c2s, absent in a real-life implementation")
        .map(key -> new SecretKeySpec(key, "AES"))
        .map(key -> new RbelKey(key, VAU_3_HANDSHAKE_S_K1_C2S + UUID.randomUUID(), 0))
        .ifPresent(rbelKey -> context.getRbelKeyManager().addKey(rbelKey));
  }

  private boolean tryToDecipherAeadCt(
      RbelElement rbelElement, RbelConversionExecutor context, SecretKeySpec aesKey) {
    final Optional<RbelElement> contentNode = rbelElement.getFirst(CONTENT);
    if (contentNode.isEmpty()) {
      return false;
    }
    final Optional<byte[]> decrypt =
        CryptoUtils.decrypt(contentNode.get().getContent(), aesKey, 12, 16);
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

  private void parseM1(RbelElement element, RbelConversionExecutor context) {
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

  private void tryToAddEccKeyToKeyManager(RbelElement element, RbelConversionExecutor context) {
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

  @SneakyThrows
  private Key toKyberPublicKey(RbelElement element) {
    return new BCKyberPublicKey(
        SubjectPublicKeyInfo.getInstance(
            new KyberPublicKeyParameters(KyberParameters.kyber768, element.getRawContent())));
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
