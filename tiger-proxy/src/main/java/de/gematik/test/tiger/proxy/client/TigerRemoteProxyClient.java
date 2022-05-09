/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.gematik.rbellogger.converter.RbelJexlExecutor;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.facet.RbelMessageTimingFacet;
import de.gematik.rbellogger.modifier.RbelModificationDescription;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.proxy.AbstractTigerProxy;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.websocket.ContainerProvider;
import javax.websocket.WebSocketContainer;
import kong.unirest.GenericType;
import kong.unirest.Unirest;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.util.Assert;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

@Slf4j
public class TigerRemoteProxyClient extends AbstractTigerProxy implements AutoCloseable {

    public static final String WS_TRACING = "/topic/traces";
    public static final String WS_DATA = "/topic/data";
    public static final String WS_ERRORS = "/topic/errors";
    private final String remoteProxyUrl;
    private final WebSocketStompClient tigerProxyStompClient;
    @Getter
    private final List<TigerExceptionDto> receivedRemoteExceptions = new ArrayList<>();
    private final Map<String, PartialTracingMessage> partiallyReceivedMessageMap = new HashMap<>();

    public TigerRemoteProxyClient(String remoteProxyUrl) {
        this(remoteProxyUrl, new TigerProxyConfiguration());
    }

    public TigerRemoteProxyClient(String remoteProxyUrl, TigerProxyConfiguration configuration) {
        super(configuration);
        final String tracingWebSocketUrl = remoteProxyUrl.replaceFirst("http", "ws") + "/tracing";
        this.remoteProxyUrl = remoteProxyUrl;

        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        container.setDefaultMaxBinaryMessageBufferSize(1024 * 1024 * configuration.getPerMessageBufferSizeInMb());
        container.setDefaultMaxTextMessageBufferSize(1024 * 1024 * configuration.getPerMessageBufferSizeInMb());
        SockJsClient webSocketClient = new SockJsClient(
            List.of(new WebSocketTransport(new StandardWebSocketClient(container))));
        webSocketClient.stop();

        final MappingJackson2MessageConverter messageConverter = new MappingJackson2MessageConverter();
        messageConverter.getObjectMapper().registerModule(new JavaTimeModule());;
        tigerProxyStompClient = new WebSocketStompClient(webSocketClient);
        tigerProxyStompClient.setMessageConverter(messageConverter);
        tigerProxyStompClient.setInboundMessageSizeLimit(1024 * 1024 * configuration.getStompClientBufferSizeInMb());
        final TigerStompSessionHandler tigerStompSessionHandler = new TigerStompSessionHandler(remoteProxyUrl);
        final ListenableFuture<StompSession> connectFuture = tigerProxyStompClient.connect(
            tracingWebSocketUrl, tigerStompSessionHandler);

        connectFuture.addCallback(stompSession -> {
                log.info("Succesfully opened stomp session {} to url",
                    stompSession.getSessionId(), tracingWebSocketUrl);
            },
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
                    "Unable to remove route. Got " + httpResponse);
            });
    }

    @Override
    public String getBaseUrl() {
        return remoteProxyUrl;
    }

    @Override
    public int getProxyPort() {
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

    @Override
    public RbelModificationDescription addModificaton(RbelModificationDescription modification) {
        return Unirest.put(remoteProxyUrl + "/modification")
            .body(modification)
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .asObject(RbelModificationDescription.class)
            .ifFailure(response -> {
                throw new TigerRemoteProxyClientException(
                    "Unable to add modification. Got " + response.getStatus() +
                        ": " + response.mapError(String.class)
                );
            })
            .getBody();
    }

    @Override
    public List<RbelModificationDescription> getModifications() {
        return Unirest.get(remoteProxyUrl + "/modification")
            .asObject(new GenericType<List<RbelModificationDescription>>() {
            })
            .ifFailure(response -> {
                throw new TigerRemoteProxyClientException(
                    "Unable to get modifications. Got " + response.getStatus() +
                        ": " + response.mapError(String.class)
                );
            })
            .getBody();
    }

    @Override
    public void removeModification(String modificationName) {
        Assert.hasText(modificationName, () -> "No modification name given!");
        Unirest.delete(remoteProxyUrl + "/modification/" + modificationName)
            .asEmpty()
            .ifFailure(httpResponse -> {
                throw new TigerRemoteProxyClientException(
                    "Unable to remove modification. Got " + httpResponse);
            });
    }

    private void propagateNewRbelMessage(RbelHostname sender, RbelHostname receiver, byte[] messageBytes, Optional<ZonedDateTime> transmissionTime) {
        if (messageBytes != null) {
            log.trace("Received new message...", new String(messageBytes));

            final RbelElement rbelMessage = getRbelLogger().getRbelConverter()
                .parseMessage(messageBytes, sender, receiver);
            if (transmissionTime.isPresent()) {
                rbelMessage.addFacet(new RbelMessageTimingFacet(transmissionTime.get()));
            }

            if (messageMatchesFilterCriterion(rbelMessage)) {
                super.triggerListener(rbelMessage);
            } else {
                getRbelLogger().getMessageHistory().remove(rbelMessage);
            }
        } else {
            log.warn("Received message with content 'null'. Skipping parsing...");
        }
    }

    private boolean messageMatchesFilterCriterion(RbelElement rbelMessage) {
        if (StringUtils.isEmpty(getTigerProxyConfiguration().getTrafficEndpointFilterString())) {
            return true;
        }
        return new RbelJexlExecutor().matchesAsJexlExpression(
            rbelMessage,
            getTigerProxyConfiguration().getTrafficEndpointFilterString(),
            Optional.empty());
    }

    public void unsubscribe() {
        tigerProxyStompClient.stop();
    }

    @Override
    public void close() {
        unsubscribe();
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
                            TracingMessagePair messagePair = new TracingMessagePair();
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
                            partiallyReceivedMessageMap.put(tigerTracingDto.getRequestUuid(),
                                messagePair.getRequest());
                            partiallyReceivedMessageMap.put(tigerTracingDto.getResponseUuid(),
                                messagePair.getResponse());
                        }
                    }
                }
            );
            stompSession.subscribe(WS_DATA, new StompFrameHandler() {
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
                            if (!partiallyReceivedMessageMap.containsKey(tracingMessagePart.getUuid())) {
                                log.error("Received stray message part with UUID {}", tracingMessagePart.getUuid());
                            } else {
                                final PartialTracingMessage tracingMessage = partiallyReceivedMessageMap.get(
                                    tracingMessagePart.getUuid());
                                tracingMessage.getMessageParts().add(tracingMessagePart);
                                if (tracingMessage.isComplete()) {
                                    tracingMessage.getMessagePair().checkForCompletePairAndPropagateIfComplete();
                                    partiallyReceivedMessageMap.remove(tracingMessagePart.getUuid());
                                }
                            }
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
            log.error("handle exception with remote url '{}': {}, {}", remoteProxyUrl, new String(bytes), throwable);
            throw new TigerRemoteProxyClientException(throwable);
        }

        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            if (exception instanceof ConnectionLostException) {
                log.warn("Remote client lost connection to url {}", remoteProxyUrl);
            } else {
                log.error("handle transport error from url '{}': {}", remoteProxyUrl, exception);
                throw new TigerRemoteProxyClientException(exception);
            }
        }
    }

    @Data
    @Builder
    private static class PartialTracingMessage {

        private final TigerTracingDto tracingDto;
        private final RbelHostname sender;
        private final RbelHostname receiver;
        private final TracingMessagePair messagePair;
        private final ZonedDateTime transmissionTime;
        private final List<TracingMessagePart> messageParts = new ArrayList<>();

        public boolean isComplete() {
            return !messageParts.isEmpty()
                && messageParts.get(0).getNumberOfMessages() == messageParts.size();
        }

        public byte[] buildCompleteContent() {
            byte[] result = new byte[messageParts.stream()
                .map(TracingMessagePart::getData)
                .mapToInt(Array::getLength)
                .sum()];
            int resultIndex = 0;
            for (int i = 0; i < messageParts.size(); i++) {
                System.arraycopy(messageParts.get(i).getData(), 0,
                    result, resultIndex, messageParts.get(i).getData().length);
                resultIndex += messageParts.get(i).getData().length;
            }
            return result;
        }
    }

    @Data
    public class TracingMessagePair {

        private PartialTracingMessage request;
        private PartialTracingMessage response;

        public void checkForCompletePairAndPropagateIfComplete() {
            if (request != null && response != null
                && request.isComplete() && response.isComplete()) {
                propagateNewRbelMessage(request.getSender(), request.getReceiver(),
                    request.buildCompleteContent(), Optional.ofNullable(request.getTransmissionTime()));
                propagateNewRbelMessage(response.getSender(), response.getReceiver(),
                    response.buildCompleteContent(), Optional.ofNullable(response.getTransmissionTime()));
            }
        }
    }
}
