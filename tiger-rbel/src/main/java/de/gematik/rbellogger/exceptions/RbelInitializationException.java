package de.gematik.rbellogger.exceptions;

public class RbelInitializationException extends RuntimeException {

  public RbelInitializationException(String s, Exception e) {
    super(s, e);
  }

  public RbelInitializationException(String s) {
    super(s);
  }
}
