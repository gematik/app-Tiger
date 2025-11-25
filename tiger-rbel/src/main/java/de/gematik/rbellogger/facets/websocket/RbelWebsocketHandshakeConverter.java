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
package de.gematik.rbellogger.facets.websocket;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConversionPhase;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.converter.ConverterInfo;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.TracingMessagePairFacet;
import de.gematik.rbellogger.facets.http.*;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@ConverterInfo(onlyActivateFor = "websocket")
@Slf4j
public class RbelWebsocketHandshakeConverter extends RbelConverterPlugin {

  @Override
  public RbelConversionPhase getPhase() {
    return RbelConversionPhase.CONTENT_ENRICHMENT;
  }

  @Override
  public void consumeElement(RbelElement rbelElement, RbelConversionExecutor converter) {
    val httpMessageFacet = rbelElement.getFacet(RbelHttpMessageFacet.class);
    if (httpMessageFacet.isEmpty()) {
      return;
    }
    converter.waitForAllElementsBeforeGivenToBeParsed(rbelElement);
    if (rbelElement.hasFacet(RbelHttpRequestFacet.class)
        && hasWebsocketHandshakeHeaders(httpMessageFacet)) {
      rbelElement.addFacet(new RbelWebsocketHandshakeFacet());
    } else if (rbelElement.getFacet(RbelHttpResponseFacet.class).stream()
            .anyMatch(resp -> "101".equals(resp.getResponseCode().getRawStringContent()))
        && rbelElement
            .getFacet(TracingMessagePairFacet.class)
            .map(TracingMessagePairFacet::getRequest)
            .stream()
            .anyMatch(req -> req.hasFacet(RbelWebsocketHandshakeFacet.class))
        && hasWebsocketHandshakeHeaders(httpMessageFacet)) {
      rbelElement.addFacet(new RbelWebsocketHandshakeFacet());
    }
  }

  private static boolean hasWebsocketHandshakeHeaders(
      Optional<RbelHttpMessageFacet> httpMessageFacet) {
    return httpMessageFacet
        .map(RbelHttpMessageFacet::getHeader)
        .flatMap(el -> el.getFacet(RbelHttpHeaderFacet.class))
        .filter(
            headers ->
                headers
                    .getCaseInsensitiveMatches("upgrade")
                    .map(RbelElement::getRawStringContent)
                    .filter(Objects::nonNull)
                    .anyMatch(header -> header.equalsIgnoreCase("websocket")))
        .filter(
            values ->
                values
                    .getCaseInsensitiveMatches("connection")
                    .map(RbelElement::getRawStringContent)
                    .filter(Objects::nonNull)
                    .anyMatch(header -> header.equalsIgnoreCase("upgrade")))
        .isPresent();
  }
}
