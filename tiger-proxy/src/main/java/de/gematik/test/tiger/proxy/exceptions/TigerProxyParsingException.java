/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.exceptions;

public class TigerProxyParsingException extends RuntimeException {
  public TigerProxyParsingException(String s) {
    super(s);
  }

  public TigerProxyParsingException(String msg, Exception e) {
    super(msg, e);
  }
}
