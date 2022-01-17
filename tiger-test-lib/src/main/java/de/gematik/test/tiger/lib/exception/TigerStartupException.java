/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.exception;

public class TigerStartupException extends RuntimeException {
    public TigerStartupException(String message) {
        super(message);
    }

    public TigerStartupException(String message, Exception e) {
        super(message, e);
    }
}
