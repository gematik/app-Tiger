/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.client;

import de.gematik.test.tiger.proxy.data.TracingMessagePairFacet;
import java.util.Optional;
import lombok.Data;
import lombok.val;

@Data
public class TracingMessagePair {

    private PartialTracingMessage request;
    private PartialTracingMessage response;
    private final TigerRemoteProxyClient remoteProxyClient;

    public void checkForCompletePairAndPropagateIfComplete() {
        if (request != null && response != null
            && request.isComplete() && response.isComplete()) {
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

            remoteProxyClient.propagateMessage(requestParsed.get());
            remoteProxyClient.propagateMessage(responseParsed.get());
        }
    }
}
