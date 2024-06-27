/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.util.email_crypto.elliptic_curve;

/**
 * Die Klasse behandelt Fehler, die während der Arbeit mit BouncyCastle auftreten können.
 *
 * @author hve
 */
public class BcException extends Exception {

  private static final long serialVersionUID = 2L;

  /**
   * Der Konstruktor
   *
   * @param msg - Die Fehlernachricht
   */
  public BcException(final String msg) {
    super(msg);
  }
}
