/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import de.gematik.test.tiger.mockserver.serialization.ObjectMapperFactory;

/*
 * @author jamesdbloom
 */
public abstract class ObjectWithJsonToString {

  private static final String ESCAPED_QUOTE = "\"";
  private static final ObjectWriter OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper(true, false);

  @Override
  public String toString() {
    try {
      String valueAsString = OBJECT_MAPPER.writeValueAsString(this);
      if (valueAsString.startsWith(ESCAPED_QUOTE) && valueAsString.endsWith(ESCAPED_QUOTE)) {
        valueAsString = valueAsString.substring(1, valueAsString.length() - 1);
      }
      return valueAsString;
    } catch (JsonProcessingException | RuntimeException e) {
      return super.toString();
    }
  }
}
