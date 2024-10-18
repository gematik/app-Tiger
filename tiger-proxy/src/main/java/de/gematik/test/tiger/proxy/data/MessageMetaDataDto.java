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

package de.gematik.test.tiger.proxy.data;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.*;
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
  private String path;
  private List<String> additionalInformation = new ArrayList<>();
  private Integer decryptedResponseCode;
  private String method;
  private Integer responseCode;
  private String recipient;
  private String sender;
  private String bundledServerNameSender;
  private String bundledServerNameReceiver;
  private long sequenceNumber;
  private String menuInfoString;
  private ZonedDateTime timestamp;
  private boolean isRequest;
  private String pairedUuid;

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
                    .flatMap(RbelHostnameFacet::tryToExtractServerName)
                    .filter(s -> !s.startsWith("localhost") && !s.startsWith("127.0.0.1"))
                    .orElse(""))
            .bundledServerNameReceiver(
                el.getFacet(RbelTcpIpMessageFacet.class)
                    .map(RbelTcpIpMessageFacet::getReceiver)
                    .flatMap(RbelHostnameFacet::tryToExtractServerName)
                    .filter(s -> !s.startsWith("localhost") && !s.startsWith("127.0.0.1"))
                    .orElse(""))
            .pairedUuid(
                el.getFacet(TracingMessagePairFacet.class)
                    .flatMap(f -> f.getOtherMessage(el))
                    .map(RbelElement::getUuid)
                    .orElse(null));

    if (el.hasFacet(RbelHttpRequestFacet.class)) {
      RbelHttpRequestFacet req = el.getFacetOrFail(RbelHttpRequestFacet.class);
      builder
          .path(req.getPath().getRawStringContent())
          .method(req.getMethod().getRawStringContent())
          .responseCode(null);

      List<Optional<RbelRequestFacet>> requestFacets = new ArrayList<>();
      el.getChildNodes()
          .forEach(
              child -> {
                List<RbelElement> nestedElements =
                    RbelElement.findAllNestedElementsWithFacet(child, RbelRequestFacet.class);
                requestFacets.addAll(
                    nestedElements.stream()
                        .map(e -> e.getFacet(RbelRequestFacet.class))
                        .filter(Optional::isPresent)
                        .toList());
              });
      builder.additionalInformation(
          requestFacets.stream()
              .flatMap(Optional::stream)
              .map(a -> a.getMenuInfoString())
              .toList());

    } else if (el.hasFacet(RbelHttpResponseFacet.class)) {
      builder.responseCode(
          Integer.parseInt(
              el.getFacetOrFail(RbelHttpResponseFacet.class)
                  .getResponseCode()
                  .getRawStringContent()));

      List<Optional<RbelResponseFacet>> responseFacets = new ArrayList<>();
      el.getChildNodes()
          .forEach(
              child -> {
                List<RbelElement> nestedElements =
                    RbelElement.findAllNestedElementsWithFacet(child, RbelResponseFacet.class);
                responseFacets.addAll(
                    nestedElements.stream()
                        .map(e -> e.getFacet(RbelResponseFacet.class))
                        .filter(Optional::isPresent)
                        .toList());
              });

      builder.additionalInformation(
          responseFacets.stream()
              .flatMap(Optional::stream)
              .map(a -> a.getMenuInfoString())
              .toList());
    }

    builder.isRequest(el.hasFacet(RbelRequestFacet.class));
    builder.timestamp(
        el.getFacet(RbelMessageTimingFacet.class)
            .map(RbelMessageTimingFacet::getTransmissionTime)
            .orElse(null));
    builder.menuInfoString(
        el.getFacet(RbelRequestFacet.class)
            .map(RbelRequestFacet::getMenuInfoString)
            .or(
                () ->
                    el.getFacet(RbelResponseFacet.class).map(RbelResponseFacet::getMenuInfoString))
            .orElse(null));
    return builder.build();
  }

  public static long getElementSequenceNumber(RbelElement rbelElement) {
    return rbelElement
        .getFacet(RbelTcpIpMessageFacet.class)
        .map(RbelTcpIpMessageFacet::getSequenceNumber)
        .orElse(0L);
  }
}
