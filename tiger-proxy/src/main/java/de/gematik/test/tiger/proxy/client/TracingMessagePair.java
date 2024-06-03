/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.client;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.data.facet.TracingMessagePairFacet;
import de.gematik.rbellogger.file.RbelFileWriter;
import java.util.Optional;
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
      parseAndPropagate();
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
    final var requestParsed =
        remoteProxyClient.buildNewRbelMessage(
            request.getSender(),
            request.getReceiver(),
            request.buildCompleteContent(),
            Optional.ofNullable(request.getTransmissionTime()),
            request.getTracingDto().getRequestUuid());
    final var responseParsed =
        remoteProxyClient.buildNewRbelResponse(
            response.getSender(),
            response.getReceiver(),
            response.buildCompleteContent(),
            requestParsed,
            Optional.ofNullable(response.getTransmissionTime()),
            response.getTracingDto().getResponseUuid());
    if (requestParsed.isEmpty() || responseParsed.isEmpty()) {
      return;
    }

    requestParsed
        .get()
        .thenCombineAsync(
            responseParsed.get(),
            (req, res) -> {
              try {
                performPostConversion(req, res);
                return null;
              } catch (RuntimeException e){
                log.info(
                  "{} - Error while processing pair with UUIDs {} and {}",
                  remoteProxyClient.proxyName(),
                  request.getTracingDto().getRequestUuid(),
                  request.getTracingDto().getResponseUuid(),
                  e);
                throw e;
              }
            })
      .exceptionally(
          e -> {
            log.error(
                "{} - Error while processing pair with UUIDs {} and {}",
                remoteProxyClient.proxyName(),
                request.getTracingDto().getRequestUuid(),
                request.getTracingDto().getResponseUuid(),
                e);
            return null;
          });
  }

  private void performPostConversion(RbelElement req, RbelElement res) {
    if (request.getTracingDto().getSequenceNumberRequest() != null
        && response.getTracingDto().getSequenceNumberResponse() != null) {
      req.addOrReplaceFacet(
          req.getFacetOrFail(RbelTcpIpMessageFacet.class).toBuilder()
              .sequenceNumber(request.getTracingDto().getSequenceNumberRequest())
              .receivedFromRemoteWithUrl(remoteProxyClient.getRemoteProxyUrl())
              .build());
      res.addOrReplaceFacet(
          res.getFacetOrFail(RbelTcpIpMessageFacet.class).toBuilder()
              .sequenceNumber(request.getTracingDto().getSequenceNumberResponse())
              .receivedFromRemoteWithUrl(remoteProxyClient.getRemoteProxyUrl())
              .build());
    }
    tigerPostConversionListener(req, res);
    val pairFacet = TracingMessagePairFacet.builder().response(res).request(req).build();
    res.addFacet(pairFacet);
    if (log.isTraceEnabled()) {
      log.trace(
          "{}Received pair to {} (UUIDs {} and {})",
          remoteProxyClient.proxyName(),
          req.printHttpDescription(),
          req.getUuid(),
          res.getUuid());
    }

    remoteProxyClient.getLastMessageUuid().set(res.getUuid());

    if (remoteProxyClient.messageMatchesFilterCriterion(req)
        || remoteProxyClient.messageMatchesFilterCriterion(res)) {
      remoteProxyClient.propagateMessage(req);
      remoteProxyClient.propagateMessage(res);
    } else {
      remoteProxyClient.removeMessage(req);
      remoteProxyClient.removeMessage(res);
    }
  }

  private void tigerPostConversionListener(RbelElement req, RbelElement res) {
    RbelFileWriter.DEFAULT_POST_CONVERSION_LISTENER.forEach(
        listener -> {
          listener.performMessagePostConversionProcessing(
              req,
              remoteProxyClient.getRbelLogger().getRbelConverter(),
              new JSONObject(this.request.getAdditionalInformation()));
          listener.performMessagePostConversionProcessing(
              res,
              remoteProxyClient.getRbelLogger().getRbelConverter(),
              new JSONObject(this.response.getAdditionalInformation()));
        });
  }
}
