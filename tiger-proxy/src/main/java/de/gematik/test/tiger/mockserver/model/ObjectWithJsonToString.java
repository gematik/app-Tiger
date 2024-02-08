/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.model;

import de.gematik.test.tiger.mockserver.serialization.ObjectMapperFactory;

/*
 * @author jamesdbloom
 */
public abstract class ObjectWithJsonToString extends ObjectWithReflectiveEqualsHashCodeToString {

  private static final String ESCAPED_QUOTE = "\"";

  @Override
  public String toString() {
    try {
      String valueAsString =
          ObjectMapperFactory.createObjectMapper(true, false).writeValueAsString(this);
      if (valueAsString.startsWith(ESCAPED_QUOTE) && valueAsString.endsWith(ESCAPED_QUOTE)) {
        valueAsString = valueAsString.substring(1, valueAsString.length() - 1);
      }
      return valueAsString;
    } catch (Exception e) {
      return super.toString();
    }
  }
}
