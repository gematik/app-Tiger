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

import java.math.BigInteger;
import java.security.spec.ECFieldFp;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECPoint;

/**
 * Die Klasse enthält die grundlegenden Algorithmen für die Verwendung von elliptischen Kurven.
 *
 * @author hve
 */
public final class BCElc {

  private static final boolean DO_NOT_COMPRESS = false;

  private BCElc() {}

  /**
   * Methode entschlüsselt Daten mittels elliptischer Kurve gemäß 6.8.2.3
   *
   * @param privateKey - Privater ELC-Schlüssel gemäß 8.2.3
   * @param domainParameter - Domainparameter gemäß (N008.600)
   * @param p - Oktettstring, ephemerer Punkt PE A des Senders
   * @param c - Oktettstring, Chiffrat der Nachricht M
   * @param t - Oktettstring, MAC über das Chiffrat c
   * @return Oktettstring, Klartextnachricht zum Chiffrat
   * @throws BcException
   */
  public static byte[] elcDec(
      final PrivateElcKeyBody privateKey,
      final ElcDomainParameter domainParameter,
      final byte[] p,
      final byte[] c,
      final byte[] t)
      throws BcException {
    // (N004.800)
    SessionKeyInfo skInfo;

    ECPoint pE = os2P(p, domainParameter);

    BigInteger k = ecka(privateKey.getSecret(), pE, domainParameter);

    int length = domainParameter.getBitLengthN() / 8;
    byte[] kdI =
        StringTools.toByteArray(StringTools.fillWithZerosBefore(length * 2, k.toString(16)));
    byte[] kdE = new byte[kdI.length];

    skInfo = BCSymmetric.keyDerivation(DerivationAlgorithm.AES256, kdI, kdE);

    if (!SymmetricAutKey.verifyCmac(skInfo.getMacKey(), t, c)) {
      throw new BcException(BCElc.class.getName() + " : (N004.800 : Error");
    }
    // ge�ndert gem�� gemSpec_COS#3.2.0 RC1 (N004.800.c.2)
    byte[] computedSSCEnc =
        BCSymmetric.cipherOperationECB(
            EncryptionAlgorithmIdentifier.AES256, skInfo.getEncKey(), true, new byte[16], 0, 16);

    byte[] m =
        StringTools.toByteArray(
            GenericSymmetricKey.aesCbc(skInfo.getEncKey(), c, 0, computedSSCEnc, false));

    byte[] mTruncated = new byte[m.length - Padding.countIsoPaddingByte(m)];
    System.arraycopy(m, 0, mTruncated, 0, mTruncated.length);

    return mTruncated;
  }

  /**
   * Methode ist an der physikalischen Schnittstelle nicht direkt sichtbar, wird aber im Rahmen der
   * asymmetrischen Ver- und Entschlüsselung mittels elliptischer Kurven verwendet gemäß 6.8.1.3.
   *
   * @param d - natürliche Zahl, die dem privaten Schlüssel entspricht
   * @param p - Punkt auf derselben elliptischen Kurve, wie PrK
   * @param domainParameter - Domainparameter gemäß (N008.600)
   * @return „gemeinsames Geheimnis“
   */
  public static BigInteger ecka(
      final BigInteger d, final ECPoint p, final ElcDomainParameter domainParameter) {
    // (N004.490)
    BigInteger n = domainParameter.getN();
    BigInteger h = domainParameter.getH();

    BigInteger l = h.modInverse(n);

    ECPoint q = p.multiply(h);

    ECPoint sharedSecretPoint = q.multiply(d.multiply(l).mod(n));

    return new BigInteger(
        1,
        ByteTools.sub(
            sharedSecretPoint.getEncoded(DO_NOT_COMPRESS), 1, domainParameter.getBitLengthN() / 8));
  }

  /**
   * Methode konvertiert einen Oktettstring in einen Punkt auf einer elliptischen Kurve gemäß 5.5.
   *
   * @param pOctets - Oktettstring, codiert einen Punkt auf einer elliptischen Kurve
   * @param domainParameter - Domainparameter gemäß (N008.600)
   * @return Punkt auf einer elliptischen Kurve mit den Koordinaten P = (x, y)
   * @throws BcException
   */
  public static ECPoint os2P(final byte[] pOctets, final ElcDomainParameter domainParameter)
      throws BcException {
    // (N000.300)
    int l = domainParameter.getBitLengthN() / 8;

    if (pOctets.length != 2 * l + 1) {
      throw new BcException(BCElc.class.getName() + " : (N000.300) : Error");
    }

    if (0x04 != pOctets[0]) {
      throw new BcException(BCElc.class.getName() + " : (N000.300) : Error");
    }

    BigInteger x = new BigInteger(1, ByteTools.sub(pOctets, 1, l));
    BigInteger y = new BigInteger(1, ByteTools.sub(pOctets, 1 + l, l));

    ECCurve curve =
        new ECCurve.Fp(
            ((ECFieldFp) domainParameter.getCurve().getField()).getP(),
            domainParameter.getA(),
            domainParameter.getB(),
            null,
            null);
    return curve.createPoint(x, y);
  }
}
