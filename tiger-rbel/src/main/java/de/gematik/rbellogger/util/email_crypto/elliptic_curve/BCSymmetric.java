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

import java.security.SecureRandom;
import java.security.Security;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.crypto.BlockCipher;
import org.bouncycastle.crypto.Mac;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.engines.DESEngine;
import org.bouncycastle.crypto.macs.CMac;
import org.bouncycastle.crypto.macs.ISO9797Alg3Mac;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * Die Klasse enthält die grundlegenden Algorithmen für DES und AES
 *
 * @author dwr
 */
@Slf4j
public final class BCSymmetric {

  private BCSymmetric() {}

  /**
   * Schlüsselvereinbarung gemäß (N001.400), (N001.500), (N001.510), (N001.520)
   *
   * @param derivationAlgorithm Der Ableitungsalgorithmus
   * @param kdI Die internen Ableitungsdaten
   * @param kdE Die externen Ableitungsdaten
   * @return Objekt, welches die SessionKey Informationen enthält
   * @throws BcException
   */
  public static SessionKeyInfo keyDerivation(
      final DerivationAlgorithm derivationAlgorithm, final byte[] kdI, final byte[] kdE)
      throws BcException {
    byte[] km;

    MathTools.xor(kdI, kdE);

    if (derivationAlgorithm.equals(DerivationAlgorithm.DESEDE)) {
      // (N001.400) a.
      km = Tools.mgf(kdI, 512 / 8, 1);
    } else {
      // (N001.500) ff
      // �nderung gemSpec_COS#3.2.0
      km = Tools.keyDerivationAes(kdI, derivationAlgorithm);
    }

    return new SessionKeyInfo(derivationAlgorithm, km);
  }

  /**
   * Verschlüsselung / Entschlüssung mittels symmetrischem Schlüssel im ECB-Mode
   *
   * @param encryptionAlgorithm {@link EncryptionAlgorithmIdentifier}
   * @param encryptionKey Der symmetrische Schlüssel
   * @param encrypt true wenn die Daten verschlüsselt werden sollen, false sonst
   * @param input Die Inputdaten
   * @param inputOffset Offset zu den Inputdaten
   * @param inputLength Länge der Inputdaten
   * @return Resultat der Berechnung
   * @throws BcException �nderung gemSpec_COS#3.2.0 f�r die Berechnung des SSCEnc
   */
  public static byte[] cipherOperationECB(
      final EncryptionAlgorithmIdentifier encryptionAlgorithm,
      final byte[] encryptionKey,
      final boolean encrypt,
      final byte[] input,
      final int inputOffset,
      final int inputLength)
      throws BcException {

    // (N002.000)
    // (N002.010)
    byte[] tmp = new byte[inputLength];
    System.arraycopy(input, inputOffset, tmp, 0, tmp.length);

    Security.addProvider(new BouncyCastleProvider());
    String algorithm = getAlgorithmString(encryptionAlgorithm);

    try {
      Cipher cipher = Cipher.getInstance(algorithm + "/" + "ECB" + "/" + "NOPADDING", "BC");

      int cipherMode = Cipher.ENCRYPT_MODE;
      if (!encrypt) {
        cipherMode = Cipher.DECRYPT_MODE;
      }

      SecretKeySpec key = new SecretKeySpec(encryptionKey, 0, encryptionKey.length, algorithm);

      cipher.init(cipherMode, key);

      return cipher.doFinal(tmp);

    } catch (Exception e) {
      throw new BcException(
          BCSymmetric.class.getName()
              + " : Error occured during symmetric cipher operation - Reason : "
              + e.getMessage());
    }
  }

