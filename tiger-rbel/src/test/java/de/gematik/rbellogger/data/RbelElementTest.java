/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data;

import static de.gematik.rbellogger.TestUtils.readAndConvertCurlMessage;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.data.facet.*;
import java.io.IOException;
import org.junit.jupiter.api.Test;

@SuppressWarnings("unchecked")
class RbelElementTest {
  private static final RbelElement msg;

  static {
    try {
      msg = readAndConvertCurlMessage("src/test/resources/sampleMessages/xmlMessage.curl");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void renameElementViaBuilder_UuidShouldChange() {
    RbelElement originalElement = new RbelElement("fo".getBytes(), null);
    RbelElement renamedElement = originalElement.toBuilder().uuid("another uuid").build();

    assertThat(originalElement.getUuid()).isNotEqualTo(renamedElement.getUuid());
  }

  @Test
  void duplicatedElementViaBuilder_UuidShouldNotChange() {
    RbelElement originalElement = new RbelElement("fo".getBytes(), null);
    RbelElement renamedElement = originalElement.toBuilder().build();

    assertThat(originalElement.getUuid()).isEqualTo(renamedElement.getUuid());
  }
}
