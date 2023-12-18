/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.exceptions;

public class RbelPkiException extends RuntimeException {
  public RbelPkiException(String msg) {
    super(msg);
  }

  public RbelPkiException(String msg, Exception e) {
    super(msg, e);
  }
}
