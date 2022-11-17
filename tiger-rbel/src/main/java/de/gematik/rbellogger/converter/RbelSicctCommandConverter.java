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
import de.gematik.rbellogger.data.sicct.RbelSicctCommand;
import de.gematik.rbellogger.data.sicct.SicctMessageType;
import de.gematik.rbellogger.util.RbelException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;

import java.util.Optional;

@Slf4j
public class RbelSicctCommandConverter implements RbelConverterPlugin {

    @Override
    public void consumeElement(final RbelElement element, final RbelConverter context) {
        if (element.getParentNode() == null) {
            return;
        }
        if (element.getParentNode().hasFacet(RbelSicctEnvelopeFacet.class) &&
            findMessageType(element)
                .map(msgType -> msgType == SicctMessageType.C_COMMAND)
                .orElse(false)) {
            final RbelSicctCommandFacet sicctCommandFacet = buildBodyFacet(element);
            element.addFacet(sicctCommandFacet);
            context.convertElement(sicctCommandFacet.getHeader());
            context.convertElement(sicctCommandFacet.getBody());
        } else if (element.getParentNode().hasFacet(RbelSicctCommandFacet.class) &&
            findMessageType(element)
                .map(msgType -> msgType == SicctMessageType.C_COMMAND)
                .orElse(false)) {
            element.addFacet(buildHeaderFacet(element));
        }
    }

    private RbelFacet buildHeaderFacet(RbelElement element) {
        // compare SICCT-specification, chapter 5.1
        final RbelElement cla = new RbelElement(ArrayUtils.subarray(element.getRawContent(), 0, 1), element);
        final RbelElement ins = new RbelElement(ArrayUtils.subarray(element.getRawContent(), 1, 2), element);
        final RbelElement p1 = new RbelElement(ArrayUtils.subarray(element.getRawContent(), 2, 3), element);
        final RbelElement p2 = new RbelElement(ArrayUtils.subarray(element.getRawContent(), 3, 4), element);

        cla.addFacet(new RbelBinaryFacet());
        ins.addFacet(new RbelBinaryFacet());
        p1.addFacet(new RbelBinaryFacet());
        p2.addFacet(new RbelBinaryFacet());

        return RbelSicctHeaderFacet.builder()
            .cla(cla)
            .ins(ins)
            .p1(p1)
            .p2(p2)
            .command(RbelSicctCommand.from(cla, ins).orElse(null))
            .build();
    }

    private RbelSicctCommandFacet buildBodyFacet(RbelElement element) {
        byte[] header = ArrayUtils.subarray(element.getRawContent(), 0, 4);
        byte[] body = ArrayUtils.subarray(element.getRawContent(), 4,
            element.getRawContent().length);
        return RbelSicctCommandFacet.builder()
            .header(new RbelElement(header, element))
            .body(new RbelElement(body, element))
            .build();
    }

    private Optional<SicctMessageType> findMessageType(RbelElement element) {
        return element.getFacet(RbelSicctEnvelopeFacet.class)
            .or(() -> element.getParentNode().getFacet(RbelSicctEnvelopeFacet.class))
            .or(() -> element.getParentNode().getParentNode().getFacet(RbelSicctEnvelopeFacet.class))
            .map(RbelSicctEnvelopeFacet::getMessageType)
            .flatMap(RbelElement::seekValue)
            .filter(SicctMessageType.class::isInstance)
            .map(SicctMessageType.class::cast);
    }
}
