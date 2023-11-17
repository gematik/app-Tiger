/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.exceptions;

public class TigerProxySslException extends RuntimeException {
  public TigerProxySslException(String s, Exception e) {
    super(s, e);
  }
}
