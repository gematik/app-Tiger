/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.util.email_crypto.elliptic_curve;

/** Die Klasse beschreibt das Attribut 'ObjectIdentifier' gemäß 7.1.1.5 */
public class ObjectIdentifier {

  public static final String ANSIX9P256R1 = "ansix9p256r1";
  public static final String ANSIX9P384R1 = "ansix9p384r1";
  public static final String SECP256R1 = "secp256r1";
  public static final String SECP384R1 = "secp384r1";
  public static final String BRAINPOOLP256R1 = "brainpoolP256r1";
  public static final String BRAINPOOLP384R1 = "brainpoolP384r1";
  public static final String BRAINPOOLP512R1 = "brainpoolP512r1";

  private ObjectIdentifier() {}
}
