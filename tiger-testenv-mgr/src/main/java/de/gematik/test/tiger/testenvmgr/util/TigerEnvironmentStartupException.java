/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.util;

public class TigerEnvironmentStartupException extends RuntimeException {
    public TigerEnvironmentStartupException(String s, Exception e) {
        super(s, e);
    }

    public TigerEnvironmentStartupException(final String pattern, Object... args) {
        super(String.format(pattern, args));
    }

    public TigerEnvironmentStartupException(final Throwable t, final String pattern, Object... args) {
        super(String.format(pattern, args), t);
    }

    public TigerEnvironmentStartupException(String s) {
        super(s);
    }
}
