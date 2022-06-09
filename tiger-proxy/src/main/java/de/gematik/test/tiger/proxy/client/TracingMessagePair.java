/*
 * Copyright (c) 2022 gematik GmbH
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

package de.gematik.test.tiger.proxy.client;

import de.gematik.test.tiger.proxy.data.TracingMessagePairFacet;
import java.util.Optional;
import lombok.Data;
import lombok.ToString;
import lombok.ToString.Exclude;
import lombok.val;

@Data
public class TracingMessagePair {

    private PartialTracingMessage request;
    private PartialTracingMessage response;
    @ToString.Exclude
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
