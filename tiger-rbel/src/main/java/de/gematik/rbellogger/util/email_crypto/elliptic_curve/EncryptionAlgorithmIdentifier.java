/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.util.email_crypto.elliptic_curve;

/**
 * Encryption Algorithm
 *
 * <ul>
 *   <li>AES128
 *   <li>AES192
 *   <li>AES256
 *   <li>DES
 *   <li>DESede
 * </ul>
 */
public enum EncryptionAlgorithmIdentifier {
  AES128,
  AES192,
  AES256,
  DES,
  DESEDE
}
