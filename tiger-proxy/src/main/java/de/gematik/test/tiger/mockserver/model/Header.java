package de.gematik.test.tiger.mockserver.model;

import java.util.Collection;

public class Header extends KeyToMultiValue {

  public Header(String name, String... value) {
    super(name, value);
  }

  public Header(String name, Collection<String> value) {
    super(name, value);
  }

  public static Header header(String name, int value) {
    return new Header(name, String.valueOf(value));
  }

  public static Header header(String name, String... value) {
    return new Header(name, value);
  }

  public static Header header(String name, Collection<String> value) {
    return new Header(name, value);
  }
}
