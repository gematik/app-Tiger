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
package de.gematik.rbellogger.renderer;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.*;
import de.gematik.rbellogger.data.core.ProxyTransmissionHistory.TransmissionHop;
import de.gematik.rbellogger.facets.timing.RbelMessageTimingFacet;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Slf4j
public class MessageMetaDataDto {

  private String uuid;
  private String menuInfoString;
  @Builder.Default private List<String> additionalInformation = new ArrayList<>();
  @Builder.Default private List<TransmissionHop> transmissionHops = new ArrayList<>();
  private String recipient;
  private String sender;
  private String bundledServerNameSender;
  private String bundledServerNameReceiver;
  private long sequenceNumber;
  private ZonedDateTime timestamp;
  private boolean isRequest;
  private String color;
  private String symbol;
  private String abbrev;
  private boolean removed;

  public static MessageMetaDataDto createFrom(RbelElement el) {
    return MessageMetaDataDto.builder()
        .uuid(el.getUuid())
        .sequenceNumber(getElementSequenceNumber(el))
        .sender(
            el.getFacet(RbelTcpIpMessageFacet.class)
                .map(RbelTcpIpMessageFacet::getSender)
                .filter(element -> element.getRawStringContent() != null)
                .flatMap(element -> Optional.of(element.getRawStringContent()))
                .orElse(""))
        .recipient(
            el.getFacet(RbelTcpIpMessageFacet.class)
                .map(RbelTcpIpMessageFacet::getReceiver)
                .filter(element -> element.getRawStringContent() != null)
                .flatMap(element -> Optional.of(element.getRawStringContent()))
                .orElse(""))
        .bundledServerNameSender(getBundledServerName(el, RbelTcpIpMessageFacet::getSender))
        .bundledServerNameReceiver(getBundledServerName(el, RbelTcpIpMessageFacet::getReceiver))
        .additionalInformation(
            el.findAllNestedFacetsStream(RbelMessageInfoFacet.class)
                .map(RbelMessageInfoFacet::getMenuInfoString)
                .toList())
        .transmissionHops(
            el.getFacet(ProxyTransmissionHistory.class)
                .map(ProxyTransmissionHistory::getTransmissionHops)
                .orElse(List.of()))
        .menuInfoString(
            el.getFacet(RbelMessageInfoFacet.class)
                .map(RbelMessageInfoFacet::getMenuInfoString)
                .orElse("<noMenuInfoString>"))
        .symbol(
            el.getFacet(RbelMessageInfoFacet.class)
                .map(RbelMessageInfoFacet::getSymbol)
                .orElse("<noSymbol>"))
        .abbrev(
            el.getFacet(RbelMessageInfoFacet.class)
                .map(RbelMessageInfoFacet::getAbbrev)
                .orElse("<noAbbrev>"))
        .color(
            el.getFacet(RbelMessageInfoFacet.class)
                .map(RbelMessageInfoFacet::getColor)
                .orElse("<noColor>"))
        .isRequest(el.hasFacet(RbelRequestFacet.class))
        .timestamp(
            el.getFacet(RbelMessageTimingFacet.class)
                .map(RbelMessageTimingFacet::getTransmissionTime)
                .orElse(null))
        .build();
  }

  public static long getElementSequenceNumber(RbelElement rbelElement) {
    return rbelElement.getSequenceNumber().orElse(0L);
  }

  private static String getBundledServerName(
      RbelElement el, Function<RbelTcpIpMessageFacet, RbelElement> senderOrReceiverFunction) {
    return el.getFacet(RbelTcpIpMessageFacet.class)
        .map(senderOrReceiverFunction)
        .flatMap(e -> e.getFacet(RbelSocketAddressFacet.class))
        .flatMap(MessageMetaDataDto::getDisplayName)
        .orElse("");
  }

  private static Optional<String> getDisplayName(RbelSocketAddressFacet facet) {
    return facet
        .getBundledServerName()
        .map(RbelElement::getRawStringContent)
        .or(() -> getNonLocalhostDomain(facet))
        .or(() -> Optional.of(facet.toString().replace(":", "#58;")));
  }

  private static Optional<String> getNonLocalhostDomain(RbelSocketAddressFacet facet) {
    return facet
        .getDomain()
        .printValue()
        .filter(domain -> !domain.startsWith("localhost") && !domain.startsWith("127.0.0.1"));
  }
}
