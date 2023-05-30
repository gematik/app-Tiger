/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.exceptions;

public class RbelRenderingException extends RuntimeException {

    public RbelRenderingException(String s) {
        super(s);
    }

    public RbelRenderingException(String s, Exception e) {
        super(s, e);
    }
}
