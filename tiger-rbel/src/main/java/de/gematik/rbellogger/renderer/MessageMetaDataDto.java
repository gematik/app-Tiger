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
import de.gematik.rbellogger.facets.timing.RbelMessageTimingFacet;
import de.gematik.rbellogger.util.RbelSocketAddress;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
  private String recipient;
  private String sender;
  private String bundledServerNameSender;
  private String bundledServerNameReceiver;
  private long sequenceNumber;
  private ZonedDateTime timestamp;
  private boolean isRequest;
  private String pairedUuid;
  private String color;
  private String symbol;
  private String abbrev;
  private boolean removed;

  public static MessageMetaDataDto createFrom(RbelElement el) {
    MessageMetaDataDto.MessageMetaDataDtoBuilder builder = MessageMetaDataDto.builder();
    builder =
        builder
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
            .bundledServerNameSender(
                el.getFacet(RbelTcpIpMessageFacet.class)
                    .map(RbelTcpIpMessageFacet::getSender)
                    .flatMap(e -> e.getFacet(RbelHostnameFacet.class))
                    .map(RbelHostnameFacet::toRbelSocketAddress)
                    .map(RbelSocketAddress::printHostname)
                    .filter(s -> !s.startsWith("localhost") && !s.startsWith("127.0.0.1"))
                    .or(
                        () ->
                            el.getFacet(RbelTcpIpMessageFacet.class)
                                .map(RbelTcpIpMessageFacet::getSender)
                                .flatMap(e -> e.getFacet(RbelHostnameFacet.class))
                                .map(RbelHostnameFacet::toString)
                                .map(s -> s.replace(":", "#58;")))
                    .orElse(""))
            .bundledServerNameReceiver(
                el.getFacet(RbelTcpIpMessageFacet.class)
                    .map(RbelTcpIpMessageFacet::getReceiver)
                    .flatMap(e -> e.getFacet(RbelHostnameFacet.class))
                    .map(RbelHostnameFacet::toRbelSocketAddress)
                    .map(RbelSocketAddress::printHostname)
                    .filter(s -> !s.startsWith("localhost") && !s.startsWith("127.0.0.1"))
                    .or(
                        () ->
                            el.getFacet(RbelTcpIpMessageFacet.class)
                                .map(RbelTcpIpMessageFacet::getReceiver)
                                .flatMap(e -> e.getFacet(RbelHostnameFacet.class))
                                .map(RbelHostnameFacet::toString)
                                .map(s -> s.replace(":", "#58;")))
                    .orElse(""))
            .pairedUuid(
                el.getFacet(TracingMessagePairFacet.class)
                    .flatMap(f -> f.getOtherMessage(el))
                    .map(RbelElement::getUuid)
                    .orElse(null));

    builder.additionalInformation(
        el.findAllNestedFacetsStream(RbelMessageInfoFacet.class)
            .map(RbelMessageInfoFacet::getMenuInfoString)
            .toList());
    builder.menuInfoString(
        el.getFacet(RbelMessageInfoFacet.class)
            .map(RbelMessageInfoFacet::getMenuInfoString)
            .orElse("<noMenuInfoString>"));
    builder.symbol(
        el.getFacet(RbelMessageInfoFacet.class)
            .map(RbelMessageInfoFacet::getSymbol)
            .orElse("<noSymbol>"));
    builder.abbrev(
        el.getFacet(RbelMessageInfoFacet.class)
            .map(RbelMessageInfoFacet::getAbbrev)
            .orElse("<noAbbrev>"));
    builder.color(
        el.getFacet(RbelMessageInfoFacet.class)
            .map(RbelMessageInfoFacet::getColor)
            .orElse("<noColor>"));

    builder.isRequest(el.hasFacet(RbelRequestFacet.class));
    builder.timestamp(
        el.getFacet(RbelMessageTimingFacet.class)
            .map(RbelMessageTimingFacet::getTransmissionTime)
            .orElse(null));
    return builder.build();
  }

  public static long getElementSequenceNumber(RbelElement rbelElement) {
    return rbelElement.getSequenceNumber().orElse(0L);
  }
}
