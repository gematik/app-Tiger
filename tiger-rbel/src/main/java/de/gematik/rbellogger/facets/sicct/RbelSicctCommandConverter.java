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
package de.gematik.rbellogger.facets.sicct;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.converter.ConverterInfo;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelBinaryFacet;
import de.gematik.rbellogger.data.core.RbelFacet;
import de.gematik.rbellogger.data.core.RbelValueFacet;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;

@ConverterInfo(onlyActivateFor = "sicct")
@Slf4j
public class RbelSicctCommandConverter extends RbelConverterPlugin {

  @Override
  public void consumeElement(final RbelElement element, final RbelConversionExecutor context) {
    if (element.getParentNode() == null) {
      return;
    }
    if (element.getParentNode().hasFacet(RbelSicctEnvelopeFacet.class)
        && findMessageType(element)
            .map(msgType -> msgType == SicctMessageType.C_COMMAND)
            .orElse(false)) {
      final RbelSicctCommandFacet sicctCommandFacet = buildBodyFacet(element);
      element.addFacet(sicctCommandFacet);
      context.convertElement(sicctCommandFacet.getHeader());
      context.convertElement(sicctCommandFacet.getBody());
    } else if (element.getParentNode().hasFacet(RbelSicctCommandFacet.class)
        && element.getKey().filter("header"::equals).isPresent()
        && findMessageType(element)
            .map(msgType -> msgType == SicctMessageType.C_COMMAND)
            .orElse(false)) {
      element.addFacet(buildHeaderFacet(element));
    }
  }

  private RbelFacet buildHeaderFacet(RbelElement element) {
    // compare SICCT-specification, chapter 5.1
    final RbelElement cla = extractHeaderValue(element, 0, 1);
    final RbelElement ins = extractHeaderValue(element, 1, 2);
    final RbelElement p1 = extractHeaderValue(element, 2, 3);
    final RbelElement p2 = extractHeaderValue(element, 3, 4);

    return RbelSicctHeaderFacet.builder()
        .cla(cla)
        .ins(ins)
        .p1(p1)
        .p2(p2)
        .command(RbelSicctCommand.from(cla, ins).orElse(null))
        .build();
  }

  private static RbelElement extractHeaderValue(
      RbelElement element, int startIndexInclusive, int endIndexExclusive) {
    final RbelElement headerValue =
        new RbelElement(
            ArrayUtils.subarray(element.getRawContent(), startIndexInclusive, endIndexExclusive),
            element);
    headerValue.addFacet(new RbelBinaryFacet());
    headerValue.addFacet(RbelValueFacet.of(headerValue.getRawContent()[0]));
    return headerValue;
  }

  private RbelSicctCommandFacet buildBodyFacet(RbelElement element) {
    byte[] header = ArrayUtils.subarray(element.getRawContent(), 0, 4);
    byte[] body = ArrayUtils.subarray(element.getRawContent(), 4, element.getRawContent().length);
    return RbelSicctCommandFacet.builder()
        .header(new RbelElement(header, element))
        .body(new RbelElement(body, element))
        .build();
  }

  private Optional<SicctMessageType> findMessageType(RbelElement element) {
    return element
        .getFacet(RbelSicctEnvelopeFacet.class)
        .or(() -> element.getParentNode().getFacet(RbelSicctEnvelopeFacet.class))
        .or(() -> element.getParentNode().getParentNode().getFacet(RbelSicctEnvelopeFacet.class))
        .map(RbelSicctEnvelopeFacet::getMessageType)
        .flatMap(RbelElement::seekValue)
        .filter(SicctMessageType.class::isInstance)
        .map(SicctMessageType.class::cast);
  }
}
