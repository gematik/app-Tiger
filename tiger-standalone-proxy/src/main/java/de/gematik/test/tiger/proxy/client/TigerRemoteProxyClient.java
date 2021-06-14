package de.gematik.test.tiger.proxy.client;

import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.RbelMessage;
import de.gematik.test.tiger.proxy.AbstractTigerProxy;
import de.gematik.test.tiger.proxy.configuration.TigerProxyConfiguration;
import de.gematik.test.tiger.proxy.data.TigerRoute;
import de.gematik.test.tiger.proxy.tracing.TigerTracingDto;
import de.gematik.test.tiger.proxy.tracing.TracingEndpointConfiguration;
import de.gematik.test.tiger.proxy.tracing.TracingMessage;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@Slf4j
public class TigerRemoteProxyClient extends AbstractTigerProxy {

    private final String remoteProxyUrl;
    private final WebSocketStompClient tigerProxyStompClient;

    public TigerRemoteProxyClient(String remoteProxyUrl, TigerProxyConfiguration configuration) {
        super(configuration);
        this.remoteProxyUrl = remoteProxyUrl;

        WebSocketClient tigerProxyWebSocketClient = new StandardWebSocketClient();
        tigerProxyStompClient = new WebSocketStompClient(tigerProxyWebSocketClient);
        tigerProxyStompClient.setMessageConverter(new MappingJackson2MessageConverter());
        final TigerStompSessionHandler tigerStompSessionHandler = new TigerStompSessionHandler(remoteProxyUrl);
        tigerProxyStompClient.connect(remoteProxyUrl.replaceFirst("http", "ws")
            + TracingEndpointConfiguration.TRACING_ENDPOINT, tigerStompSessionHandler);
    }

    @Override
    public TigerRoute addRoute(String sourceHost, String targetHost) {
        return null;
    }

    @Override
    public void removeRoute(String sourceHost) {

    }

    @Override
    public String getBaseUrl() {
        return remoteProxyUrl;
    }

    @Override
    public int getPort() {
        return 0;
    }

    @Override
    public List<TigerRoute> getRoutes() {
        return null;
    }

    private void propagateNewRbelMessage(RbelHostname sender, RbelHostname receiver, TracingMessage tracingMessage) {
        byte[] messageBytes = ArrayUtils.addAll((
                tracingMessage.getHeader().replace("\n", "\r\n")
                    .stripTrailing() + "\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII),
            tracingMessage.getBody());

        log.info("Propagating new message {}", new String(messageBytes));

        final RbelMessage rbelMessage = getRbelLogger().getRbelConverter().parseMessage(messageBytes, sender, receiver);

        super.triggerListener(rbelMessage);
    }

    @RequiredArgsConstructor
    private class TigerStompSessionHandler extends StompSessionHandlerAdapter {

        private final String remoteProxyUrl;

        @Override
        public void afterConnected(StompSession stompSession, StompHeaders stompHeaders) {
            log.info("Connecting to tracing point {}", remoteProxyUrl);

            stompSession.subscribe("/topic/traces", new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders stompHeaders) {
                        return TigerTracingDto.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders stompHeaders, Object frameContent) {
                        if (frameContent instanceof TigerTracingDto) {
                            final TigerTracingDto tigerTracingDto = (TigerTracingDto) frameContent;
                            propagateNewRbelMessage(tigerTracingDto.getSender(), tigerTracingDto.getReceiver(),
                                tigerTracingDto.getRequest());
                            propagateNewRbelMessage(tigerTracingDto.getReceiver(), tigerTracingDto.getSender(),
                                tigerTracingDto.getResponse());
                        }
                    }
                }
            );
        }

        @Override
        public void handleException(StompSession stompSession, StompCommand stompCommand, StompHeaders stompHeaders,
            byte[] bytes, Throwable throwable) {
            log.error("handle exception TigerRemoteProxy: {}, {}", new String(bytes), throwable);
        }
    }
}
