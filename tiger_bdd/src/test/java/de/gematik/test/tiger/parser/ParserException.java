package de.gematik.test.tiger.parser;

public class ParserException extends RuntimeException {

    private static final long serialVersionUID = -5184487815608514494L;

    public ParserException(final String s) {
        super(s);
    }

    public ParserException(final String s, final Throwable t) {
        super(s, t);
    }
}
