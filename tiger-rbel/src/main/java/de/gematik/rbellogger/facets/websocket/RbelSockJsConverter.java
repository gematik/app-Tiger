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
import de.gematik.rbellogger.facets.http.RbelHttpResponseConverter;
import de.gematik.rbellogger.facets.jackson.RbelJsonConverter;
import de.gematik.rbellogger.facets.jackson.RbelJsonFacet;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@Slf4j
@ConverterInfo(
    dependsOn = {RbelHttpResponseConverter.class, RbelJsonConverter.class},
    onlyActivateFor = "websocket")
public class RbelSockJsConverter extends RbelConverterPlugin {

  @Override
  public RbelConversionPhase getPhase() {
    return RbelConversionPhase.CONTENT_PARSING;
  }

  @Override
  public void consumeElement(RbelElement message, RbelConversionExecutor converter) {
    // only root TCP/IP messages are considered for websocket conversion
    if (message.getParentNode() == null
        || !message.getParentNode().hasFacet(RbelWebsocketMessageFacet.class)) {
      return;
    }
    boolean isSockJsStream =
        getPreviousMessage(message.findRootElement(), converter)
            .map(RbelElement::getChildNodes)
            .stream()
            .flatMap(List::stream)
            .flatMap(el -> el.getFacets().stream())
            .anyMatch(RbelSockJsFacet.class::isInstance);
    // TODO exclude close command (since after that we can reconnect)
    if (message.getContent().startsWith(new byte[] {'o'})
        && message.getContent().size() == 1
        && !isSockJsStream) {
      addSockJsFacetWithoutContent(message, RbelSockJsFrameType.OPEN_FRAME);
    } else if (message.getContent().startsWith(new byte[] {'h'})
        && message.getContent().size() == 1) {
      addSockJsFacetWithoutContent(message, RbelSockJsFrameType.HEARTBEAT_FRAME);
    } else if (message.getContent().startsWith(new byte[] {'c'})
        && message.getContent().size() == 1) {
      addSockJsFacetWithoutContent(message, RbelSockJsFrameType.CLOSE_FRAME);
    } else if (isSockJsStream) {
      int offset = message.getContent().startsWith(new byte[] {'a'}) ? 1 : 0;
      if (message.getContent().startsWith(new byte[] {'['}, offset)) {
        val payloadElement =
            new RbelElement(
                message.getContent().subArray(offset, message.getContent().size()).toByteArray(),
                message);
        val sockJsFacet =
            new RbelSockJsFacet(
                RbelElement.wrap(message, RbelSockJsFrameType.MESSAGE_FRAME), null, payloadElement);
        message.addFacet(sockJsFacet);
        message.removeFacetsOfType(RbelJsonFacet.class);
        converter.convertElement(payloadElement);
      }
    }
  }

  private static void addSockJsFacetWithoutContent(
      RbelElement message, RbelSockJsFrameType openFrame) {
    final RbelSockJsFacet sockJsFacet =
        new RbelSockJsFacet(
            RbelElement.wrap(message, openFrame),
            null,
            new RbelElement(message.getContent().toByteArray(), message));
    message.addFacet(sockJsFacet);
  }
}
