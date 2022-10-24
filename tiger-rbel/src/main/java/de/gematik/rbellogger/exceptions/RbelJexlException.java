/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.exceptions;

public class RbelJexlException extends RuntimeException {

    public RbelJexlException(String s, Exception e) {
        super(s, e);
    }
}
