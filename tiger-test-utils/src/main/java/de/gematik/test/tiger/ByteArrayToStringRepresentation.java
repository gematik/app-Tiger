/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger;

import java.nio.charset.StandardCharsets;
import org.assertj.core.presentation.Representation;

public class ByteArrayToStringRepresentation implements Representation {

  @Override
  public String toStringOf(Object object) {
    if (object instanceof byte[] byteArray) {
      return new String(byteArray, StandardCharsets.UTF_8);
    } else {
      return object.toString();
    }
  }
}
