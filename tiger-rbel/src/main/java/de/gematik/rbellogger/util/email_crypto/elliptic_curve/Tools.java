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
package de.gematik.rbellogger.util.email_crypto.elliptic_curve;

import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.SHA1Digest;
import org.bouncycastle.crypto.digests.SHA256Digest;

/**
 * Klasse enthält Tools, die innerhalb des Bouncycastle Packages verwendet werden.
 *
 * @author hve
 */
public final class Tools {

  private static final byte[] POSTFIX_1 = {0x00, 0x00, 0x00, 0x01};
  private static final byte[] POSTFIX_2 = {0x00, 0x00, 0x00, 0x02};

  private static final int LENGTH_SSC = 16;

  private Tools() {}

  /**
   * Führt die "Mask Generation Function" gemäß 5.10 durch.
   *
   * @param z Die Inputdaten
   * @param lN Integer, welches die Bitlänge von N angibt
   * @param i Startwert der Iteration
   * @return N, Ergbenis der Mask Generation Function
   */
  public static byte[] mgf(final byte[] z, final int lN, final int i) {
    int startCount = i;
    StringBuilder returnValue = new StringBuilder();
    byte[] count = new byte[4];

    SHA256Digest sha256 = new SHA256Digest();
    byte[] hash = new byte[sha256.getDigestSize()];

    // (N001.100)
    while ((returnValue.length() / 2) < lN) {
      count[0] = (byte) (startCount >>> 24);
      count[1] = (byte) (startCount >>> 16);
      count[2] = (byte) (startCount >>> 8);
      count[3] = (byte) (startCount >>> 0);

      sha256.update(z, 0, z.length);
      sha256.update(count, 0, count.length);
      sha256.doFinal(hash, 0);

      returnValue.append(StringTools.toHexString(hash));
      startCount++;
    }

    return StringTools.toByteArray(returnValue.substring(0, lN * 2));
  }

  /**
   * Sessionkeyableitung nach gemSpec_COS#3.2.0.
   *
   * @param kdIE byte[] KeyDerivationData
   * @param algo (DerivationAlgorithm.AES128)) {
   * @return km ByteArray <Kenc>, <SSCKenc == '00'...'00'> N001.500ff �nderung gemSpec_COS#3.2.0
   * @throws BcException
   */
  public static byte[] keyDerivationAes(final byte[] kdIE, final DerivationAlgorithm algo)
      throws BcException {

    byte[] km;

    byte[] kdIEnc = new byte[kdIE.length + POSTFIX_1.length];
    System.arraycopy(kdIE, 0, kdIEnc, 0, kdIE.length);
    System.arraycopy(POSTFIX_1, 0, kdIEnc, kdIE.length, POSTFIX_1.length);

    byte[] kdIMac = new byte[kdIE.length + POSTFIX_2.length];
    System.arraycopy(kdIE, 0, kdIMac, 0, kdIE.length);
    System.arraycopy(POSTFIX_2, 0, kdIMac, kdIE.length, POSTFIX_2.length);

    Digest digest;
    int keyLength;

    // (N001.500) a �nderung gemSpec_COS#3.2.0
    if (algo.equals(DerivationAlgorithm.AES128)) {

      digest = new SHA1Digest();
      keyLength = 16;

      // (N001.510) a. �nderung gemSpec_COS#3.2.0
    } else if (algo.equals(DerivationAlgorithm.AES192)) {

      digest = new SHA256Digest();
      keyLength = 24;

      // (N001.520) a. �nderung gemSpec_COS#3.2.0
    } else if (algo.equals(DerivationAlgorithm.AES256)) {

      digest = new SHA256Digest();
      keyLength = 32;

    } else {

      throw new BcException(
          BCSymmetric.class.getName() + " : Key derivation algorithm unknown : " + algo);
    }

    byte[] k4Enc = new byte[digest.getDigestSize()];
    byte[] k4Mac = new byte[digest.getDigestSize()];

    digest.update(kdIEnc, 0, kdIEnc.length);
    digest.doFinal(k4Enc, 0);

    digest.reset();

    digest.update(kdIMac, 0, kdIMac.length);
    digest.doFinal(k4Mac, 0);

    km = new byte[keyLength * 2 + LENGTH_SSC * 2];
    System.arraycopy(k4Enc, 0, km, 0, keyLength);
    System.arraycopy(k4Mac, 0, km, keyLength + LENGTH_SSC, keyLength);

    return km;
  }
}
