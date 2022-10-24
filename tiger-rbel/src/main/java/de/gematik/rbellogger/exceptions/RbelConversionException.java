/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.exceptions;

import de.gematik.rbellogger.util.RbelException;

public class RbelConversionException extends RbelException {

    public RbelConversionException(String s) {
        super(s);
    }

    public RbelConversionException(Exception e) {
        super(e);
    }

    public RbelConversionException(String msg, Throwable e) {
        super(msg, e);
    }
}
