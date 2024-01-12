package de.gematik.test.tiger.mockserver.model;

import lombok.Data;

@Data
public class KeyAndValue extends ObjectWithJsonToString {
  private final String name;
  private final String value;
}
