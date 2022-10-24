/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.util;

public class RbelException extends RuntimeException {

    private static final long serialVersionUID = -2312909087086432824L;

    public RbelException(final String s) {
        super(s);
    }

    public RbelException(final String s, Throwable e) {
        super(s, e);
    }

    public RbelException(Exception e) {
        super(e);
    }
}
