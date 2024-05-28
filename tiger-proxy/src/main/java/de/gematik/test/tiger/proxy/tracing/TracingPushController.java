/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.tracing;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import de.gematik.rbellogger.data.facet.RbelMessageTimingFacet;
import de.gematik.rbellogger.data.facet.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.data.facet.TigerNonPairedMessageFacet;
import de.gematik.rbellogger.data.facet.TracingMessagePairFacet;
import de.gematik.rbellogger.file.MessageTimeWriter;
import de.gematik.rbellogger.file.RbelFileWriter;
import de.gematik.rbellogger.file.TcpIpMessageFacetWriter;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.client.*;
import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.JSONObject;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TracingPushController {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
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
        || (msg.getFacet(TracingMessagePairFacet.class)
            .map(facet -> facet.isResponse(msg))
            .orElse(false))) {
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
              .additionalInformationRequest(gatherAdditionalInformation(msg))
              .build());

      mapRbelMessageAndSent(msg);
    } catch (RuntimeException e) {
      log.error(e.getMessage());
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

    RbelTcpIpMessageFacet rbelTcpIpMessageFacet =
        request.getFacetOrFail(RbelTcpIpMessageFacet.class);
    final RbelHostname sender =
        RbelHostname.fromString(rbelTcpIpMessageFacet.getSender().getRawStringContent())
            .orElse(null);
    final RbelHostname receiver =
        RbelHostname.fromString(rbelTcpIpMessageFacet.getReceiver().getRawStringContent())
            .orElse(null);
    log.trace(
        "{}Propagating new request/response pair (IDs: {} and {})",
        tigerProxy.proxyName(),
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

    final int numberOfParts = rbelMessage.getRawContent().length / MAX_MESSAGE_SIZE + 1;
    for (int i = 0; i < numberOfParts; i++) {
      byte[] partContent =
          Arrays.copyOfRange(
              rbelMessage.getRawContent(),
              i * MAX_MESSAGE_SIZE,
              Math.min((i + 1) * MAX_MESSAGE_SIZE, rbelMessage.getRawContent().length));

      log.trace(
          "{} sending part {} of {} for UUID {}...",
          tigerProxy.proxyName(),
          i + 1,
          numberOfParts,
          rbelMessage.getUuid());
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
