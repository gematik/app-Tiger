/*
 * Copyright (c) 2023 gematik GmbH
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

import java.lang.reflect.Type;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;

@RequiredArgsConstructor
class TracingStompHandler implements StompFrameHandler {

    private final TigerRemoteProxyClient remoteProxyClient;

    @Override
    public Type getPayloadType(StompHeaders stompHeaders) {
        return TigerTracingDto.class;
    }

    @Override
    public void handleFrame(StompHeaders stompHeaders, Object frameContent) {
        if (frameContent instanceof TigerTracingDto) {
            final TigerTracingDto tigerTracingDto = (TigerTracingDto) frameContent;
            if (StringUtils.isEmpty(tigerTracingDto.getResponseUuid())) {
                registerNewIsolaniMessage(tigerTracingDto);
            } else {
                registerNewMessagePair(tigerTracingDto);
            }
        }
    }

    private void registerNewIsolaniMessage(TigerTracingDto tigerTracingDto) {
        var isolani = new TracingMessageIsolani(remoteProxyClient);
        isolani.setMessage(PartialTracingMessage.builder()
            .tracingDto(tigerTracingDto)
            .receiver(tigerTracingDto.getSender())
            .sender(tigerTracingDto.getReceiver())
            .messageFrame(isolani)
            .transmissionTime(tigerTracingDto.getRequestTransmissionTime())
            .build());
        remoteProxyClient.initOrUpdateMessagePart(tigerTracingDto.getRequestUuid(), isolani.getMessage());
    }

    private void registerNewMessagePair(TigerTracingDto tigerTracingDto) {
        TracingMessagePair messagePair = new TracingMessagePair(remoteProxyClient);
        messagePair.setRequest(PartialTracingMessage.builder()
            .tracingDto(tigerTracingDto)
            .receiver(tigerTracingDto.getSender())
            .sender(tigerTracingDto.getReceiver())
            .messageFrame(messagePair)
            .transmissionTime(tigerTracingDto.getRequestTransmissionTime())
            .build());
        messagePair.setResponse(
            PartialTracingMessage.builder()
                .tracingDto(tigerTracingDto)
                .receiver(tigerTracingDto.getReceiver())
                .sender(tigerTracingDto.getSender())
                .messageFrame(messagePair)
                .transmissionTime(tigerTracingDto.getResponseTransmissionTime())
                .build());
        remoteProxyClient.initOrUpdateMessagePart(tigerTracingDto.getRequestUuid(), messagePair.getRequest());
        remoteProxyClient.initOrUpdateMessagePart(tigerTracingDto.getResponseUuid(), messagePair.getResponse());
    }
}
