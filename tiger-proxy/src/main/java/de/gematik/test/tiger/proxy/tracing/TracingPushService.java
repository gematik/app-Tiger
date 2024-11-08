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

package de.gematik.test.tiger.proxy.tracing;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import de.gematik.rbellogger.data.facet.RbelMessageTimingFacet;
import de.gematik.rbellogger.data.facet.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.data.facet.TigerNonPairedMessageFacet;
import de.gematik.rbellogger.data.facet.TracingMessagePairFacet;
import de.gematik.rbellogger.data.facet.UnparsedChunkFacet;
import de.gematik.rbellogger.file.MessageTimeWriter;
import de.gematik.rbellogger.file.RbelFileWriter;
import de.gematik.rbellogger.file.TcpIpMessageFacetWriter;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.client.*;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@RequiredArgsConstructor
public class TracingPushService {

  public static final int MAX_MESSAGE_SIZE = 512 * 1024;
  private final SimpMessagingTemplate template;
  private final TigerProxy tigerProxy;
  private final ConcurrentHashMap<String, Long> nextSequenceNumberToBePushed =
      new ConcurrentHashMap<>();
  private final Map<String, Map<Long, Runnable>> pendingMessagesPerUrl = new ConcurrentHashMap<>();
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
      propagateException(exc);
    } catch (RuntimeException e) {
      log.error("Error while propagating Exception", e);
      throw e;
    }
  }

  private synchronized void propagateRbelMessageSafe(RbelElement msg) {
    try {
      if (!msg.hasFacet(RbelTcpIpMessageFacet.class)) {
        return;
      }

      final long sequenceNumber =
          msg.getFacetOrFail(RbelTcpIpMessageFacet.class).getSequenceNumber();
      if (log.isTraceEnabled()) {
        log.trace("Transmitting message #{}: {}", sequenceNumber, msg.printHttpDescription());
      }
      if (sequenceNumber < nextSequenceNumberFor(msg)) {
        throw new IllegalStateException(
            "Received message with sequence number lower than expected! (We are at "
                + nextSequenceNumberFor(msg)
                + ", received "
                + sequenceNumber
                + ")");
      }
      if (sequenceNumber == nextSequenceNumberFor(msg)) {
        propagateMessageAndUpdateSequenceCounter(msg, sequenceNumber);
      } else {
        if (log.isTraceEnabled()) {
          log.trace(
              "Received message with sequence number {}. Waiting... (we are at {})",
              sequenceNumber,
              nextSequenceNumberFor(msg));
        }
        addNewPendingMessage(msg, sequenceNumber);
      }
    } catch (RuntimeException e) {
      log.error("Error while propagating new Rbel-Message", e);
      throw e;
    }
  }

  private void addNewPendingMessage(RbelElement msg, long sequenceNumber) {
    final String remoteUrl = extractRemoteUrl(msg);
    pendingMessagesPerUrl.computeIfAbsent(remoteUrl, k -> new ConcurrentHashMap<>());

    pendingMessagesPerUrl
        .get(remoteUrl)
        .put(sequenceNumber, () -> propagateMessageAndUpdateSequenceCounter(msg, sequenceNumber));
  }

  private Long nextSequenceNumberFor(RbelElement msg) {
    return this.nextSequenceNumberToBePushed.getOrDefault(extractRemoteUrl(msg), 0L);
  }

  private void propagateMessageAndUpdateSequenceCounter(RbelElement msg, long sequenceNumber) {
    log.trace("Received message with sequence number {}. Pushing...", sequenceNumber);
    propagateRbelMessage(msg);
    this.nextSequenceNumberToBePushed.put(extractRemoteUrl(msg), sequenceNumber + 1);
    log.trace(
        "Pushed message with sequence number {}. Now treating waiting messages (sequence numbers"
            + " are {})",
        sequenceNumber,
        nextSequenceNumberToBePushed);
    queryAndRemovePendingMessageFuture(msg, sequenceNumber)
        .ifPresent(
            future -> {
              log.info("Completing future for sequence number {}", sequenceNumber);
              future.run();
            });
  }

  private static String extractRemoteUrl(RbelElement msg) {
    return msg.getFacet(RbelTcpIpMessageFacet.class)
        .map(RbelTcpIpMessageFacet::getReceivedFromRemoteWithUrl)
        .orElse("local");
  }

  /** Retrieves a potentially pending future for a message following the given message. */
  private Optional<Runnable> queryAndRemovePendingMessageFuture(
      RbelElement msg, long sequenceNumber) {
    final String remoteUrl = extractRemoteUrl(msg);

    return Optional.ofNullable(pendingMessagesPerUrl.get(remoteUrl))
        .map(map -> map.remove(sequenceNumber + 1));
  }

  private void propagateRbelMessage(RbelElement msg) {
    if (!msg.hasFacet(RbelTcpIpMessageFacet.class)) {
      log.trace("Skipping propagation, not a TCP/IP message {}", msg.getUuid());
      return;
    }
    if (msg.hasFacet(TigerNonPairedMessageFacet.class)) {
      sendNonPairedMessage(msg);
    } else if (msg.hasFacet(RbelHttpResponseFacet.class)
        || msg.getFacet(TracingMessagePairFacet.class)
            .map(facet -> facet.isResponse(msg))
            .orElse(false)) {
      sendPairedMessage(msg);
    } else {
      if (log.isTraceEnabled()) {
        log.trace(
            "Skipping propagation, not a response (facets: {}, uuid: {})",
            msg.getFacets().stream()
                .map(Object::getClass)
                .map(Class::getSimpleName)
                .collect(Collectors.joining(", ")),
            msg.getUuid());
      }
    }
  }

  private void sendNonPairedMessage(RbelElement msg) {
    try {
      RbelTcpIpMessageFacet rbelTcpIpMessageFacet = msg.getFacetOrFail(RbelTcpIpMessageFacet.class);
      final RbelHostname sender =
          RbelHostname.fromString(rbelTcpIpMessageFacet.getSender().getRawStringContent())
              .orElse(null);
      final RbelHostname receiver =
          RbelHostname.fromString(rbelTcpIpMessageFacet.getReceiver().getRawStringContent())
              .orElse(null);

      log.trace("Propagating new non-paired message (ID: {})", msg.getUuid());

      template.convertAndSend(
          TigerRemoteProxyClient.WS_TRACING,
          TigerTracingDto.builder()
              .receiver(receiver)
              .sender(sender)
              .requestUuid(msg.getUuid())
              .requestTransmissionTime(
                  msg.getFacet(RbelMessageTimingFacet.class)
                      .map(RbelMessageTimingFacet::getTransmissionTime)
                      .orElse(null))
              .additionalInformationRequest(gatherAdditionalInformation(msg))
              .unparsedChunk(msg.hasFacet(UnparsedChunkFacet.class))
              .sequenceNumberRequest(rbelTcpIpMessageFacet.getSequenceNumber())
              .build());

      mapRbelMessageAndSent(msg);
    } catch (RuntimeException e) {
      log.error("Error while sending non-paired message: {}", e.getMessage());
      throw e;
    }
  }

  private void sendPairedMessage(RbelElement response) {
    final RbelElement request =
        response
            .getFacet(TracingMessagePairFacet.class)
            .map(TracingMessagePairFacet::getRequest)
            .or(
                () ->
                    response
                        .getFacet(RbelHttpResponseFacet.class)
                        .map(RbelHttpResponseFacet::getRequest))
            .orElseThrow(
                () ->
                    new TigerRemoteProxyClientException(
                        "Failure to correctly push message with id '"
                            + response.getUuid()
                            + "': Unable to find matching request"));

    RbelTcpIpMessageFacet requestTcpIpFacet = request.getFacetOrFail(RbelTcpIpMessageFacet.class);
    RbelTcpIpMessageFacet responseTcpIpFacet = response.getFacetOrFail(RbelTcpIpMessageFacet.class);
    final RbelHostname sender =
        RbelHostname.fromString(requestTcpIpFacet.getSender().getRawStringContent()).orElse(null);
    final RbelHostname receiver =
        RbelHostname.fromString(requestTcpIpFacet.getReceiver().getRawStringContent()).orElse(null);
    log.trace(
        "Propagating new request/response pair (IDs: {} and {})",
        request.getUuid(),
        response.getUuid());
    template.convertAndSend(
        TigerRemoteProxyClient.WS_TRACING,
        TigerTracingDto.builder()
            .receiver(receiver)
            .sender(sender)
            .responseUuid(response.getUuid())
            .requestUuid(request.getUuid())
            .responseTransmissionTime(
                response
                    .getFacet(RbelMessageTimingFacet.class)
                    .map(RbelMessageTimingFacet::getTransmissionTime)
                    .orElse(null))
            .requestTransmissionTime(
                request
                    .getFacet(RbelMessageTimingFacet.class)
                    .map(RbelMessageTimingFacet::getTransmissionTime)
                    .orElse(null))
            .additionalInformationRequest(gatherAdditionalInformation(request))
            .additionalInformationResponse(gatherAdditionalInformation(response))
            .sequenceNumberRequest(requestTcpIpFacet.getSequenceNumber())
            .sequenceNumberResponse(responseTcpIpFacet.getSequenceNumber())
            .build());

    mapRbelMessageAndSent(request);
    mapRbelMessageAndSent(response);
  }

  private Map<String, String> gatherAdditionalInformation(RbelElement msg) {
    // create new blank jackson object
    var infoObject = new JSONObject();
    RbelFileWriter.DEFAULT_PRE_SAVE_LISTENER.stream()
        .filter(
            listener ->
                !(listener instanceof TcpIpMessageFacetWriter)
                    && !(listener instanceof MessageTimeWriter))
        .forEach(listener -> listener.preSaveCallback(msg, infoObject));
    return infoObject.toMap().entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().toString()));
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

    var content = rbelMessage.getContent();
    final int numberOfParts = content.size() / MAX_MESSAGE_SIZE + 1;
    for (int i = 0; i < numberOfParts; i++) {
      byte[] partContent =
          content.subArray(
              i * MAX_MESSAGE_SIZE, Math.min((i + 1) * MAX_MESSAGE_SIZE, content.size()));

      log.trace(
          "sending part {} of {} for UUID {}...", i + 1, numberOfParts, rbelMessage.getUuid());
      template.convertAndSend(
          TigerRemoteProxyClient.WS_DATA,
          TracingMessagePart.builder()
              .data(partContent)
              .index(i)
              .uuid(rbelMessage.getUuid())
              .numberOfMessages(numberOfParts)
              .build());
    }
  }
}
