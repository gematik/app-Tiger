/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.exceptions;

public class TigerProxyWebUiException extends RuntimeException {

    public TigerProxyWebUiException(String message, Exception exception) {
        super(message, exception);
    }

    public TigerProxyWebUiException(String message) {
        super(message);
    }
}
