/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.client;

import java.lang.reflect.Type;
import lombok.RequiredArgsConstructor;
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
            TracingMessagePair messagePair = new TracingMessagePair(remoteProxyClient);
            messagePair.setRequest(PartialTracingMessage.builder()
                .tracingDto(tigerTracingDto)
                .receiver(tigerTracingDto.getSender())
                .sender(tigerTracingDto.getReceiver())
                .messagePair(messagePair)
                .transmissionTime(tigerTracingDto.getRequestTransmissionTime())
                .build());
            messagePair.setResponse(
                PartialTracingMessage.builder()
                    .tracingDto(tigerTracingDto)
                    .receiver(tigerTracingDto.getReceiver())
                    .sender(tigerTracingDto.getSender())
                    .messagePair(messagePair)
                    .transmissionTime(tigerTracingDto.getResponseTransmissionTime())
                    .build());
            remoteProxyClient.initOrUpdateMessagePart(tigerTracingDto.getRequestUuid(), messagePair.getRequest());
            remoteProxyClient.initOrUpdateMessagePart(tigerTracingDto.getResponseUuid(), messagePair.getResponse());
        }
    }
}
