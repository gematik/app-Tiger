package de.gematik.test.tiger.common;

import java.net.SocketException;

public class TigerOsException extends RuntimeException {

    public TigerOsException(String s) {
        super(s);
    }

    public TigerOsException(String s, Exception e) {
        super(s, e);
    }
}
