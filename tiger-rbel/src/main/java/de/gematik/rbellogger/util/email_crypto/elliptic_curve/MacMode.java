/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.util.email_crypto.elliptic_curve;

/**
 * MAC Mode
 *
 * <ul>
 *   <li>CFB
 *   <li>CMAC
 * </ul>
 */
public enum MacMode {
  CFB,
  CMAC,
  CMAC_NO_PADDING
}
