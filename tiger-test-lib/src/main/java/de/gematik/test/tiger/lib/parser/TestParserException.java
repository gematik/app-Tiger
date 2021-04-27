package de.gematik.test.tiger.lib.parser;

public class TestParserException extends RuntimeException {

    private static final long serialVersionUID = -5184487815608514494L;

    public TestParserException(final String s) {
        super(s);
    }

    public TestParserException(final String s, final Throwable t) {
        super(s, t);
    }
}
