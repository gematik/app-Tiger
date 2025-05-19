/*
 * Copyright 2025 gematik GmbH
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

package de.gematik.test.tiger.proxy.data;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelMessageInfoFacet;
import de.gematik.rbellogger.data.core.RbelRequestFacet;
import de.gematik.rbellogger.data.core.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.data.core.TracingMessagePairFacet;
import de.gematik.rbellogger.facets.timing.RbelMessageTimingFacet;
import java.time.ZonedDateTime;
import java.util.List;
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
public class MetaMessageScrollableDto {
  private String uuid;
  private long offset;
  private long sequenceNumber;
  private boolean isRequest;
  private String infoString;
  private List<String> additionalInfoStrings;
  private ZonedDateTime timestamp;
  private String pairedUuid;
  private long pairedSequenceNumber;
  private String recipient;
  private String sender;

  public static MetaMessageScrollableDto createFrom(RbelElement el) {
    final var paired =
        el.getFacet(TracingMessagePairFacet.class).flatMap(f -> f.getOtherMessage(el));
    return MetaMessageScrollableDto.builder()
        .uuid(el.getUuid())
        .sequenceNumber(getElementSequenceNumber(el))
        .pairedUuid(paired.map(RbelElement::getUuid).orElse(null))
        .pairedSequenceNumber(
            paired.map(MetaMessageScrollableDto::getElementSequenceNumber).orElse(-1L))
        .sender(
            el.getFacet(RbelTcpIpMessageFacet.class)
                .map(RbelTcpIpMessageFacet::getSender)
                .map(RbelElement::getRawStringContent)
                .orElse(null))
        .recipient(
            el.getFacet(RbelTcpIpMessageFacet.class)
                .map(RbelTcpIpMessageFacet::getReceiver)
                .map(RbelElement::getRawStringContent)
                .orElse(null))
        .infoString(
            el.getFacet(RbelMessageInfoFacet.class)
                .map(RbelMessageInfoFacet::getMenuInfoString)
                .orElse("<noMenuInfoString>"))
        .additionalInfoStrings(
            el.findAllNestedFacets(RbelMessageInfoFacet.class).stream()
                .map(RbelMessageInfoFacet::getMenuInfoString)
                .toList())
        .isRequest(el.hasFacet(RbelRequestFacet.class))
        .timestamp(
            el.getFacet(RbelMessageTimingFacet.class)
                .map(RbelMessageTimingFacet::getTransmissionTime)
                .orElse(null))
        .build();
  }

  public static long getElementSequenceNumber(RbelElement rbelElement) {
    return rbelElement
        .getFacet(RbelTcpIpMessageFacet.class)
        .map(RbelTcpIpMessageFacet::getSequenceNumber)
        .orElse(-1L);
  }
}
