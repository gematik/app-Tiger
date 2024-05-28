/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.client;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.TracingMessagePairFacet;
import de.gematik.rbellogger.file.RbelFileWriter;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.json.JSONObject;

@Data
@Slf4j
public class TracingMessagePair implements TracingMessageFrame {

  private PartialTracingMessage request;
  private PartialTracingMessage response;
  @ToString.Exclude private final TigerRemoteProxyClient remoteProxyClient;

  @Override
  public void checkForCompletePairAndPropagateIfComplete() {
    if (request != null && response != null && request.isComplete() && response.isComplete()) {
      remoteProxyClient.submitNewMessageTask(this::parseAndPropagate);
    }
  }

  private void parseAndPropagate() {
    if (remoteProxyClient.messageUuidKnown(request.getTracingDto().getRequestUuid())
        || remoteProxyClient.messageUuidKnown(request.getTracingDto().getResponseUuid())) {
      log.trace(
          "{}Skipping parsing of pair with UUIDs ({} and {}) (received from PUSH): UUID already"
              + " known",
          remoteProxyClient.proxyName(),
          request.getTracingDto().getRequestUuid(),
          request.getTracingDto().getResponseUuid());
      return;
    }
    val requestParsed =
        remoteProxyClient.buildNewRbelMessage(
            request.getSender(),
            request.getReceiver(),
            request.buildCompleteContent(),
            Optional.ofNullable(request.getTransmissionTime()),
            request.getTracingDto().getRequestUuid());
    val responseParsed =
        remoteProxyClient.buildNewRbelResponse(
            response.getSender(),
            response.getReceiver(),
            response.buildCompleteContent(),
            requestParsed.orElse(null),
            Optional.ofNullable(response.getTransmissionTime()),
            response.getTracingDto().getResponseUuid());
    if (requestParsed.isEmpty() || responseParsed.isEmpty()) {
      return;
    }

    RbelFileWriter.DEFAULT_POST_CONVERSION_LISTENER.forEach(
        listener -> {
          listener.performMessagePostConversionProcessing(
              requestParsed.get(),
              remoteProxyClient.getRbelLogger().getRbelConverter(),
              new JSONObject(this.request.getAdditionalInformation()));
          listener.performMessagePostConversionProcessing(
              responseParsed.get(),
              remoteProxyClient.getRbelLogger().getRbelConverter(),
              new JSONObject(this.response.getAdditionalInformation()));
        });

    val pairFacet =
        TracingMessagePairFacet.builder()
            .response(responseParsed.get())
            .request(requestParsed.get())
            .build();
    responseParsed.get().addFacet(pairFacet);

    if (log.isTraceEnabled()) {
      log.trace(
          "{}Received pair to {} (UUIDs {} and {})",
          remoteProxyClient.proxyName(),
          requestParsed
              .map(RbelElement::getRawStringContent)
              .map(s -> Stream.of(s.split(" ")).skip(1).limit(1).collect(Collectors.joining(",")))
              .orElse("<>"),
          requestParsed.get().getUuid(),
          responseParsed.get().getUuid());
    }
    remoteProxyClient.getLastMessageUuid().set(responseParsed.get().getUuid());

    if (remoteProxyClient.messageMatchesFilterCriterion(requestParsed.get())
        || remoteProxyClient.messageMatchesFilterCriterion(responseParsed.get())) {
      remoteProxyClient.propagateMessage(requestParsed.get());
      remoteProxyClient.propagateMessage(responseParsed.get());
    } else {
      remoteProxyClient.removeMessage(requestParsed.get());
      remoteProxyClient.removeMessage(responseParsed.get());
    }
  }
}
