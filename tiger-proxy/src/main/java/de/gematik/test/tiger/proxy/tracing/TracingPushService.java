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

import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.facet.PreviousMessageFacet;
import de.gematik.rbellogger.data.facet.ProxyTransmissionHistory;
import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import de.gematik.rbellogger.data.facet.RbelMessageTimingFacet;
import de.gematik.rbellogger.data.facet.RbelRequestFacet;
import de.gematik.rbellogger.data.facet.RbelResponseFacet;
import de.gematik.rbellogger.data.facet.RbelRootFacet;
import de.gematik.rbellogger.data.facet.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.data.facet.TigerNonPairedMessageFacet;
import de.gematik.rbellogger.data.facet.TracingMessagePairFacet;
import de.gematik.rbellogger.data.facet.UnparsedChunkFacet;
import de.gematik.rbellogger.file.MessageTimeWriter;
import de.gematik.rbellogger.file.RbelFileWriter;
import de.gematik.rbellogger.file.TcpIpMessageFacetWriter;
import de.gematik.rbellogger.util.RbelContent;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.client.*;
import java.util.List;
import java.util.Map;
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
  private Logger log = LoggerFactory.getLogger(TracingPushService.class);

  public void addWebSocketListener() {
    tigerProxy.addRbelMessageListener(this::propagateRbelMessageSafe);
    tigerProxy.addNewExceptionConsumer(this::propagateExceptionSafe);
    tigerProxy
        .getRbelLogger()
        .getRbelConverter()
        .addLastPostConversionListener(this::markUnsuccessfulMessageAsProcessed);

    log =
        LoggerFactory.getLogger(
            TracingPushService.class.getName() + "(" + tigerProxy.proxyName() + ")");
  }

  private void markUnsuccessfulMessageAsProcessed(RbelElement result, RbelConverter rbelConverter) {
    if (result.getFacets().stream()
        .noneMatch(
            f ->
                f instanceof RbelRootFacet
                    || f instanceof RbelResponseFacet
                    || f instanceof RbelRequestFacet)) {
      RbelConverter.setMessageFullyProcessed(result);
    }
  }

  private void propagateExceptionSafe(Throwable exc) {
    try {
      propagateException(exc);
    } catch (RuntimeException e) {
      log.error("Error while propagating Exception", e);
      throw e;
    }
  }

  private void propagateRbelMessageSafe(RbelElement msg) {
    try {
      if (!msg.hasFacet(RbelTcpIpMessageFacet.class)) {
        log.info("Skipping propagation, not a TCP/IP message {}", msg.getUuid());
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
        .addArgument(msg::printHttpDescription)
        .log("Transmitting message #{}: {}");
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
              .proxyTransmissionHistoryRequest(
                  new ProxyTransmissionHistory(
                      tigerProxy.getTigerProxyConfiguration().getName(),
                      List.of(rbelTcpIpMessageFacet.getSequenceNumber()),
                      msg.getFacet(ProxyTransmissionHistory.class).orElse(null)))
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
            .proxyTransmissionHistoryRequest(
                new ProxyTransmissionHistory(
                    tigerProxy.getTigerProxyConfiguration().getName(),
                    List.of(requestTcpIpFacet.getSequenceNumber()),
                    request.getFacet(ProxyTransmissionHistory.class).orElse(null)))
            .proxyTransmissionHistoryResponse(
                new ProxyTransmissionHistory(
                    tigerProxy.getTigerProxyConfiguration().getName(),
                    List.of(responseTcpIpFacet.getSequenceNumber()),
                    response.getFacet(ProxyTransmissionHistory.class).orElse(null)))
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

    RbelContent content = rbelMessage.getContent();
    if (content.isNull()) {
      return;
    }

    final int size = content.getSize();
    final int chunkSize = content.getChunkSize();
    final int numberOfParts = (size + chunkSize - 1) / chunkSize;
    int i = 0;
    int nextPartIndex = 0;
    while (nextPartIndex < size) {
      byte[] partContent =
          content.subArray(nextPartIndex, Math.min(nextPartIndex + chunkSize, size));

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
      nextPartIndex += partContent.length;
      i++;
    }
  }

  private static Long getSequenceNumber(RbelElement msg) {
    return msg.getFacet(RbelTcpIpMessageFacet.class)
        .map(RbelTcpIpMessageFacet::getSequenceNumber)
        .orElse(-1L);
  }

  private void waitForMessageFullyProcessed(RbelElement msg) {
    log.atTrace().addArgument(() -> getSequenceNumber(msg)).log("Waiting for message #{}");
    RbelConverter.waitUntilFullyProcessed(msg);
  }

  private void waitForPreviousMessageFullyProcessed(RbelElement msg) {
    log.atTrace()
        .addArgument(() -> getSequenceNumber(msg))
        .log("Waiting for previous message of #{}");
    msg.getFacet(PreviousMessageFacet.class)
        .map(PreviousMessageFacet::getMessage)
        .ifPresent(this::waitForMessageFullyProcessed);
    msg.removeFacetsOfType(PreviousMessageFacet.class);
  }
}
