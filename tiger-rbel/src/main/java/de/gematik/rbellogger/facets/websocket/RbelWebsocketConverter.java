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
import de.gematik.rbellogger.data.core.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.facets.http.RbelHttpMessageFacet;
import de.gematik.rbellogger.facets.http.RbelHttpResponseConverter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@Slf4j
@ConverterInfo(
    dependsOn = {RbelHttpResponseConverter.class},
    onlyActivateFor = "websocket")
public class RbelWebsocketConverter extends RbelConverterPlugin {

  @Override
  public RbelConversionPhase getPhase() {
    return RbelConversionPhase.PROTOCOL_PARSING;
  }

  @Override
  public void consumeElement(RbelElement rbelElement, RbelConversionExecutor converter) {
    // only root TCP/IP messages are considered for websocket conversion
    if (rbelElement.getParentNode() != null
        || !rbelElement.hasFacet(RbelTcpIpMessageFacet.class)
        || rbelElement.hasFacet(RbelWebsocketHandshakeFacet.class)
        || rbelElement.hasFacet(RbelHttpMessageFacet.class)) {
      return;
    }
    // is preceding message websocket or handshake message?
    val previousMessage =
        converter
            .getPreviousMessagesInSameConnectionAs(rbelElement)
            .findFirst()
            .filter(
                prevMessage ->
                    prevMessage.hasFacet(RbelWebsocketMessageFacet.class)
                        || prevMessage.hasFacet(RbelWebsocketHandshakeFacet.class));
    if (previousMessage.isEmpty()) {
      return;
    }
    try {
      new RbelWebsocketMessageConverter(rbelElement, converter, previousMessage.get())
          .parseWebsocketMessage();
    } catch (Exception e) {
      log.error("Error while parsing websocket message", e);
    }
  }
}
