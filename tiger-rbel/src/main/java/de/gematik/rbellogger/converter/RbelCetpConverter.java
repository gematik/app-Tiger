/*
 * Copyright 2024 gematik GmbH
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
 */

package de.gematik.rbellogger.converter;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelCetpFacet;

public class RbelCetpConverter implements RbelConverterPlugin {

  private static final byte[] CETP_INTRO_MARKER = "CETP".getBytes();
  public static final int MIN_CETP_MESSAGE_LENGTH = CETP_INTRO_MARKER.length + 4;

  @Override
  public void consumeElement(final RbelElement targetElement, final RbelConverter converter) {
    var content = targetElement.getContent();
    var contentSize = targetElement.getSize();
    if (contentSize < MIN_CETP_MESSAGE_LENGTH || !content.startsWith(CETP_INTRO_MARKER)) {
      return;
    }
    byte[] messageLengthBytes = content.subArray(CETP_INTRO_MARKER.length, MIN_CETP_MESSAGE_LENGTH);
    int messageLength = java.nio.ByteBuffer.wrap(messageLengthBytes).getInt();
    if (contentSize != MIN_CETP_MESSAGE_LENGTH + messageLength) {
      return;
    }

    byte[] messageBody = content.subArray(MIN_CETP_MESSAGE_LENGTH, (int) contentSize);

    final RbelCetpFacet cetpFacet =
        RbelCetpFacet.builder()
            .menuInfoString("CETP")
            .messageLength(RbelElement.wrap(messageLengthBytes, targetElement, messageLength))
            .body(converter.convertElement(messageBody, targetElement))
            .build();

    targetElement.addFacet(cetpFacet);
  }
}
