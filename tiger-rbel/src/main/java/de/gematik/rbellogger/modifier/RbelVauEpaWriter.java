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

package de.gematik.rbellogger.modifier;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelVauEpaFacet;
import de.gematik.rbellogger.exceptions.RbelPkiException;
import de.gematik.rbellogger.key.RbelKey;
import de.gematik.rbellogger.modifier.RbelModifier.RbelModificationException;
import de.gematik.rbellogger.util.CryptoUtils;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;

@RequiredArgsConstructor
@Slf4j
public class RbelVauEpaWriter implements RbelElementWriter {

  @SneakyThrows
  public static byte[] encrypt(byte[] input, byte[] key, byte[] iv) {
    SecretKey secretKey = new SecretKeySpec(key, "AES");

    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding", "BC"); // NOSONAR

    cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(16 * 8, iv));

    byte[] cipherTextPlusTag = cipher.doFinal(input);

    byte[] encMessage = Arrays.copyOf(iv, 12 + cipherTextPlusTag.length);
    System.arraycopy(cipherTextPlusTag, 0, encMessage, 12, cipherTextPlusTag.length);

    return encMessage;
  }

  @Override
  public boolean canWrite(RbelElement oldTargetElement) {
    return oldTargetElement.hasFacet(RbelVauEpaFacet.class);
  }

  @SneakyThrows
  @Override
  public byte[] write(
      RbelElement oldTargetElement, RbelElement oldTargetModifiedChild, byte[] newContent) {
    final Optional<RbelKey> decryptionKey =
        oldTargetElement.getFacet(RbelVauEpaFacet.class).flatMap(RbelVauEpaFacet::getKeyUsed);
    if (decryptionKey.isEmpty()) {
      throw new RbelPkiException(
          "Error while trying to write VAU Erp Message: No decryption-key found!");
    }
    final byte[] oldEncryptedMessage =
        oldTargetElement
            .getFacetOrFail(RbelVauEpaFacet.class)
            .getEncryptedMessage()
            .getRawContent();
    final byte[] oldCleartext =
        CryptoUtils.decrypt(
                oldEncryptedMessage,
                oldTargetElement.getFacetOrFail(RbelVauEpaFacet.class).getKeyUsed().get().getKey())
            .get();
    // for details see gemSpec_krypt, chapter 6
    int headerLength =
        java.nio.ByteBuffer.wrap((Arrays.copyOfRange(oldCleartext, 1 + 8, 1 + 8 + 4))).getInt();
    int introLength = 1 + 8 + 4 + headerLength;
    byte[] oldIv = Arrays.copyOfRange(oldEncryptedMessage, 0, 12);
    final byte[] newCleartext =
        ArrayUtils.addAll(Arrays.copyOfRange(oldCleartext, 0, introLength), newContent);

    final byte[] newVauMessage =
        ArrayUtils.addAll(
            Arrays.copyOfRange(oldTargetElement.getRawContent(), 0, 32),
            encrypt(newCleartext, decryptionKey.get().getKey().getEncoded(), oldIv));
    var splitVauMessage = splitVauMessage(newVauMessage);
    log.info(
        "splitted into {} and {}",
        Base64.getEncoder().encodeToString(splitVauMessage.getLeft()),
        Base64.getEncoder().encodeToString(splitVauMessage.getRight()));
    return newVauMessage;
  }

  private Pair<byte[], byte[]> splitVauMessage(byte[] vauMessage) {
    try {
      byte[] keyID = new byte[32];
      System.arraycopy(vauMessage, 0, keyID, 0, 32);
      byte[] enc = new byte[vauMessage.length - 32];
      System.arraycopy(vauMessage, 32, enc, 0, vauMessage.length - 32);
      return Pair.of(keyID, enc);
    } catch (ArrayIndexOutOfBoundsException e) {
      throw new RbelModificationException("Unable to write VAU message", e);
    }
  }
}
