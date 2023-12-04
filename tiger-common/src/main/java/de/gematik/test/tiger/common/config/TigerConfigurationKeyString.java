/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.config;

import java.util.List;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
public class TigerConfigurationKeyString {
  private static final List<Character> FORBIDDEN_CHARACTERS = List.of('{', '}', '|');

  private final String value;

  public static TigerConfigurationKeyString wrapAsKey(String value) {
    for (Character c : FORBIDDEN_CHARACTERS) {
      value = value.replace(c, '_');
    }
    return new TigerConfigurationKeyString(value);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;

    if (o instanceof String asString) return value.equalsIgnoreCase(asString);

    if (o instanceof TigerConfigurationKeyString asTigerString) {
      return value != null
          ? value.equalsIgnoreCase(asTigerString.value)
          : asTigerString.value == null;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return value != null ? value.toLowerCase().hashCode() : 0;
  }

  public String asLowerCase() {
    return value.toLowerCase();
  }

  public String asString() {
    return value;
  }

  public boolean isNotEmptyKey() {
    return StringUtils.isNotEmpty(value);
  }
}
