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
package de.gematik.test.tiger.proxy.tracing;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.data.core.ProxyTransmissionHistory;
import de.gematik.rbellogger.data.core.RbelRequestFacet;
import de.gematik.rbellogger.data.core.RbelResponseFacet;
import de.gematik.rbellogger.data.core.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.util.RbelContent;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.client.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@RequiredArgsConstructor
public class TracingPushService {

  public static final int MAX_MESSAGE_SIZE = 512 * 1024;
  private final SimpMessagingTemplate template;
  private final TigerProxy tigerProxy;
  private Logger log = LoggerFactory.getLogger(TracingPushService.class);

  public void addWebSocketListener() {
    tigerProxy.addRbelMessageListener(this::propagateRbelMessageSafe);
    tigerProxy.addNewExceptionConsumer(this::propagateExceptionSafe);

    log =
        LoggerFactory.getLogger(
            TracingPushService.class.getName() + "(" + tigerProxy.proxyName() + ")");
  }

  private void propagateExceptionSafe(Throwable exc) {
    try {
      log.atTrace().addArgument(exc::getMessage).log("Transmitting Exception: {}");
      propagateException(exc);
    } catch (RuntimeException e) {
      log.error("Error while propagating Exception", e);
      throw e;
    }
  }

  private void propagateRbelMessageSafe(RbelElement msg) {
    try {
      if (!msg.hasFacet(RbelTcpIpMessageFacet.class)) {
        log.atTrace()
            .addArgument(msg::getUuid)
            .log("Skipping propagation, not a TCP/IP message {}");
        return;
      }

      waitForPreviousMessageFullyProcessed(msg);
      propagateRbelMessage(msg);
    } catch (RuntimeException e) {
      log.error("Error while propagating new Rbel-Message", e);
      throw e;
    }
  }

  private synchronized void propagateRbelMessage(RbelElement msg) {
    log.atTrace()
        .addArgument(() -> getSequenceNumber(msg))
        .addArgument(
            () ->
                Optional.ofNullable(msg.getRawStringContent())
                    .map(String::lines)
                    .flatMap(Stream::findFirst)
                    .orElse("<>"))
        .addArgument(() -> msg.getFacet(RbelMessageMetadata.class).map(RbelMessageMetadata::toMap))
        .log("Transmitting message #{}: {}\nwith metadata: {}");
    sendMessageToRemotes(
        msg, msg.getFacet(RbelMessageMetadata.class).orElse(new RbelMessageMetadata()));
  }

  private void sendMessageToRemotes(RbelElement msg, RbelMessageMetadata metadata) {
    try {
      RbelTcpIpMessageFacet rbelTcpIpMessageFacet = msg.getFacetOrFail(RbelTcpIpMessageFacet.class);
      final RbelHostname sender =
          Optional.ofNullable(rbelTcpIpMessageFacet.getSender())
              .map(RbelElement::getRawStringContent)
              .flatMap(RbelHostname::fromString)
              .orElse(null);
      final RbelHostname receiver =
          Optional.ofNullable(rbelTcpIpMessageFacet.getReceiver())
              .map(RbelElement::getRawStringContent)
              .flatMap(RbelHostname::fromString)
              .orElse(null);

      log.atTrace().addArgument(msg::getUuid).log("Propagating message via mesh... (ID: {})");

      final TigerTracingDto tracingDto =
          TigerTracingDto.builder()
              .receiver(receiver)
              .sender(sender)
              .messageUuid(msg.getUuid())
              .additionalInformation(gatherAdditionalInformation(metadata))
              .sequenceNumber(rbelTcpIpMessageFacet.getSequenceNumber())
              .proxyTransmissionHistory(
                  new ProxyTransmissionHistory(
                      tigerProxy.getTigerProxyConfiguration().getName(),
                      List.of(rbelTcpIpMessageFacet.getSequenceNumber()),
                      msg.getFacet(ProxyTransmissionHistory.class).orElse(null)))
              .request(
                  msg.hasFacet(RbelRequestFacet.class) || !msg.hasFacet(RbelResponseFacet.class))
              .build();

      template.convertAndSend(TigerRemoteProxyClient.WS_TRACING, tracingDto);

      mapRbelMessageAndSent(msg);
      log.trace("completed sending message {}", msg.getUuid());
    } catch (RuntimeException e) {
      log.error("Error while sending message: {}", e.getMessage());
      throw e;
    }
  }

  private Map<String, Object> gatherAdditionalInformation(RbelMessageMetadata metadata) {
    val result = new HashMap<String, Object>();
    metadata.forEach(result::put);
    return result;
  }

  private void propagateException(Throwable exception) {
    template.convertAndSend(
        TigerRemoteProxyClient.WS_ERRORS,
        TigerExceptionDto.builder()
            .className(exception.getClass().getName())
            .message(exception.getMessage())
            .stacktrace(ExceptionUtils.getStackTrace(exception))
            .build());
  }

  private void mapRbelMessageAndSent(RbelElement rbelMessage) {
    if (rbelMessage == null) {
      return;
    }

    RbelContent content = rbelMessage.getContent();
    if (content.isNull()) {
      return;
    }

    final int size = content.size();
    final int chunkSize = content.getChunkSize();
    final int numberOfParts = (size + chunkSize - 1) / chunkSize;
    for (int i = 0, nextPartIndex = 0; nextPartIndex < size; i++) {
      byte[] partContent =
          content.toByteArray(nextPartIndex, Math.min(nextPartIndex + chunkSize, size));

      log.atTrace()
          .addArgument(i + 1)
          .addArgument(numberOfParts)
          .addArgument(rbelMessage.getUuid())
          .log("sending part {} of {} for UUID {}...");
      template.convertAndSend(
          TigerRemoteProxyClient.WS_DATA,
          TracingMessagePart.builder()
              .data(partContent)
              .index(i)
              .uuid(rbelMessage.getUuid())
              .numberOfMessages(numberOfParts)
              .build());
      nextPartIndex += partContent.length;
    }
  }

  private static Long getSequenceNumber(RbelElement msg) {
    return msg.getFacet(RbelTcpIpMessageFacet.class)
        .map(RbelTcpIpMessageFacet::getSequenceNumber)
        .orElse(-1L);
  }

  private void waitForPreviousMessageFullyProcessed(RbelElement msg) {
    tigerProxy.getRbelLogger().getRbelConverter().waitForAllElementsBeforeGivenToBeParsed(msg);
  }
}
