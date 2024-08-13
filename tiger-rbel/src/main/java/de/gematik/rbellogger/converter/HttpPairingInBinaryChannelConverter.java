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

package de.gematik.rbellogger.converter;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelHostnameFacet;
import de.gematik.rbellogger.data.facet.RbelHttpRequestFacet;
import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import de.gematik.rbellogger.data.facet.RbelTcpIpMessageFacet;
import java.util.Objects;
import lombok.val;

@ConverterInfo(
  addAutomatically=false
)
public class HttpPairingInBinaryChannelConverter implements RbelConverterPlugin {
  @Override
  public void consumeElement(RbelElement rbelElement, RbelConverter converter) {
    if (rbelElement.getParentNode() != null) {
      return;
    }
    if (rbelElement.hasFacet(RbelHttpResponseFacet.class)) { // if it is a response
      // we assume the request before it is the request
      converter
          .messagesStreamLatestFirst()
          .dropWhile(e -> e != rbelElement)
          .filter(e -> e.hasFacet(RbelHttpRequestFacet.class))
          .findFirst()
          .ifPresent(e -> updateHttpFacetsWithPairedMessages(e, rbelElement));
    }
  }

  private static void updateHttpFacetsWithPairedMessages(
      RbelElement request, RbelElement response) {
    val requestTcpFacet = request.getFacet(RbelTcpIpMessageFacet.class);
    val responseTcpFacet = response.getFacet(RbelTcpIpMessageFacet.class);
    if (requestTcpFacet.isPresent()
        && responseTcpFacet.isPresent()
        && senderAndReceiverMatch(requestTcpFacet.get(), responseTcpFacet.get())) {
      RbelHttpRequestFacet.updateResponseOfRequestFacet(request, response);
      RbelHttpResponseFacet.updateRequestOfResponseFacet(response, request);
    }
  }

  private static boolean senderAndReceiverMatch(
      RbelTcpIpMessageFacet request, RbelTcpIpMessageFacet response) {
    return Objects.equals(
            RbelHostnameFacet.tryToExtractServerName(request.getSender()).orElse(null),
            RbelHostnameFacet.tryToExtractServerName(response.getReceiver()).orElse(null))
        && Objects.equals(
            RbelHostnameFacet.tryToExtractServerName(request.getReceiver()).orElse(null),
            RbelHostnameFacet.tryToExtractServerName(response.getSender()).orElse(null));
  }
}
