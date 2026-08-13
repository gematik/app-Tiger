/*
 *  Copyright 2021-2026 gematik GmbH
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
 * ******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */

package de.gematik.rbellogger.facets.vau;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.RbelMapFacet;
import de.gematik.rbellogger.facets.http.RbelHttpMessageFacet;
import de.gematik.rbellogger.facets.vau.asl.RbelAslEncryptionFacet;
import de.gematik.rbellogger.key.RbelKey;
import de.gematik.rbellogger.util.RbelContent;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.Key;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@Slf4j
public abstract class AbstractAslDecryptionConverter extends RbelConverterPlugin {

  // These numbers are derived from A_24628 to A_24633 in gemSpec_Krypt V2.30.0
  public static final int HEADER_LENGTH = 32 + 8 + 1 + 1 + 1;
  static final int HEADER_PU_INDEX = 1;
  static final int HEADER_REQ_INDEX = 2;
  static final int HEADER_REQ_COUNTER_INDEX = 3;
  static final int HEADER_REQ_COUNTER_LENGTH = 8;
  static final int HEADER_KEY_ID_INDEX = HEADER_REQ_COUNTER_INDEX + HEADER_REQ_COUNTER_LENGTH;
  static final int HEADER_KEY_ID_LENGTH = 32;

  static final int BODY_INDEX = HEADER_KEY_ID_INDEX + HEADER_KEY_ID_LENGTH;
  static final int BODY_IV_LENGTH = 12;
  static final int BODY_CT_INDEX = BODY_INDEX + BODY_IV_LENGTH;

  public void tryToParseVau3AslMessage(RbelElement element, RbelConversionExecutor context) {
    context
        .getRbelKeyManager()
        .getAllKeys()
        .filter(key -> key.getKey() instanceof SecretKeySpec)
        .filter(key -> key.getKey().getAlgorithm().equals("AES"))
        .filter(key -> key.getKeyName().startsWith(getKeyHeaderName()))
        .forEach(key -> decryptPayloadSuccessful(element, key.getKey(), context)); // NOSONAR
  }

  public abstract String getKeyHeaderName();

  private void decryptPayloadSuccessful(
      RbelElement element, Key key, RbelConversionExecutor context) {
    try {
      val content = element.getContent();
      val header = content.subArray(0, HEADER_LENGTH);
      val ad = header.toByteArray();
      val cleartext = decryptEncryptedContent(key, content, ad);
      val headerElement = convertHeader(element, context, header);
      val cleartextElement = context.convertElement(cleartext, element);
      element.addFacet(buildFacet(cleartextElement, headerElement));
    } catch (Exception e) {
      log.trace("Failed to parse VAU EPA3: ", e);
    }
  }

  private static RbelContent decryptEncryptedContent(Key key, RbelContent content, byte[] ad) {
    val iv = content.subArray(HEADER_LENGTH, HEADER_LENGTH + BODY_IV_LENGTH).toByteArray();
    val ct = content.subArray(BODY_CT_INDEX, content.size());
    val cleartext = performActualDecryption(key, iv, ct, ad);
    log.atTrace().addArgument(cleartext::toReadableString).log("Decrypted VAU3/ASL message: {}");
    return cleartext;
  }

  private static RbelElement convertHeader(
      RbelElement element, RbelConversionExecutor context, RbelContent header) {
    val headerElement = context.convertElement(header, element);
    val version = header.get(0);
    val pu = header.get(HEADER_PU_INDEX);
    val req = header.get(HEADER_REQ_INDEX);
    val reqCounterBytes =
        header
            .subArray(
                HEADER_REQ_COUNTER_INDEX, HEADER_REQ_COUNTER_INDEX + HEADER_REQ_COUNTER_LENGTH)
            .toByteArray();
    val keyId =
        header
            .subArray(HEADER_KEY_ID_INDEX, HEADER_KEY_ID_INDEX + HEADER_KEY_ID_LENGTH)
            .toByteArray();
    headerElement.addFacet(
        new RbelMapFacet(
            new RbelMultiMap<RbelElement>()
                .with("version", RbelElement.wrap(new byte[] {version}, headerElement, version))
                .with("pu", RbelElement.wrap(new byte[] {pu}, headerElement, pu))
                .with("req", RbelElement.wrap(new byte[] {req}, headerElement, req))
                .with(
                    "reqCtr",
                    RbelElement.wrap(
                        reqCounterBytes, headerElement, ByteBuffer.wrap(reqCounterBytes).getLong()))
                .with("keyId", RbelElement.wrap(keyId, headerElement, new BigInteger(keyId)))));
    return headerElement;
  }

  public abstract RbelAslEncryptionFacet buildFacet(
      RbelElement cleartextElement, RbelElement headerElement);

  @SneakyThrows
  private static RbelContent performActualDecryption(
      Key key, byte[] iv, RbelContent ciphertext, byte[] ad) {
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); // NOSONAR
    cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
    cipher.updateAAD(ad);
    return RbelContent.from(new CipherInputStream(ciphertext.toInputStream(), cipher));
  }

  public void tryToExtractVauNonPuTracingKeys(RbelElement element, RbelConversionExecutor context) {
    Optional.ofNullable(element.getParentNode())
        .flatMap(el -> el.getFacet(RbelHttpMessageFacet.class))
        .map(RbelHttpMessageFacet::getHeader)
        .flatMap(header -> header.getFirstIgnoringCase(getKeyHeaderName()))
        .map(RbelElement::getRawStringContent)
        .map(keyString -> keyString.split(" "))
        .stream()
        .flatMap(Stream::of)
        .map(Base64.getDecoder()::decode)
        .map(key -> new SecretKeySpec(key, "AES"))
        .map(key -> new RbelKey(key, getKeyHeaderName() + UUID.randomUUID(), 0))
        .forEach(key -> context.getRbelKeyManager().addKey(key));
  }
}
