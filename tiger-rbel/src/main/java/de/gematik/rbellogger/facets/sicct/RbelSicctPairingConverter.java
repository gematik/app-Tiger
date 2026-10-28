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
import de.gematik.rbellogger.data.core.TracingMessagePairFacet;
import de.gematik.rbellogger.facets.apdu.RbelApduCommandFacet;
import de.gematik.rbellogger.facets.apdu.RbelApduResponseFacet;
import java.util.Optional;

/**
 * Pairs a SICCT response with the command of the same connection carrying the same sequence number
 * and address.
 */
@ConverterInfo(onlyActivateFor = "sicct")
public class RbelSicctPairingConverter extends RbelConverterPlugin {

  @Override
  public int getPriority() {
    // run after the RbelSicctEnvelopeConverter
    return -10;
  }

  @Override
  public void consumeElement(RbelElement element, RbelConversionExecutor context) {
    if (element.getParentNode() != null || element.hasFacet(TracingMessagePairFacet.class)) {
      return;
    }
    findEnvelope(element, SicctMessageType.R_RESPONSE)
        .flatMap(
            response ->
                context.findPreviousMessageInSameConnectionAs(
                    element,
                    candidate ->
                        !candidate.hasFacet(TracingMessagePairFacet.class)
                            && findEnvelope(candidate, SicctMessageType.C_COMMAND)
                                .filter(command -> answers(response, command))
                                .isPresent()))
        .ifPresent(
            command -> {
              final TracingMessagePairFacet pair = new TracingMessagePairFacet(element, command);
              command.addFacet(pair);
              element.addFacet(pair);
              annotateTerminalAuthenticateResponse(command, element);
            });
  }

  private static void annotateTerminalAuthenticateResponse(
      RbelElement command, RbelElement response) {
    apdu(command, RbelApduCommandFacet.class)
        .filter(EhealthTerminalAuthenticate::isCommand)
        .ifPresent(
            commandApdu ->
                apdu(response, RbelApduResponseFacet.class)
                    .ifPresent(
                        responseApdu ->
                            EhealthTerminalAuthenticate.annotateResponse(
                                commandApdu, responseApdu)));
  }

  private static <T> Optional<T> apdu(RbelElement message, Class<T> facetClass) {
    return message
        .getFacet(RbelSicctEnvelopeFacet.class)
        .flatMap(envelope -> envelope.getBody().getFacet(facetClass));
  }

  private static boolean answers(RbelSicctEnvelopeFacet response, RbelSicctEnvelopeFacet command) {
    return response.getAddress() == command.getAddress()
        && response.getSequenceNumber().seekValue().equals(command.getSequenceNumber().seekValue());
  }

  private static Optional<RbelSicctEnvelopeFacet> findEnvelope(
      RbelElement message, SicctMessageType messageType) {
    return message
        .getFacet(RbelSicctEnvelopeFacet.class)
        .filter(envelope -> envelope.findMessageType().filter(messageType::equals).isPresent());
  }
}
