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
import de.gematik.rbellogger.RbelConversionPhase;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.converter.*;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.data.core.TracingMessagePairFacet;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ConverterInfo(
    dependsOn = {RbelSicctEnvelopeConverter.class},
    addAutomatically = true)
public class RbelSicctPairingProcessor extends RbelConverterPlugin {

  @Override
  public RbelConversionPhase getPhase() {
    return RbelConversionPhase.CONTENT_ENRICHMENT;
  }

  @Override
  public void consumeElement(RbelElement targetElement, final RbelConversionExecutor converter) {
    if (!targetElement.hasFacet(RbelTcpIpMessageFacet.class)
        || targetElement.hasFacet(TracingMessagePairFacet.class)) {
      return;
    }

    if (isSicctEnvelopeWithType(targetElement, SicctMessageType.R_COMMAND)) {
      int responseSequenceNumber =
          targetElement
              .getFacet(RbelSicctEnvelopeFacet.class)
              .map(RbelSicctEnvelopeFacet::getSequenceNumber)
              .flatMap(RbelElement::seekValue)
              .filter(Integer.class::isInstance)
              .map(Integer.class::cast)
              .orElse(-1);
      converter
          .findPreviousMessageInSameConnectionAs(
              targetElement,
              msg ->
                  isSicctEnvelopeWithType(msg, SicctMessageType.C_COMMAND)
                      && sequenceNumberMatchesGivenValue(msg, responseSequenceNumber))
          .ifPresent(
              previousRequest -> {
                targetElement.addFacet(new TracingMessagePairFacet(targetElement, previousRequest));
                previousRequest.addFacet(
                    new TracingMessagePairFacet(targetElement, previousRequest));
              });
    }
  }

  private static boolean sequenceNumberMatchesGivenValue(
      RbelElement msg, int requestSequenceNumber) {
    return msg.getFacet(RbelSicctEnvelopeFacet.class)
        .map(RbelSicctEnvelopeFacet::getSequenceNumber)
        .flatMap(RbelElement::seekValue)
        .filter(Integer.class::isInstance)
        .map(Integer.class::cast)
        .filter(responseSeqNum -> responseSeqNum == requestSequenceNumber)
        .isPresent();
  }

  private static boolean isSicctEnvelopeWithType(
      RbelElement targetElement, SicctMessageType sicctMessageType) {
    return targetElement
        .getFacet(RbelSicctEnvelopeFacet.class)
        .map(RbelSicctEnvelopeFacet::getMessageType)
        .flatMap(RbelElement::seekValue)
        .filter(type -> type == sicctMessageType)
        .isPresent();
  }
}
