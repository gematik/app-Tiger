/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.exception;

public class TigerHttpGlueCodeException extends RuntimeException {
    public TigerHttpGlueCodeException(String message) {
        super(message);
    }

    public TigerHttpGlueCodeException(String message, Exception e) {
        super(message, e);
    }
}
