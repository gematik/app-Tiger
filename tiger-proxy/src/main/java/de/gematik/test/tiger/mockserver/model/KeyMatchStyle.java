package de.gematik.test.tiger.mockserver.model;

public enum KeyMatchStyle {
  SUB_SET, // default
  MATCHING_KEY;

  public static KeyMatchStyle defaultValue = SUB_SET;
}