  /**
   * Verschlüsselung / Entschlüssung mittels symmetrischem Schlüssel und spezifischen Algorithmus
   *
   * @param encryptionAlgorithm {@link EncryptionAlgorithmIdentifier}
   * @param encryptionKey Der symmetrische Schlüssel
   * @param encrypt true wenn die Daten verschlüsselt werden sollen, false sonst
   * @param input Die Inputdaten
   * @param inputOffset Offset zu den Inputdaten
   * @param inputLength Länge der Inputdaten
   * @param tmpIcv Der initiale Chaining Wert oder null
   * @return Resultat der Berechnung
   * @throws BcException
   */
  public static byte[] cipherOperation(
      final EncryptionAlgorithmIdentifier encryptionAlgorithm,
      final byte[] encryptionKey,
      final boolean encrypt,
      final byte[] input,
      final int inputOffset,
      final int inputLength,
      final byte[] tmpIcv)
      throws BcException {

    // (N001.600)
    // (N001.700)
    // (N001.800)
    // (N001.900)

    // (N002.000)
    // (N002.010)
    byte[] value = new byte[inputLength];
    System.arraycopy(input, inputOffset, value, 0, value.length);

    byte[] icv = tmpIcv;

    Security.addProvider(new BouncyCastleProvider());
    String algorithm = getAlgorithmString(encryptionAlgorithm);

    try {
      Cipher cipher = Cipher.getInstance(algorithm + "/" + "CBC" + "/" + "NOPADDING", "BC");

      int cipherMode = Cipher.ENCRYPT_MODE;
      if (!encrypt) {
        cipherMode = Cipher.DECRYPT_MODE;
      }

      log.debug(
          String.format(
              "algorithm: %s encryptionAlgorithm: %s cipherMode: %s",
              algorithm, encryptionAlgorithm, cipherMode));
      if (icv == null) {
        if (encryptionAlgorithm.equals(EncryptionAlgorithmIdentifier.DES)
            || encryptionAlgorithm.equals(EncryptionAlgorithmIdentifier.DESEDE)) {
          icv = new byte[8];
        } else {
          icv = new byte[16];
        }
        new SecureRandom().nextBytes(icv);
      }

      SecretKeySpec key = new SecretKeySpec(encryptionKey, 0, encryptionKey.length, algorithm);
      IvParameterSpec ips = new IvParameterSpec(icv);

      cipher.init(cipherMode, key, ips);

      return cipher.doFinal(value);
    } catch (Exception e) {
      throw new BcException(
          BCSymmetric.class.getName()
              + " : Error occured during symmetric cipher operation - Reason : "
              + e.getMessage());
    }
  }

  /**
   * Message Authenticate Code mit einem symmetrischen Schlüssel mit einem spezifischen Algorithmus
   *
   * @param macMode {@link MacMode}
   * @param macPadding {@link PaddingMode}
   * @param macKey Der symmetrische Schlüssel
   * @param value Die Inputdaten
   * @return Der resultierende Message Authentication Code
   * @throws BcException
   */
  public static byte[] generateMac(
      final MacMode macMode, final PaddingMode macPadding, final byte[] macKey, final byte[] value)
      throws BcException {
    byte[] macInput;
    byte[] mac;
    log.debug(
        String.format(
            "macMode: %s macPadding: %s, macKey: %s, plain: %s",
            macMode, macPadding, Arrays.toString(macKey), Arrays.toString(value)));
    BlockCipher blockCipher;
    switch (macMode) {
      case CFB:
        DESEngine des = new DESEngine();
        ISO9797Alg3Mac retailMac = new ISO9797Alg3Mac(des);

        mac = new byte[8];
        KeyParameter keyParam = new KeyParameter(macKey);
        macInput = Padding.addIsoPadding(value, 8);

        retailMac.reset();
        retailMac.init(keyParam);
        retailMac.update(macInput, 0, macInput.length);
        retailMac.doFinal(mac, 0);
        break;

      case CMAC:
        blockCipher = AESEngine.newInstance();
        Mac macBlockCipher = new CMac(blockCipher, blockCipher.getBlockSize() * 8);

        macInput = Padding.addIsoPadding(value, blockCipher.getBlockSize());
        macBlockCipher.init(new KeyParameter(macKey));
        macBlockCipher.update(macInput, 0, macInput.length);

        int macSize = macBlockCipher.getMacSize();
        mac = new byte[macSize];
        macBlockCipher.doFinal(mac, 0);
        break;

      case CMAC_NO_PADDING:
        blockCipher = AESEngine.newInstance();
        Mac macBlockCipherNP = new CMac(blockCipher, blockCipher.getBlockSize() * 8);

        macInput = value;
        macBlockCipherNP.init(new KeyParameter(macKey));
        macBlockCipherNP.update(macInput, 0, macInput.length);

        int macSizeNP = macBlockCipherNP.getMacSize();
        mac = new byte[macSizeNP];
        macBlockCipherNP.doFinal(mac, 0);
        break;

      default:
        throw new BcException(
            BCSymmetric.class.getName() + " : MAC calculation algorithm unknown : " + macMode);
    }
    return mac;
  }

  private static String getAlgorithmString(final EncryptionAlgorithmIdentifier algorithm) {
    return switch (algorithm) {
      case AES128, AES192, AES256 -> "AES";
      case DES -> "DES";
      case DESEDE -> "DESEDE";
    };
  }
}
