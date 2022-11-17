/*
 * Copyright (c) 2022 gematik GmbH
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
import de.gematik.rbellogger.data.facet.*;
import de.gematik.rbellogger.data.sicct.SicctMessageType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.bouncycastle.util.encoders.Hex;

@Slf4j
public class RbelSicctEnvelopeConverter implements RbelConverterPlugin {

    @Override
    public void consumeElement(final RbelElement element, final RbelConverter context) {
        if (element.getParentNode() != null
            || element.hasFacet(RbelHttpMessageFacet.class)
            || element.getRawContent().length < 11) {
            return;
        }
        try {
            final RbelSicctEnvelopeFacet envelopeFacet = buildEnvelopeFacet(element);
            element.addFacet(envelopeFacet);
            context.convertElement(envelopeFacet.getCommand());
            envelopeFacet.getMessageType()
                .seekValue(SicctMessageType.class)
                .ifPresent(msgType -> {
                    if (msgType == SicctMessageType.C_COMMAND) {
                        element.findMessage().addFacet(new RbelRequestFacet(requestInfoString(envelopeFacet)));
                    } else {
                        element.findMessage().addFacet(new RbelResponseFacet(Hex.toHexString(
                            ArrayUtils.subarray(element.getRawContent(),
                                element.getRawContent().length - 2,
                                element.getRawContent().length)
                        )));
                    }
                });
        } catch (RuntimeException e) {
            // swallow
        }
    }

    private String requestInfoString(RbelSicctEnvelopeFacet envelopeFacet) {
        return envelopeFacet.getCommand().getFacet(RbelSicctCommandFacet.class)
            .map(RbelSicctCommandFacet::getHeader)
            .flatMap(el -> el.getFacet(RbelSicctHeaderFacet.class))
            .map(header -> header.getCommand().toString())
            .orElse("");
    }

    private RbelSicctEnvelopeFacet buildEnvelopeFacet(RbelElement element) {
        // compare SICCT-specification, chapter 6.1.4.2
        final RbelElement commandElement = new RbelElement(ArrayUtils.subarray(element.getRawContent(), 10, element.getRawContent().length), element);
        commandElement.addFacet(new RbelBinaryFacet());
        return RbelSicctEnvelopeFacet.builder()
            .messageType(RbelElement.wrap(new byte[]{element.getRawContent()[0]}, element, SicctMessageType.of(element.getRawContent()[0])))
            .srcOrDesAddress(new RbelElement(ArrayUtils.subarray(element.getRawContent(), 1, 3), element))
            .sequenceNumber(new RbelElement(ArrayUtils.subarray(element.getRawContent(), 3, 5), element))
            .abRfu(new RbelElement(ArrayUtils.subarray(element.getRawContent(), 5, 6), element))
            .length(new RbelElement(ArrayUtils.subarray(element.getRawContent(), 6, 10), element))
            .command(commandElement)
            .build();
    }
}
