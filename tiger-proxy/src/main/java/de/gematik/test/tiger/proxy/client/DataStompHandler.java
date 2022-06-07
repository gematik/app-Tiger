/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.client;

import java.lang.reflect.Type;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;

@RequiredArgsConstructor
@Slf4j
class DataStompHandler implements StompFrameHandler {

    private final TigerRemoteProxyClient remoteProxyClient;

    @Override
    public Type getPayloadType(StompHeaders stompHeaders) {
        return TracingMessagePart.class;
    }

    @Override
    public void handleFrame(StompHeaders stompHeaders, Object frameContent) {
        if (frameContent instanceof TracingMessagePart) {
            final TracingMessagePart tracingMessagePart = (TracingMessagePart) frameContent;
            log.trace("Received part {} of {} for UUID {}",
                tracingMessagePart.getIndex(), tracingMessagePart.getNumberOfMessages(),
                tracingMessagePart.getUuid());
            remoteProxyClient.receiveNewMessagePart(tracingMessagePart);
        }
    }
}
