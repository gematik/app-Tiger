package de.gematik.test.tiger.common.exceptions;

public class TigerFileSeparatorException extends RuntimeException {

    public TigerFileSeparatorException(String s) {
        super(s);
    }

    public TigerFileSeparatorException(String s, Exception e) {
        super(s, e);
    }
}

