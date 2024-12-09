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

package de.gematik.rbellogger.data.facet;

import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelMessageRenderer;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
public class RbelTcpIpMessageFacet implements RbelFacet {

  static {
    RbelHtmlRenderer.registerFacetRenderer(new RbelMessageRenderer());
  }

  private final Long sequenceNumber;
  private final String receivedFromRemoteWithUrl;
  private final RbelElement sender;
  private final RbelElement receiver;

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<RbelElement>().with("sender", sender).with("receiver", receiver);
  }

  public RbelHostname getSenderHostname() {
    return hostname(sender).toRbelHostname();
  }

  public RbelHostname getReceiverHostname() {
    return hostname(receiver).toRbelHostname();
  }

  private static RbelHostnameFacet hostname(RbelElement element) {
    return element.getFacetOrFail(RbelHostnameFacet.class);
  }

  public static Optional<RbelElement> findAndPairMatchingRequest(
      RbelElement response, RbelConverter context, Class<? extends RbelFacet> requestFacetClass) {
    context.waitForAllElementsBeforeGivenToBeParsed(response.findRootElement());
    return response
        .getFacet(TracingMessagePairFacet.class)
        .map(TracingMessagePairFacet::getRequest)
        .or(
            () ->
                context
                    .messagesStreamLatestFirst()
                    .dropWhile(e -> e != response)
                    .filter(e -> e != response)
                    .filter(e -> e.hasFacet(requestFacetClass))
                    .filter(e -> !e.hasFacet(TracingMessagePairFacet.class))
                    .filter(request -> haveOppositeTcpIpEndpoints(request, response))
                    .filter(
                        request -> {
                          var pair =
                              TracingMessagePairFacet.builder()
                                  .request(request)
                                  .response(response)
                                  .build();
                          response.addFacet(pair);
                          request.addFacet(pair);
                          return true;
                        })
                    .findFirst());
  }

  private static boolean haveOppositeTcpIpEndpoints(
      RbelElement requestElement, RbelElement responseElement) {
    return requestElement
        .getFacet(RbelTcpIpMessageFacet.class)
        .filter(
            request ->
                responseElement
                    .getFacet(RbelTcpIpMessageFacet.class)
                    .filter(request::hasOppositeEndpointsOf)
                    .isPresent())
        .isPresent();
  }

  private boolean hasOppositeEndpointsOf(RbelTcpIpMessageFacet other) {
    return hostname(sender).domainAndPortEquals(hostname(other.receiver))
        && hostname(receiver).domainAndPortEquals(hostname(other.sender));
  }
}
