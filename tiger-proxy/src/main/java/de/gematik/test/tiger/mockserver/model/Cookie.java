package de.gematik.test.tiger.mockserver.model;

public class Cookie extends KeyAndValue {

  public Cookie(String name, String value) {
    super(name, value);
  }

  public static Cookie cookie(String name, String value) {
    return new Cookie(name, value);
  }
}
