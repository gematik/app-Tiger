/*
 * Copyright (c) 2023 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
        if (targetElement.getSize() <= 8
            || !startsWithCetpMarker(targetElement.getRawContent())) {
            return;
        }
        byte[] messageLength = new byte[CETP_INTRO_MARKER.length];
        System.arraycopy(targetElement.getRawContent(), 4, messageLength, 0, CETP_INTRO_MARKER.length);
        byte[] messageBody = new byte[targetElement.getRawContent().length - 8];
        System.arraycopy(targetElement.getRawContent(), 8, messageBody, 0,
            targetElement.getRawContent().length - 8);

        final RbelCetpFacet cetpFacet = RbelCetpFacet.builder()
            .messageLength(
                RbelElement.wrap(messageLength, targetElement, java.nio.ByteBuffer.wrap(messageLength).getInt()))
            .body(converter.convertElement(messageBody, targetElement))
            .build();

        targetElement.addFacet(cetpFacet);
    }

    private boolean startsWithCetpMarker(byte[] rawContent) {
        byte[] actualIntro = ArrayUtils.subarray(rawContent, 0, 4);
        return Objects.deepEquals(CETP_INTRO_MARKER, actualIntro);
    }
}
