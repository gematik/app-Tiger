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

package de.gematik.test.tiger.proxy.tracing;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import de.gematik.rbellogger.data.facet.RbelMessageTimingFacet;
import de.gematik.rbellogger.data.facet.RbelTcpIpMessageFacet;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.client.*;
import de.gematik.test.tiger.proxy.data.TigerNonPairedMessageFacet;
import de.gematik.test.tiger.proxy.data.TracingMessagePairFacet;
import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TracingPushController {

  public static final int MAX_MESSAGE_SIZE = 512 * 1024;
  public final SimpMessagingTemplate template;
  public final TigerProxy tigerProxy;

  @PostConstruct
  public void addWebSocketListener() {
    tigerProxy.addRbelMessageListener(this::propagateRbelMessageSafe);
    tigerProxy.addNewExceptionConsumer(this::propagateExceptionSafe);
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
      propagateRbelMessage(msg);
    } catch (RuntimeException e) {
      log.error("Error while propagating new Rbel-Message", e);
      throw e;
    }
  }

  private void propagateRbelMessage(RbelElement msg) {
    if (!msg.hasFacet(RbelTcpIpMessageFacet.class)) {
      log.trace("Skipping propagation, not a TCP/IP message {}", msg.getUuid());
      return;
    }
    if (msg.hasFacet(TigerNonPairedMessageFacet.class)) {
      sendNonPairedMessage(msg);
    } else if (msg.hasFacet(RbelHttpResponseFacet.class)
        || msg.hasFacet(TracingMessagePairFacet.class)) {
      sendPairedMessage(msg);
    } else {
      log.trace(
          "Skipping propagation, not a response (facets: {}, uuid: {})",
          msg.getFacets().stream()
              .map(Object::getClass)
              .map(Class::getSimpleName)
              .collect(Collectors.joining(", ")),
          msg.getUuid());
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

      log.trace(
          "{}Propagating new non-paired message (ID: {})", tigerProxy.proxyName(), msg.getUuid());

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
              .build());

      mapRbelMessageAndSent(msg);
    } catch (RuntimeException e) {
      log.error(e.getMessage());
      throw e;
    }
  }

  private void sendPairedMessage(RbelElement msg) {
    RbelTcpIpMessageFacet rbelTcpIpMessageFacet = msg.getFacetOrFail(RbelTcpIpMessageFacet.class);
    final RbelHostname sender =
        RbelHostname.fromString(rbelTcpIpMessageFacet.getSender().getRawStringContent())
            .orElse(null);
    final RbelHostname receiver =
        RbelHostname.fromString(rbelTcpIpMessageFacet.getReceiver().getRawStringContent())
            .orElse(null);
    final RbelElement request =
        msg.getFacet(TracingMessagePairFacet.class)
            .map(TracingMessagePairFacet::getRequest)
            .or(
                () ->
                    msg.getFacet(RbelHttpResponseFacet.class)
                        .map(RbelHttpResponseFacet::getRequest))
            .orElseThrow(
                () ->
                    new TigerRemoteProxyClientException(
                        "Failure to correctly push message with id '"
                            + msg.getUuid()
                            + "': Unable to find matching request"));

    log.trace(
        "{}Propagating new request/response pair (IDs: {} and {})",
        tigerProxy.proxyName(),
        request.getUuid(),
        msg.getUuid());
    template.convertAndSend(
        TigerRemoteProxyClient.WS_TRACING,
        TigerTracingDto.builder()
            .receiver(receiver)
            .sender(sender)
            .responseUuid(msg.getUuid())
            .requestUuid(request.getUuid())
            .responseTransmissionTime(
                msg.getFacet(RbelMessageTimingFacet.class)
                    .map(RbelMessageTimingFacet::getTransmissionTime)
                    .orElse(null))
            .requestTransmissionTime(
                request
                    .getFacet(RbelMessageTimingFacet.class)
                    .map(RbelMessageTimingFacet::getTransmissionTime)
                    .orElse(null))
            .build());

    mapRbelMessageAndSent(msg);
    mapRbelMessageAndSent(request);
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

    final int numberOfParts = rbelMessage.getRawContent().length / MAX_MESSAGE_SIZE + 1;
    for (int i = 0; i < numberOfParts; i++) {
      byte[] partContent =
          Arrays.copyOfRange(
              rbelMessage.getRawContent(),
              i * MAX_MESSAGE_SIZE,
              Math.min((i + 1) * MAX_MESSAGE_SIZE, rbelMessage.getRawContent().length));

      log.trace(
          "Sending part {} of {} for UUID {}...", i + 1, numberOfParts, rbelMessage.getUuid());
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
