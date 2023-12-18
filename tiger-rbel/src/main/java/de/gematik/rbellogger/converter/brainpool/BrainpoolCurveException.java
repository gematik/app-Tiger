/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter.brainpool;

public class BrainpoolCurveException extends RuntimeException {
  public BrainpoolCurveException(String msg, Exception e) {
    super(msg, e);
  }
}
