package de.gematik.test.tiger.proxy.client;

import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.RbelMessage;
import de.gematik.test.tiger.proxy.AbstractTigerProxy;
import de.gematik.test.tiger.proxy.configuration.TigerProxyConfiguration;
import de.gematik.test.tiger.proxy.data.TigerRoute;
import kong.unirest.GenericType;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.util.Assert;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
public class TigerRemoteProxyClient extends AbstractTigerProxy {

    private final String remoteProxyUrl;
    private final WebSocketStompClient tigerProxyStompClient;
    private Consumer<HttpResponse<List<TigerRoute>>> remoteProxyErrorConsumer;

    public TigerRemoteProxyClient(String remoteProxyUrl, TigerProxyConfiguration configuration) {
        super(configuration);
        this.remoteProxyUrl = remoteProxyUrl;

        WebSocketClient tigerProxyWebSocketClient = new StandardWebSocketClient();
        tigerProxyStompClient = new WebSocketStompClient(tigerProxyWebSocketClient);
        tigerProxyStompClient.setMessageConverter(new MappingJackson2MessageConverter());
        final TigerStompSessionHandler tigerStompSessionHandler = new TigerStompSessionHandler(remoteProxyUrl);
        tigerProxyStompClient.connect(
                remoteProxyUrl.replaceFirst("http", "ws") + "/tracing",
                tigerStompSessionHandler);
    }

    @Override
    public TigerRoute addRoute(TigerRoute tigerRoute) {
        return Unirest.put(remoteProxyUrl + "/route")
                .body(tigerRoute)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .asObject(TigerRoute.class)
                .ifFailure(response -> {
                    throw new TigerRemoteProxyClientException(
                            "Unable to add route. Got " + response.getStatus() +
                                    ": " + response.mapError(String.class)
                    );
                })
                .getBody();
    }

    @Override
    public void removeRoute(String routeId) {
        Assert.hasText(routeId, () -> "No route ID given!");
        Unirest.delete(remoteProxyUrl + "/route/" + routeId)
                .asEmpty()
                .ifFailure(httpResponse -> {
                    throw new TigerRemoteProxyClientException(
                            "Unable to add route. Got " + httpResponse);
                });
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
        return Unirest.get(remoteProxyUrl + "/route")
                .asObject(new GenericType<List<TigerRoute>>() {
                })
                .ifFailure(response -> {
                    throw new TigerRemoteProxyClientException(
                            "Unable to get routes. Got " + response.getStatus() +
                                    ": " + response.mapError(String.class)
                    );
                })
                .getBody();
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
