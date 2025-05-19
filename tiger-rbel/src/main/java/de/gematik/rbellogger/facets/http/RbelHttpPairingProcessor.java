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

package de.gematik.rbellogger.facets.http;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConversionPhase;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.converter.*;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelNoteFacet;
import de.gematik.rbellogger.data.core.RbelNoteFacet.NoteStyling;
import de.gematik.rbellogger.data.core.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.data.core.TracingMessagePairFacet;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ConverterInfo(
    dependsOn = {RbelHttpRequestConverter.class, RbelHttpResponseConverter.class},
    addAutomatically = false)
public class RbelHttpPairingProcessor extends RbelConverterPlugin {

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

    if (targetElement.hasFacet(RbelHttpResponseFacet.class)) {
      converter
          .findPreviousMessageInSameConnectionAs(
              targetElement, msg -> msg.hasFacet(RbelHttpRequestFacet.class))
          .ifPresent(
              previousRequest -> {
                log.atError()
                    .addArgument(targetElement::getUuid)
                    .addArgument(previousRequest::getUuid)
                    .addArgument(converter::converterName)
                    .log("Pairing {} with {} in {}");

                targetElement.addFacet(
                    new RbelNoteFacet(
                        "PAIRING by TracingMessageFrame " + previousRequest.getUuid(),
                        NoteStyling.ERROR));
                targetElement.addFacet(new TracingMessagePairFacet(targetElement, previousRequest));
                previousRequest.addFacet(
                    new TracingMessagePairFacet(targetElement, previousRequest));
              });
    }
  }
}
