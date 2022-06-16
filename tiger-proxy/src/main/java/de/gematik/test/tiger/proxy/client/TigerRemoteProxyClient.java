/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.client;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.converter.RbelJexlExecutor;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.facet.RbelMessageTimingFacet;
import de.gematik.rbellogger.modifier.RbelModificationDescription;
import de.gematik.rbellogger.util.RbelFileWriterUtils;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.proxy.AbstractTigerProxy;
import de.gematik.test.tiger.proxy.TigerProxy;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import javax.websocket.ContainerProvider;
import javax.websocket.WebSocketContainer;
import kong.unirest.GenericType;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompSession;
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
    @Getter
    private final String remoteProxyUrl;
    private final WebSocketStompClient tigerProxyStompClient;
    @Getter
    private final List<TigerExceptionDto> receivedRemoteExceptions = new ArrayList<>();
    @Getter
    private final Map<String, PartialTracingMessage> partiallyReceivedMessageMap = new HashMap<>();
    @Getter
    private final TigerStompSessionHandler tigerStompSessionHandler;
    @Getter
    @Setter
    private Duration maximumPartialMessageAge;
    private AtomicReference<String> lastMsgUuid = new AtomicReference();

    public TigerRemoteProxyClient(String remoteProxyUrl) {
        this(remoteProxyUrl, new TigerProxyConfiguration(), null);
    }

    public TigerRemoteProxyClient(String remoteProxyUrl, TigerProxyConfiguration configuration) {
        this(remoteProxyUrl, configuration, null);
    }

    public TigerRemoteProxyClient(String remoteProxyUrl, TigerProxyConfiguration configuration,
        @Nullable TigerProxy masterTigerProxy) {
        super(configuration, masterTigerProxy == null ? null : masterTigerProxy.getRbelLogger());
        this.remoteProxyUrl = remoteProxyUrl;

        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        container.setDefaultMaxBinaryMessageBufferSize(1024 * 1024 * configuration.getPerMessageBufferSizeInMb());
        container.setDefaultMaxTextMessageBufferSize(1024 * 1024 * configuration.getPerMessageBufferSizeInMb());
        SockJsClient webSocketClient = new SockJsClient(
            List.of(new WebSocketTransport(new StandardWebSocketClient(container))));
        webSocketClient.stop();

        if (masterTigerProxy != null) {
            addRbelMessageListener(masterTigerProxy::triggerListener);
        }

        final MappingJackson2MessageConverter messageConverter = new MappingJackson2MessageConverter();
        messageConverter.getObjectMapper().registerModule(new JavaTimeModule());

        tigerProxyStompClient = new WebSocketStompClient(webSocketClient);
        tigerProxyStompClient.setMessageConverter(messageConverter);
        tigerProxyStompClient.setInboundMessageSizeLimit(1024 * 1024 * configuration.getStompClientBufferSizeInMb());
        tigerStompSessionHandler = new TigerStompSessionHandler(this);
        connectToRemoteUrl(tigerStompSessionHandler,
            configuration.getConnectionTimeoutInSeconds(),
            getTigerProxyConfiguration().isDownloadInitialTrafficFromEndpoints());
        maximumPartialMessageAge = Duration.ofSeconds(configuration.getMaximumPartialMessageAgeInSeconds());
    }

    private String getTracingWebSocketUrl(String remoteProxyUrl) {
        return remoteProxyUrl.replaceFirst("http", "ws") + "/tracing";
    }

    private void downloadTrafficFromRemoteProxy() {
        new TigerRemoteTrafficDownloader(this, lastMsgUuid).execute();
    }

    void connectToRemoteUrl(TigerStompSessionHandler tigerStompSessionHandler,
        int connectionTimeoutInSeconds, boolean downloadTraffic) {
        waitForRemoteTigerProxyToBeOnline(remoteProxyUrl);
        final String tracingWebSocketUrl = getTracingWebSocketUrl(remoteProxyUrl);
        final ListenableFuture<StompSession> connectFuture
            = tigerProxyStompClient.connect(tracingWebSocketUrl, tigerStompSessionHandler);

        connectFuture.addCallback(stompSession -> {
                log.info("Succesfully opened stomp session {} to url",
                    stompSession.getSessionId(), tracingWebSocketUrl);
                if (downloadTraffic) {
                    new Thread(this::downloadTrafficFromRemoteProxy,
                        "connectToRemoteUrl-Download").start();
                }
            },
            throwable -> {
                throw new TigerRemoteProxyClientException("Exception while opening tracing-connection to "
                    + tracingWebSocketUrl, throwable);
            });

        try {
            connectFuture.get(connectionTimeoutInSeconds, TimeUnit.SECONDS);
        } catch (RuntimeException | ExecutionException | TimeoutException e) {
            throw new TigerRemoteProxyClientException("Exception while opening tracing-connection to "
                + tracingWebSocketUrl, e);
        } catch (InterruptedException e) {
            log.error("InterruptedException while opening tracing-connection to {}", tracingWebSocketUrl);
            Thread.currentThread().interrupt();
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

    Optional<RbelElement> buildNewRbelMessage(RbelHostname sender, RbelHostname receiver, byte[] messageBytes,
        Optional<ZonedDateTime> transmissionTime, String uuid) {
        if (messageBytes != null) {
            log.debug("Received new message with ID '{}'", uuid);

            final RbelElement rbelMessage = getRbelLogger().getRbelConverter()
                .parseMessage(RbelElement.builder()
                    .uuid(uuid)
                    .rawContent(messageBytes)
                    .build(), sender, receiver);

            transmissionTime.ifPresent(
                zonedDateTime -> rbelMessage.addFacet(new RbelMessageTimingFacet(zonedDateTime)));
            return Optional.of(rbelMessage);
        } else {
            log.warn("Received message with content 'null'. Skipping parsing...");
            return Optional.empty();
        }
    }

    void propagateMessage(RbelElement rbelMessage) {
        if (messageMatchesFilterCriterion(rbelMessage)) {
            super.triggerListener(rbelMessage);
        } else {
            getRbelLogger().getMessageHistory().remove(rbelMessage);
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

    void receiveNewMessagePart(TracingMessagePart tracingMessagePart) {
        final PartialTracingMessage tracingMessage = retrieveOrInitializePartialMessage(
            tracingMessagePart.getUuid(), PartialTracingMessage.builder().build());

        tracingMessage.getMessageParts().add(tracingMessagePart);
        checkForCompletion(tracingMessage, tracingMessagePart.getUuid());
    }

    private PartialTracingMessage retrieveOrInitializePartialMessage(String uuid, PartialTracingMessage message) {
        synchronized (partiallyReceivedMessageMap) {
            if (partiallyReceivedMessageMap.containsKey(uuid)) {
                return partiallyReceivedMessageMap.get(uuid);
            }
            partiallyReceivedMessageMap.put(uuid, message);
            return message;
        }
    }

    public void initOrUpdateMessagePart(String uuid, PartialTracingMessage partialTracingMessage) {
        synchronized (partiallyReceivedMessageMap) {
            if (partiallyReceivedMessageMap.containsKey(uuid)) {
                val oldMessage = partiallyReceivedMessageMap.get(uuid);
                partialTracingMessage.getMessageParts().addAll(oldMessage.getMessageParts());
            }
            partiallyReceivedMessageMap.put(uuid, partialTracingMessage);
        }
        checkForCompletion(partialTracingMessage, uuid);
    }

    private void checkForCompletion(PartialTracingMessage tracingMessage, String messageUuid) {
        if (tracingMessage.isComplete()) {
            tracingMessage.getMessagePair().checkForCompletePairAndPropagateIfComplete();
            partiallyReceivedMessageMap.remove(messageUuid);
            lastMsgUuid.set(messageUuid);
        }
    }

    public void triggerPartialMessageCleanup() {
        final ZonedDateTime cutoff = ZonedDateTime.now().minus(maximumPartialMessageAge);
        synchronized (partiallyReceivedMessageMap) {
            final Iterator<PartialTracingMessage> entryIterator
                = partiallyReceivedMessageMap.values().iterator();
            while (entryIterator.hasNext()) {
                PartialTracingMessage next = entryIterator.next();
                log.info("Trying to remove {}, cutoff is {}", next.getReceivedTime(), cutoff);
                if (cutoff.isAfter(next.getReceivedTime())) {
                    entryIterator.remove();
                }
            }
        }
    }
}
