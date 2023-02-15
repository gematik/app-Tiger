package de.gematik.rbellogger.writer;


public class RbelSerializationException extends RuntimeException {

    public RbelSerializationException(String s) {
        super(s);
    }

    public RbelSerializationException(String s, Exception e) {
        super(s, e);
    }
}
