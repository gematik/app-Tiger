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
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.Key;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;

@Slf4j
public abstract class AbstractAslDecryptionConverter extends RbelConverterPlugin {
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
      final byte[] rawContent = element.getRawContent();
      // These numbers are derived from A_24628 to A_24633 in gemSpec_Krypt V2.30.0
      final int headerLength = 32 + 8 + 1 + 1 + 1;
      byte[] header = ArrayUtils.subarray(rawContent, 0, headerLength);
      byte[] iv = ArrayUtils.subarray(rawContent, headerLength, headerLength + BODY_IV_LENGTH);
      byte[] ct = ArrayUtils.subarray(rawContent, BODY_CT_INDEX, rawContent.length);
      final byte[] cleartext = performActualDecryption(key, iv, ct, header);
      if (log.isTraceEnabled()) {
        log.trace("Decrypted VAU3/ASL message: {}", new String(cleartext));
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
      element.addFacet(buildFacet(cleartextElement, headerElement));
    } catch (Exception e) {
      log.trace("Failed to parse VAU EPA3: ", e);
    }
  }

  public abstract RbelAslEncryptionFacet buildFacet(
      RbelElement cleartextElement, RbelElement headerElement);

  @SneakyThrows
  private static byte[] performActualDecryption(Key key, byte[] iv, byte[] ciphertext, byte[] ad) {
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); // NOSONAR
    cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
    cipher.updateAAD(ad);
    return cipher.doFinal(ciphertext);
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
