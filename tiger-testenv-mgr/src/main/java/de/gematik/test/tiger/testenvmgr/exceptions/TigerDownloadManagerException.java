package de.gematik.test.tiger.testenvmgr.exceptions;

import de.gematik.test.tiger.testenvmgr.TigerEnvironmentStartupException;

public class TigerDownloadManagerException extends TigerEnvironmentStartupException {
    public TigerDownloadManagerException(String s, Exception e) {
        super(s, e);
    }
}
