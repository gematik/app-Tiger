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
import java.security.spec.ECField;
import java.security.spec.ECFieldFp;
import java.security.spec.EllipticCurve;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.custom.sec.SecP256R1Curve;
import org.bouncycastle.math.ec.custom.sec.SecP384R1Curve;

/**
 * Die Klasse beschreibt den Domain Parameter einer elliptischen Kurve gemäß 8.2.2.
 *
 * @author hve
 */
public class ElcDomainParameter {

  private static final int BIT_LENGTH_512 = 512;
  private static final int BIT_LENGTH_384 = 384;
  private static final int BIT_LENGTH_256 = 256;

  private int bitLengthN;
  private final ECNamedCurveParameterSpec ecSpec;

  /**
   * Der Konstruktor
   *
   * @param oid - Die ObjektID gemäß N008.600
   * @throws ParseException
   */
  public ElcDomainParameter(final String oid) throws ParseException {
    // (N002.500) a.
    // (N008.600) b. 1.
    // (N008.600) c. 1.
    String oidFinal = oid;
    if (oidFinal.equalsIgnoreCase(ObjectIdentifier.BRAINPOOLP256R1)
        || oidFinal.equalsIgnoreCase(ObjectIdentifier.ANSIX9P256R1)
        || oid.equalsIgnoreCase(ObjectIdentifier.SECP256R1)) {
      this.bitLengthN = BIT_LENGTH_256;
    }

    // (N002.500) b.
    // (N008.600) b. 2.
    // (N008.600) c. 2.
    if (oidFinal.equalsIgnoreCase(ObjectIdentifier.BRAINPOOLP384R1)
        || oidFinal.equalsIgnoreCase(ObjectIdentifier.ANSIX9P384R1)
        || oid.equalsIgnoreCase(ObjectIdentifier.SECP384R1)) {
      this.bitLengthN = BIT_LENGTH_384;
    }

    // (N002.500) c.
    // (N008.600) b. 3.
    // (N008.600) c. 3.
    if (oidFinal.equalsIgnoreCase(ObjectIdentifier.BRAINPOOLP512R1)) {
      this.bitLengthN = BIT_LENGTH_512;
    }

    // Die offiziellen Namen müssen für BouncyCastle gemappt werden
    if (oidFinal.equalsIgnoreCase(ObjectIdentifier.ANSIX9P256R1)) {
      oidFinal = "secp256r1";
    }

    if (oidFinal.equalsIgnoreCase(ObjectIdentifier.ANSIX9P384R1)) {
      oidFinal = "secp384r1";
    }

    // (N002.600)
    // (N008.600) d.
    ecSpec = ECNamedCurveTable.getParameterSpec(oidFinal);
    if (null == ecSpec) {
      throw new ParseException(
          this.getClass().getName() + " : (N008.600) d. : Invalid Oid = " + oidFinal);
    }
  }

  /**
   * @return eine Zahl L, welche die minimale Anzahl Oktette angibt, die nötig sind, um p als
   *     vorzeichenlose Zahl zu codieren.
   */
  public int getBitLengthN() {
    return this.bitLengthN;
  }

  public EllipticCurve getCurve() {
    ECField field = new ECFieldFp(getP());
    return new EllipticCurve(field, getA(), getB());
  }

  /**
   * @return Object Identifier, der die elliptische Kurve referenziert
   */
  public String getOid() {
    return ecSpec.getName();
  }

  /**
   * @return Primzahl, welche die zugrunde liegende Gruppe Fp beschreibt
   */
  public BigInteger getP() {
    if (ecSpec.getCurve() instanceof SecP256R1Curve secCurve) {
      return secCurve.getQ();
    } else if (ecSpec.getCurve() instanceof SecP384R1Curve secCurve) {
      return secCurve.getQ(); // NOSONAR
    }
    return ((ECCurve.Fp) ecSpec.getCurve()).getQ();
  }

  /**
   * @return Erster Koeffizient der Weierstra�schen Gleichung
   */
  public BigInteger getA() {
    return ecSpec.getCurve().getA().toBigInteger();
  }

  /**
   * @return Zweiter Koeffizient der Weierstra�schen Gleichung
   */
  public BigInteger getB() {
    return ecSpec.getCurve().getB().toBigInteger();
  }

  /**
   * @return Ein Punkt auf der Kurve E(Fp), Basispunkt
   */
  public org.bouncycastle.math.ec.ECPoint getG() {
    return ecSpec.getG();
  }

  /**
   * @return Ein Punkt auf der Kurve E(Fp), Basispunkt, x-Koordinate
   */
  public BigInteger getGx() {
    return ecSpec.getG().normalize().getXCoord().toBigInteger();
  }

  /**
   * @return Ein Punkt auf der Kurve E(Fp), Basispunkt, y-Koordinate
   */
  public BigInteger getGy() {
    return ecSpec.getG().normalize().getYCoord().toBigInteger();
  }

  /**
   * @return Ordnung des Basispunktes G in E(Fp)
   */
  public BigInteger getN() {
    return ecSpec.getN();
  }

  /**
   * @return Cofaktor von G in E(Fp)
   */
  public BigInteger getH() {
    return ecSpec.getH();
  }

  @Override
  public boolean equals(final Object obj) {
    return obj instanceof ElcDomainParameter parameter
        && getBitLengthN() == parameter.getBitLengthN()
        && getOid().equals(parameter.getOid())
        && getP().equals(parameter.getP())
        && getA().equals(parameter.getA())
        && getB().equals(parameter.getB())
        && getGx().equals(parameter.getGx())
        && getGy().equals(parameter.getGy())
        && getN().equals(parameter.getN())
        && getH().equals(parameter.getH());
  }

  @Override
  public int hashCode() {
    throw new UnsupportedOperationException("Function not yet implemented.");
  }
}
