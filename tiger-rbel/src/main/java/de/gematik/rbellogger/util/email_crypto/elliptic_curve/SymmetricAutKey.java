/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.util.email_crypto.elliptic_curve;

import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;

/**
 * Die Klasse beschreibt ein symmetrischen Authentisierungsschlüssel.
 *
 * <ul>
 *   <li>encKey
 *   <li>macKey
 *   <li>key mode
 * </ul>
 *
 * @author cdh
 */
@Slf4j
public class SymmetricAutKey {

  private SymmetricAutKey() {}

  /**
   * Prüfung CMAC gemäß (N003.000)
   *
   * @param mac
   * @param cryptogram
   * @return
   * @throws BcException
   */
  public static boolean verifyCmac(final byte[] macKey, final byte[] mac, final byte[] cryptogram)
      throws BcException {
    // (N003.000)
    String referenceMac = calculateCmac(macKey, cryptogram);
    String macString = StringTools.toHexString(mac);
    boolean verifyResult = referenceMac.equalsIgnoreCase(macString);
    log.debug(
        String.format(
            "referenceMac: %s macKey: %s, mac: %s verifyResult: %s",
            referenceMac, Arrays.toString(macKey), macString, verifyResult));
    return verifyResult;
  }

  /**
   * Berechnung CMAC gemäß (N002.800)
   *
   * @param plain
   * @return
   * @throws BcException
   */
  public static String calculateCmac(final byte[] macKey, final byte[] plain) throws BcException {
    // (N002.800)
    return StringTools.toHexString(
            BCSymmetric.generateMac(MacMode.CMAC, PaddingMode.ISO7816, macKey, plain))
        .substring(0, 16);
  }
}
