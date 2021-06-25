/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.exception;

public class TigerProxyStartupException extends RuntimeException {

    public TigerProxyStartupException(String message, Exception exception) {
        super(message, exception);
    }
}
