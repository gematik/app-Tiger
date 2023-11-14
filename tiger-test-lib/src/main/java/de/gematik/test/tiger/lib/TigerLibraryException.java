/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib;

public class TigerLibraryException extends RuntimeException {

  public TigerLibraryException(String msg) {
    super(msg);
  }

  public TigerLibraryException(String pattern, Object... args) {
    super(String.format(pattern, args));
  }
}
