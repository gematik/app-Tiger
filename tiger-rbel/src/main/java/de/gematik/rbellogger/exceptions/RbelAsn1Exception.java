/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.exceptions;

public class RbelAsn1Exception extends RuntimeException {

  public RbelAsn1Exception(String message) {
    super(message);
  }

  public RbelAsn1Exception(String message, Throwable e) {
    super(message, e);
  }
}
