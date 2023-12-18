/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.zion;

public class ZionException extends RuntimeException {
  public ZionException(String msg) {
    super(msg);
  }

  public ZionException(String msg, Exception e) {
    super(msg, e);
  }
}
