/*
 * Copyright (c) 2024 gematik GmbH
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

package de.gematik.test.tiger.proxy.data;

import static de.gematik.rbellogger.file.RbelFileWriter.PAIRED_MESSAGE_UUID;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.facet.RbelFacet;
import de.gematik.rbellogger.data.facet.RbelHttpRequestFacet;
import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import de.gematik.rbellogger.util.RbelMessagePostProcessor;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class TracingMessagePairFacet implements RbelFacet {

  private final RbelElement response;
  private final RbelElement request;

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<>();
  }

  public Optional<RbelElement> getOtherMessage(RbelElement thisMessage) {
    if (thisMessage.equals(request)) {
      return Optional.of(response);
    } else if (thisMessage.equals(response)) {
      return Optional.of(request);
    } else {
      return Optional.empty();
    }
  }

  public boolean isResponse(RbelElement msg) {
    return response == msg;
  }

  public static final RbelMessagePostProcessor pairingPostProcessor =
      (el, conv, json) -> {
        if (json.has(PAIRED_MESSAGE_UUID)) {
          final String partnerUuid = json.getString(PAIRED_MESSAGE_UUID);
          final Optional<RbelElement> partner =
              conv.messagesStreamLatestFirst()
                  .filter(element -> element.getUuid().equals(partnerUuid))
                  .findFirst();
          if (partner.isPresent()) {
            final TracingMessagePairFacet pairFacet =
                TracingMessagePairFacet.builder().response(el).request(partner.get()).build();
            el.addFacet(pairFacet);
            partner.get().addFacet(pairFacet);
          }
        }
      };

  public static final RbelMessagePostProcessor updateHttpFacetsBasedOnPairsPostProcessor =
      (currentMessage, converter, messageObject) -> {
        var pairFacet = currentMessage.getFacet(TracingMessagePairFacet.class);

        if (pairFacet.isPresent()) {
          var pairedRequest = pairFacet.get().getRequest();
          var pairedResponse = pairFacet.get().getResponse();
          RbelHttpRequestFacet.updateResponseOfRequestFacet(pairedRequest, pairedResponse);
          RbelHttpResponseFacet.updateRequestOfResponseFacet(pairedResponse, pairedRequest);
        } else { // fallback for old .tgr files that may not have the pairedMessageUuid in the
          // file
          if (currentMessage.hasFacet(RbelHttpResponseFacet.class)) { // if it is a response
            // we assume the request before it is the request
            converter
                .messagesStreamLatestFirst()
                .dropWhile(e -> e != currentMessage)
                .filter(e -> e.hasFacet(RbelHttpRequestFacet.class))
                .findFirst()
                .ifPresent(
                    e -> {
                      RbelHttpRequestFacet.updateResponseOfRequestFacet(e, currentMessage);
                      RbelHttpResponseFacet.updateRequestOfResponseFacet(currentMessage, e);
                    });
          }
        }
      };
}
