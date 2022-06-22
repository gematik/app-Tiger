/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.client;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.test.tiger.proxy.data.TracingMessagePairFacet;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Data;
import lombok.ToString;
import lombok.ToString.Exclude;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@Data
@Slf4j
public class TracingMessagePair {

    private PartialTracingMessage request;
    private PartialTracingMessage response;
    @ToString.Exclude
    private final TigerRemoteProxyClient remoteProxyClient;

    public void checkForCompletePairAndPropagateIfComplete() {
        if (request != null && response != null
            && request.isComplete() && response.isComplete()) {
            remoteProxyClient.submitNewMessageTask(this::parseAndPropagate);
        }
    }

    private void parseAndPropagate() {
        if (remoteProxyClient.messageUuidKnown(request.getTracingDto().getRequestUuid())
            || remoteProxyClient.messageUuidKnown(request.getTracingDto().getResponseUuid())) {
            log.trace("{}Skipping parsing of pair with UUIDs ({} and {}) (received from PUSH): UUID already known",
                remoteProxyClient.proxyName(),
                request.getTracingDto().getRequestUuid(),
                request.getTracingDto().getResponseUuid());
            return;
        }
        val requestParsed = remoteProxyClient.buildNewRbelMessage(request.getSender(), request.getReceiver(),
            request.buildCompleteContent(), Optional.ofNullable(request.getTransmissionTime()),
            request.getTracingDto().getRequestUuid());
        val responseParsed = remoteProxyClient.buildNewRbelMessage(response.getSender(), response.getReceiver(),
            response.buildCompleteContent(), Optional.ofNullable(response.getTransmissionTime()),
            response.getTracingDto().getResponseUuid());
        if (requestParsed.isEmpty() || responseParsed.isEmpty()) {
            return;
        }

        val pairFacet = TracingMessagePairFacet.builder()
            .response(responseParsed.get())
            .request(requestParsed.get())
            .build();
        responseParsed.get().addFacet(pairFacet);
        if (log.isTraceEnabled()) {
            log.trace("{}Received pair to {} (UUIDs {} and {})", remoteProxyClient.proxyName(),
                requestParsed.map(RbelElement::getRawStringContent)
                    .map(s -> Stream.of(s.split(" "))
                        .skip(1).limit(1)
                        .collect(Collectors.joining(",")))
                    .orElse("<>"), requestParsed.get().getUuid(), responseParsed.get().getUuid());
        }
        remoteProxyClient.getLastMessageUuid().set(responseParsed.get().getUuid());

        remoteProxyClient.propagateMessage(requestParsed.get());
        remoteProxyClient.propagateMessage(responseParsed.get());
    }
}
