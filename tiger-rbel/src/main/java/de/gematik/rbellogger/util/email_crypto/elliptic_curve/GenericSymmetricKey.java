/*
 * Copyright 2024 gematik GmbH
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
 */

package de.gematik.rbellogger.util.email_crypto.elliptic_curve;

import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;

/** Die Klasse beschreibt symmetrische Schlüsselobjekte gemäß 8.2.1 */
@Slf4j
public class GenericSymmetricKey {

  private GenericSymmetricKey() {}

  /**
   * Verschlüsselung AES gemäß (N004.000) Entschlüsselung AES gemäß (N004.200)
   *
   * @param cryptogram
   * @param cryptogramOffset
   * @param icv
   * @param encrypt
   * @return
   * @throws BcException
   */
  public static String aesCbc(
      final byte[] encKey,
      final byte[] cryptogram,
      final int cryptogramOffset,
      final byte[] icv,
      final boolean encrypt)
      throws BcException {

    // (N004.000)
    // (N004.200)
    byte[] resultValue;
    log.debug(
        String.format(
            "AES_CBC: encKey: %s encrypt: %s cryptogramLength: %s",
            Arrays.toString(encKey), encrypt, cryptogram.length));
    var alg =
        switch (encKey.length) {
          case 16 -> EncryptionAlgorithmIdentifier.AES128;
          case 24 -> EncryptionAlgorithmIdentifier.AES192;
          case 32 -> EncryptionAlgorithmIdentifier.AES256;
          default -> throw new UnsupportedOperationException("Unexpected encryption key length");
        };

    resultValue =
        BCSymmetric.cipherOperation(
            alg, encKey, encrypt, cryptogram, cryptogramOffset, cryptogram.length, icv);
    String secret = StringTools.toHexString(resultValue);

    log.debug(String.format("alg: %s secret: %s", alg, secret));

    return secret;
  }
}
