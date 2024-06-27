/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.util.email_crypto;

import de.gematik.rbellogger.util.RbelException;

public class RbelDecryptionException extends RbelException {
  public RbelDecryptionException(String s, Throwable ex) {
    super(s, ex);
  }

  public RbelDecryptionException(String s) {
    super(s);
  }
}
