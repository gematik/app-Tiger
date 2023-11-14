/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelCetpFacet;
import java.util.Objects;
import org.apache.commons.lang3.ArrayUtils;

public class RbelCetpConverter implements RbelConverterPlugin {

  private static final byte[] CETP_INTRO_MARKER = "CETP".getBytes();

  @Override
  public void consumeElement(final RbelElement targetElement, final RbelConverter converter) {
    if (targetElement.getSize() <= 8 || !startsWithCetpMarker(targetElement.getRawContent())) {
      return;
    }
    byte[] messageLengthBytes = new byte[CETP_INTRO_MARKER.length];
    System.arraycopy(
        targetElement.getRawContent(), 4, messageLengthBytes, 0, CETP_INTRO_MARKER.length);

    int messageLength = java.nio.ByteBuffer.wrap(messageLengthBytes).getInt();
    if (targetElement.getSize() != 8 + messageLength) {
      return;
    }

    byte[] messageBody = new byte[targetElement.getRawContent().length - 8];
    System.arraycopy(
        targetElement.getRawContent(), 8, messageBody, 0, targetElement.getRawContent().length - 8);

    final RbelCetpFacet cetpFacet =
        RbelCetpFacet.builder()
            .messageLength(RbelElement.wrap(messageLengthBytes, targetElement, messageLength))
            .body(converter.convertElement(messageBody, targetElement))
            .build();

    targetElement.addFacet(cetpFacet);
  }

  private boolean startsWithCetpMarker(byte[] rawContent) {
    byte[] actualIntro = ArrayUtils.subarray(rawContent, 0, 4);
    return Objects.deepEquals(CETP_INTRO_MARKER, actualIntro);
  }
}
