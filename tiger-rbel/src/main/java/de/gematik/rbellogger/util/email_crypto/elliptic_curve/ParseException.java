/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.util.email_crypto.elliptic_curve;

/**
 * Die Klasse behandelt Fehler die während des Parsingvorgangs auftreten können.
 *
 * @author cdh
 */
public class ParseException extends Exception {

  private static final long serialVersionUID = 2L;

  /**
   * Der Konstruktor
   *
   * @param msg - Die Fehlernachricht
   */
  public ParseException(final String msg) {
    super(msg);
  }
}
