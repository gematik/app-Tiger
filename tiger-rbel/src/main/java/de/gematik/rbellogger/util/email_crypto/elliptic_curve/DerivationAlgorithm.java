/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.util.email_crypto.elliptic_curve;

/**
 * Derivation Algorithm
 *
 * <ul>
 *   <li>AES-128
 *   <li>AES-192
 *   <li>AES-256
 *   <li>DESede
 * </ul>
 */
public enum DerivationAlgorithm {
  AES128,
  AES192,
  AES256,
  DESEDE
}
