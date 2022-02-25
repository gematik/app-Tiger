/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.config;

public class TigerConfigurationException extends RuntimeException {

    public TigerConfigurationException(String s) {
        super(s);
    }
    public TigerConfigurationException(String s, Throwable t) {
        super(s, t);
    }
}
