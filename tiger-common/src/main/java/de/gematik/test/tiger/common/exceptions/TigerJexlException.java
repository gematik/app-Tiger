package de.gematik.test.tiger.common.exceptions;

public class TigerJexlException extends RuntimeException {

    public TigerJexlException(String s, RuntimeException e) {
        super(s, e);
    }

    public TigerJexlException(String s) {
        super(s);
    }
}
