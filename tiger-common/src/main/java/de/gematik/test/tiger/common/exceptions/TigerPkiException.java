/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.exceptions;

public class TigerPkiException extends RuntimeException {

  public TigerPkiException(String s) {
    super(s);
  }

  public TigerPkiException(String s, Exception e) {
    super(s, e);
  }

  public TigerPkiException(final String pattern, Object... args) {
    super(String.format(pattern, args));
  }

  public TigerPkiException(final Throwable t, final String pattern, Object... args) {
    super(String.format(pattern, args), t);
  }
}
