/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.client;

public class TigerRemoteProxyClientException extends RuntimeException {
    public TigerRemoteProxyClientException(String s) {
        super(s);
    }

    public TigerRemoteProxyClientException(Throwable exception) {
        super(exception);
    }

    public TigerRemoteProxyClientException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
