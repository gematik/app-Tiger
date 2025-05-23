/*
 *
 * Copyright 2021-2025 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.rbellogger.facets.cetp;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConversionPhase;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.data.RbelElement;

public class RbelCetpConverter extends RbelConverterPlugin {

  private static final byte[] CETP_INTRO_MARKER = "CETP".getBytes();
  public static final int MIN_CETP_MESSAGE_LENGTH = CETP_INTRO_MARKER.length + 4;

  @Override
  public RbelConversionPhase getPhase() {
    return RbelConversionPhase.PROTOCOL_PARSING;
  }

  @Override
  public void consumeElement(
      final RbelElement targetElement, final RbelConversionExecutor converter) {
    var content = targetElement.getContent();
    var contentSize = targetElement.getSize();
    if (targetElement.getSize() < MIN_CETP_MESSAGE_LENGTH
        || !content.startsWith(CETP_INTRO_MARKER)) {
      return;
    }
    byte[] messageLengthBytes = content.subArray(CETP_INTRO_MARKER.length, MIN_CETP_MESSAGE_LENGTH);
    int messageLength = java.nio.ByteBuffer.wrap(messageLengthBytes).getInt();
    if (contentSize < MIN_CETP_MESSAGE_LENGTH + messageLength) {
      return;
    }

    byte[] messageBody =
        content.subArray(MIN_CETP_MESSAGE_LENGTH, MIN_CETP_MESSAGE_LENGTH + messageLength);

    final RbelCetpFacet cetpFacet =
        RbelCetpFacet.builder()
            .menuInfoString("CETP")
            .messageLength(RbelElement.wrap(messageLengthBytes, targetElement, messageLength))
            .body(new RbelElement(messageBody, targetElement))
            .build();

    targetElement.addFacet(cetpFacet);
    targetElement.setUsedBytes(MIN_CETP_MESSAGE_LENGTH + messageLength);
  }

  public static class RbelCetpBodyConverter extends RbelConverterPlugin {
    @Override
    public void consumeElement(RbelElement rbelElement, RbelConversionExecutor converter) {
      rbelElement
          .getFacet(RbelCetpFacet.class)
          .ifPresent(
              cetpFacet -> {
                converter.convertElement(cetpFacet.getBody());
              });
    }
  }
}
