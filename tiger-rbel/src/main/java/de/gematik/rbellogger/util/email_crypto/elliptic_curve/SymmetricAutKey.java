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
