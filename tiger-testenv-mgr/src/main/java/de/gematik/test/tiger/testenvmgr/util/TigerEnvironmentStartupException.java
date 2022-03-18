/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.util;

public class TigerEnvironmentStartupException extends RuntimeException {
    public TigerEnvironmentStartupException(String s, Exception e) {
        super(s, e);
    }

    public TigerEnvironmentStartupException(String s) {
        super(s);
    }
}
