package de.gematik.test.tiger.testenvmgr;

public class TigerTestEnvException extends RuntimeException {

    private static final long serialVersionUID = 7701810722390571308L;

    public TigerTestEnvException(final String msg) {
        super(msg);
    }

    public TigerTestEnvException(final String pattern, Object... args) {
        super(String.format(pattern, args));
    }

    public TigerTestEnvException(final String msg, final Throwable t) {
        super(msg, t);
    }
}
