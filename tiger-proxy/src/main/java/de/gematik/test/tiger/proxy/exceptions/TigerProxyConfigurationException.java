/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.exceptions;

public class TigerProxyConfigurationException extends RuntimeException {

    public TigerProxyConfigurationException(String s) {
        super(s);
    }
    public TigerProxyConfigurationException(String s, Exception e) {
        super(s, e);
    }


}
