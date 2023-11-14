/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.exceptions;

public class RbelHostnameFormatException extends RuntimeException {

  public RbelHostnameFormatException(String message, Exception exception) {
    super(message, exception);
  }

  public RbelHostnameFormatException(String message) {
    super(message);
  }
}
