/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.exceptions;

import de.gematik.test.tiger.testenvmgr.util.TigerEnvironmentStartupException;

public class TigerDownloadManagerException extends TigerEnvironmentStartupException {
  public TigerDownloadManagerException(String s, Exception e) {
    super(s, e);
  }
}
