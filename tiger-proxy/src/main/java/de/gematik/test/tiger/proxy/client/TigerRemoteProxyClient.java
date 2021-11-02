/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.client;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.test.tiger.common.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.proxy.AbstractTigerProxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.websocket.ContainerProvider;
import javax.websocket.WebSocketContainer;
import kong.unirest.GenericType;
import kong.unirest.Unirest;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNullApi;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.util.Assert;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

@Slf4j
public class TigerRemoteProxyClient extends AbstractTigerProxy {

    public static final String WS_TRACING = "/topic/traces";
    public static final String WS_ERRORS = "/topic/errors";
    private final String remoteProxyUrl;
    private final WebSocketStompClient tigerProxyStompClient;
    @Getter
    private final List<TigerExceptionDto> receivedRemoteExceptions = new ArrayList<>();

    public TigerRemoteProxyClient(String remoteProxyUrl, TigerProxyConfiguration configuration) {
        super(configuration);
        final String tracingWebSocketUrl = remoteProxyUrl.replaceFirst("http", "ws") + "/tracing";
        this.remoteProxyUrl = remoteProxyUrl;

        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        container.setDefaultMaxBinaryMessageBufferSize(1024 * 1024 * configuration.getPerMessageBufferSizeInMb());
        container.setDefaultMaxTextMessageBufferSize(1024 * 1024 * configuration.getPerMessageBufferSizeInMb());
        WebSocketClient webSocketClient = new SockJsClient(
            List.of(new WebSocketTransport(new StandardWebSocketClient(container))));

        tigerProxyStompClient = new WebSocketStompClient(webSocketClient);
        tigerProxyStompClient.setMessageConverter(new MappingJackson2MessageConverter());
        tigerProxyStompClient.setInboundMessageSizeLimit(1024 * 1024 * configuration.getStompClientBufferSizeInMb());
        final TigerStompSessionHandler tigerStompSessionHandler = new TigerStompSessionHandler(remoteProxyUrl);
        final ListenableFuture<StompSession> connectFuture = tigerProxyStompClient.connect(
            tracingWebSocketUrl, tigerStompSessionHandler);

        connectFuture.addCallback(stompSession -> log.info("Succesfully opened stomp session {} to url",
                stompSession.getSessionId(), tracingWebSocketUrl),
            throwable -> {
                throw new TigerRemoteProxyClientException("Exception while opening tracing-connection to "
                    + tracingWebSocketUrl, throwable);
            });

        try {
            connectFuture.get(configuration.getConnectionTimeoutInSeconds(), TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new TigerRemoteProxyClientException("Exception while opening tracing-connection to "
                + tracingWebSocketUrl, e);
        }
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
        byte[] messageBytes = tracingMessage.getRawContent();

        if (messageBytes != null) {
            log.info("Received new message {}", new String(messageBytes));

            final RbelElement rbelMessage = getRbelLogger().getRbelConverter()
                .parseMessage(messageBytes, sender, receiver);

            super.triggerListener(rbelMessage);
        } else {
            log.warn("Received message with content 'null'. Skipping parsing...");
        }
    }

    @RequiredArgsConstructor
    private class TigerStompSessionHandler extends StompSessionHandlerAdapter {

        private final String remoteProxyUrl;

        @Override
        public void afterConnected(StompSession stompSession, StompHeaders stompHeaders) {
            log.info("Connecting to tracing point {}", remoteProxyUrl);

            stompSession.subscribe(WS_TRACING, new StompFrameHandler() {
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

            stompSession.subscribe(WS_ERRORS, new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders stompHeaders) {
                        return TigerExceptionDto.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders stompHeaders, Object frameContent) {
                        if (frameContent instanceof TigerExceptionDto) {
                            final TigerExceptionDto exceptionDto = (TigerExceptionDto) frameContent;
                            log.warn("Received remote exception: ({}) {}: {} ",
                                exceptionDto.getClassName(), exceptionDto.getMessage(), exceptionDto.getStacktrace());
                            receivedRemoteExceptions.add(exceptionDto);
                        }
                    }
                }
            );
        }

        @Override
        public void handleException(StompSession stompSession, StompCommand stompCommand, StompHeaders stompHeaders,
            byte[] bytes, Throwable throwable) {
            log.error("handle exception TigerRemoteProxy: {}, {}", new String(bytes), throwable);
            throw new TigerRemoteProxyClientException(throwable);
        }

        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            log.error("handle transport Error TigerRemoteProxy: {}", exception);
            throw new TigerRemoteProxyClientException(exception);
        }
    }

    private class TigerReceivedRemoteException extends RuntimeException {
        public TigerReceivedRemoteException(String message) {
            super(message);
        }
    }
}
