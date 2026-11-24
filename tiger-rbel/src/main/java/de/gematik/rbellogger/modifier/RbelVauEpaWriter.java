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
package de.gematik.rbellogger.modifier;

import static de.gematik.rbellogger.facets.vau.vau.RbelVauEpaFacet.KEY_ID_LENGTH;
import static de.gematik.rbellogger.facets.vau.vau.RbelVauEpaFacet.keyIdHexOf;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.exceptions.RbelPkiException;
import de.gematik.rbellogger.facets.vau.vau.RbelVauEpaFacet;
import de.gematik.rbellogger.key.RbelKey;
import de.gematik.rbellogger.util.CryptoUtils;
import de.gematik.rbellogger.util.RbelContent;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

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

  @SneakyThrows
  private static RbelContent encrypt(
      RbelContent keyId,
      RbelContent iv,
      byte[] key,
      byte[] head,
      int headLength,
      RbelContent tail) {
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding", "BC"); // NOSONAR
    cipher.init(
        Cipher.ENCRYPT_MODE,
        new SecretKeySpec(key, "AES"),
        new GCMParameterSpec(16 * 8, iv.toByteArray()));

    final InputStream cleartext =
        new SequenceInputStream(
            new ByteArrayInputStream(head, 0, headLength), tail.toInputStream());
    return RbelContent.from(
        new SequenceInputStream(
            Collections.enumeration(
                List.of(
                    keyId.toInputStream(),
                    iv.toInputStream(),
                    new CipherInputStream(cleartext, cipher)))));
  }

  @Override
  public boolean canWrite(RbelElement oldTargetElement) {
    return oldTargetElement.hasFacet(RbelVauEpaFacet.class);
  }

  @SneakyThrows
  @Override
  public RbelContent write(
      RbelElement oldTargetElement, RbelElement oldTargetModifiedChild, RbelContent newContent) {
    final Optional<RbelKey> decryptionKey =
        oldTargetElement.getFacet(RbelVauEpaFacet.class).flatMap(RbelVauEpaFacet::getKeyUsed);
    if (decryptionKey.isEmpty()) {
      throw new RbelPkiException(
          "Error while trying to write VAU Erp Message: No decryption-key found!");
    }
    final var oldEncryptedMessage =
        oldTargetElement.getFacetOrFail(RbelVauEpaFacet.class).getEncryptedMessage().getContent();
    final byte[] oldCleartext =
        CryptoUtils.decrypt(oldEncryptedMessage, decryptionKey.get().getKey()).get();
    // for details see gemSpec_krypt, chapter 6
    int headerLength = ByteBuffer.wrap(oldCleartext, 1 + 8, 4).getInt();
    long introLength = 1L + 8 + 4 + headerLength;
    if (headerLength < 0 || introLength > oldCleartext.length) {
      throw new RbelModifier.RbelModificationException(
          "The VAU message can not be rewritten: its header would be "
              + headerLength
              + " bytes long, but it only has "
              + oldCleartext.length
              + " bytes of cleartext (a handshake message has no header length)");
    }

    final RbelContent newVauMessage =
        encrypt(
            oldTargetElement.getContent().subArray(0, KEY_ID_LENGTH),
            oldEncryptedMessage.subArray(0, 12),
            decryptionKey.get().getKey().getEncoded(),
            oldCleartext,
            (int) introLength,
            newContent);
    log.atTrace()
        .addArgument(() -> keyIdHexOf(newVauMessage))
        .addArgument(
            () ->
                Base64.getEncoder()
                    .encodeToString(
                        newVauMessage.subArray(KEY_ID_LENGTH, newVauMessage.size()).toByteArray()))
        .log("wrote VAU message with key id {} and body {}");
    return newVauMessage;
  }
}
