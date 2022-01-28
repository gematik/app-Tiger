/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.exceptions;

public class TigerProxyStartupException extends RuntimeException {

    public TigerProxyStartupException(String message, Exception exception) {
        super(message, exception);
    }

    public TigerProxyStartupException(String message) {
        super(message);
    }
}
