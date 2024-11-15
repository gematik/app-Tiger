/*
 * Copyright 2024 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.proxy.client;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelElementConvertionPair;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.util.IRbelMessageListener;
import de.gematik.test.tiger.common.config.RbelModificationDescription;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerConfigurationRoute;
import de.gematik.test.tiger.common.jexl.TigerJexlExecutor;
import de.gematik.test.tiger.proxy.AbstractTigerProxy;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.data.TigerProxyRoute;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyStartupException;
import de.gematik.test.tiger.proxy.handler.BinaryChunksBuffer;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import kong.unirest.GenericType;
import kong.unirest.Unirest;
import lombok.Getter;
import lombok.Setter;
import lombok.val;
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

/**
 * The TigerRemoteProxyClient is a client for a TigerProxy that is running on a remote machine. It
 * is mostly used by the TigerProxy itself to establish and hold that connection. It can also be
 * used to manipulate the setup on a remote proxy (e.g. adding routes, modifications, etc.). The
 * second scenario would be independently of a master TigerProxy.
 */
public class TigerRemoteProxyClient extends AbstractTigerProxy implements AutoCloseable {

  public static final String WS_TRACING = "/topic/traces";
  public static final String WS_DATA = "/topic/data";
  public static final String WS_ERRORS = "/topic/errors";
  @Getter private final String remoteProxyUrl;
  private final WebSocketStompClient tigerProxyStompClient;

  @Getter private final List<TigerExceptionDto> receivedRemoteExceptions = new ArrayList<>();

  @Getter
  private final Map<String, PartialTracingMessage> partiallyReceivedMessageMap = new HashMap<>();

  @Getter
  private final BinaryChunksBuffer binaryChunksBuffer =
      new BinaryChunksBuffer(getRbelLogger().getRbelConverter(), getTigerProxyConfiguration());

  @Getter private final TigerStompSessionHandler tigerStompSessionHandler;
  @Nullable private final TigerProxy masterTigerProxy;
  @Getter @Setter private Duration maximumPartialMessageAge;
  private final AtomicReference<StompSession> stompSession = new AtomicReference<>();
  @Getter private final AtomicReference<String> lastMessageUuid = new AtomicReference<>();
  private final SockJsClient webSocketClient;
  private final int connectionTimeoutInSeconds;

  public TigerRemoteProxyClient(String remoteProxyUrl) {
    this(remoteProxyUrl, new TigerProxyConfiguration(), null);
  }

  public TigerRemoteProxyClient(String remoteProxyUrl, TigerProxyConfiguration configuration) {
    this(remoteProxyUrl, configuration, null);
  }

  public TigerRemoteProxyClient(
      String remoteProxyUrl,
      TigerProxyConfiguration configuration,
      @Nullable TigerProxy masterTigerProxy) {
    super(configuration, masterTigerProxy == null ? null : masterTigerProxy.getRbelLogger());
    this.remoteProxyUrl = remoteProxyUrl;
    this.masterTigerProxy = masterTigerProxy;

    WebSocketContainer container = ContainerProvider.getWebSocketContainer();
    container.setDefaultMaxBinaryMessageBufferSize(
        1024 * 1024 * configuration.getPerMessageBufferSizeInMb());
    container.setDefaultMaxTextMessageBufferSize(
        1024 * 1024 * configuration.getPerMessageBufferSizeInMb());

    final MappingJackson2MessageConverter messageConverter = new MappingJackson2MessageConverter();
    messageConverter.getObjectMapper().registerModule(new JavaTimeModule());

    StandardWebSocketClient wsClient = new StandardWebSocketClient(container);
    webSocketClient = new SockJsClient(List.of(new WebSocketTransport(wsClient)));
    tigerProxyStompClient = new WebSocketStompClient(webSocketClient);
    tigerProxyStompClient.setMessageConverter(messageConverter);
    tigerProxyStompClient.setInboundMessageSizeLimit(
        1024 * 1024 * configuration.getStompClientBufferSizeInMb());
    tigerStompSessionHandler = new TigerStompSessionHandler(this);
    maximumPartialMessageAge =
        Duration.ofSeconds(configuration.getMaximumPartialMessageAgeInSeconds());
    connectionTimeoutInSeconds = configuration.getConnectionTimeoutInSeconds();
  }

  public void connect() {
    try {
      connectToRemoteUrl(
          tigerStompSessionHandler,
          connectionTimeoutInSeconds,
          getTigerProxyConfiguration().isDownloadInitialTrafficFromEndpoints());
    } catch (TigerProxyStartupException e) {
      if (getTigerProxyConfiguration().isFailOnOfflineTrafficEndpoints()) {
        log.warn("Ignoring offline traffic endpoint {}", remoteProxyUrl);
      } else {
        throw e;
      }
    }
  }

  private String getTracingWebSocketUrl(String remoteProxyUrl) {
    return remoteProxyUrl.replaceFirst("http", "ws") + "/tracing";
  }

  private void downloadTrafficFromRemoteProxy() {
    new TigerRemoteTrafficDownloader(this).execute();
  }

  void connectToRemoteUrl(
      TigerStompSessionHandler tigerStompSessionHandler,
      int connectionTimeoutInSeconds,
      boolean downloadTraffic) {
    if (isShuttingDown()) {
      return;
    }
    waitForRemoteTigerProxyToBeOnline(remoteProxyUrl);
    if (isShuttingDown()) {
      return;
    }
    log.info("remote proxy at {} is online, now connecting...", remoteProxyUrl);
    final String tracingWebSocketUrl = getTracingWebSocketUrl(remoteProxyUrl);
    final ListenableFuture<StompSession> connectFuture =
        tigerProxyStompClient.connect(tracingWebSocketUrl, tigerStompSessionHandler);

    connectFuture.addCallback(
        stompSessionInCallback -> {
          log.info(
              "Successfully opened stomp session {} to url {}",
              stompSessionInCallback.getSessionId(),
              tracingWebSocketUrl);
          tigerStompSessionHandler.setOnConnectedCallback(
              () -> {
                if (downloadTraffic) {
                  downloadTrafficFromRemoteProxy();
                }
              });
        },
        throwable -> {
          throw new TigerRemoteProxyClientException(
              "Exception while opening tracing-connection to " + tracingWebSocketUrl, throwable);
        });

    try {
      stompSession.set(connectFuture.get(connectionTimeoutInSeconds, TimeUnit.SECONDS));
    } catch (RuntimeException | ExecutionException | TimeoutException e) {
      throw new TigerRemoteProxyClientException(
          "Exception while opening tracing-connection to " + tracingWebSocketUrl, e);
    } catch (InterruptedException e) {
      log.error("InterruptedException while opening tracing-connection to {}", tracingWebSocketUrl);
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public TigerProxyRoute addRoute(TigerProxyRoute tigerRoute) {
    return Unirest.put(remoteProxyUrl + "/route")
        .body(tigerRoute)
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .asObject(TigerProxyRoute.class)
        .ifFailure(
            response -> {
              throw new TigerRemoteProxyClientException(
                  "Unable to add route. Got "
                      + response.getStatus()
                      + ": "
                      + response.mapError(String.class));
            })
        .getBody();
  }

  @Override
  public void removeRoute(String routeId) {
    Assert.hasText(routeId, () -> "No route ID given!");
    Unirest.delete(remoteProxyUrl + "/route/" + routeId)
        .asEmpty()
        .ifFailure(
            httpResponse -> {
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
  public List<TigerProxyRoute> getRoutes() {
    return Unirest.get(remoteProxyUrl + "/route")
        .asObject(new GenericType<List<TigerProxyRoute>>() {})
        .ifFailure(
            response -> {
              throw new TigerRemoteProxyClientException(
                  "Unable to get routes. Got "
                      + response.getStatus()
                      + ": "
                      + response.mapError(String.class));
            })
        .getBody();
  }

  @Override
  public RbelModificationDescription addModificaton(RbelModificationDescription modification) {
    return Unirest.put(remoteProxyUrl + "/modification")
        .body(modification)
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .asObject(RbelModificationDescription.class)
        .ifFailure(
            response -> {
              throw new TigerRemoteProxyClientException(
                  "Unable to add modification. Got "
                      + response.getStatus()
                      + ": "
                      + response.mapError(String.class));
            })
        .getBody();
  }

  @Override
  public List<RbelModificationDescription> getModifications() {
    return Unirest.get(remoteProxyUrl + "/modification")
        .asObject(new GenericType<List<RbelModificationDescription>>() {})
        .ifFailure(
            response -> {
              throw new TigerRemoteProxyClientException(
                  "Unable to get modifications. Got "
                      + response.getStatus()
                      + ": "
                      + response.mapError(String.class));
            })
        .getBody();
  }

  @Override
  public void removeModification(String modificationName) {
    Assert.hasText(modificationName, () -> "No modification name given!");
    Unirest.delete(remoteProxyUrl + "/modification/" + modificationName)
        .asEmpty()
        .ifFailure(
            httpResponse -> {
              throw new TigerRemoteProxyClientException(
                  "Unable to remove modification. Got " + httpResponse);
            });
  }

  Optional<CompletableFuture<RbelElement>> buildNewRbelMessage(
      RbelHostname sender,
      RbelHostname receiver,
      byte[] messageBytes,
      Optional<ZonedDateTime> transmissionTime,
      String uuid) {
    return buildNewMessage(
        sender, receiver, messageBytes, Optional.empty(), transmissionTime, uuid);
  }

  Optional<CompletableFuture<RbelElement>> tryParseMessage(PartialTracingMessage message) {
    if (message.isUnparsedChunk()) {
      return getBinaryChunksBuffer()
          .tryToConvertMessageAndBufferUnusedBytes(
              message.buildCompleteContent(),
              message.getSender().asSocketAddress(),
              message.getReceiver().asSocketAddress())
          .map(CompletableFuture::completedFuture);

    } else {
      return buildNewRbelMessage(
          message.getSender(),
          message.getReceiver(),
          message.buildCompleteContent(),
          Optional.ofNullable(message.getTransmissionTime()),
          message.getTracingDto().getRequestUuid());
    }
  }

  Optional<CompletableFuture<RbelElement>> buildNewRbelResponse(
      RbelHostname sender,
      RbelHostname receiver,
      byte[] messageBytes,
      Optional<CompletableFuture<RbelElement>> parsedRequest,
      Optional<ZonedDateTime> transmissionTime,
      String uuid) {
    return buildNewMessage(sender, receiver, messageBytes, parsedRequest, transmissionTime, uuid);
  }

  private Optional<CompletableFuture<RbelElement>> buildNewMessage(
      RbelHostname sender,
      RbelHostname receiver,
      byte[] messageBytes,
      Optional<CompletableFuture<RbelElement>> parsedRequest,
      Optional<ZonedDateTime> transmissionTime,
      String uuid) {
    if (messageBytes != null) {
      if (log.isTraceEnabled()) {
        log.trace("Received new message with ID '{}'", uuid);
      }

      return Optional.of(
          getRbelLogger()
              .getRbelConverter()
              .parseMessageAsync(
                  new RbelElementConvertionPair(
                      RbelElement.builder().uuid(uuid).rawContent(messageBytes).build(),
                      parsedRequest.orElse(null)),
                  sender,
                  receiver,
                  transmissionTime));
    } else {
      log.warn("Received message with content 'null'. Skipping parsing...");
      return Optional.empty();
    }
  }

  void propagateMessage(RbelElement rbelMessage) {
    super.triggerListener(rbelMessage);
  }

  void removeMessage(RbelElement rbelMessage) {
    getRbelLogger().getRbelConverter().removeMessage(rbelMessage);
  }

  public boolean messageMatchesFilterCriterion(RbelElement rbelMessage) {
    if (StringUtils.isEmpty(getTigerProxyConfiguration().getTrafficEndpointFilterString())) {
      return true;
    }
    return TigerJexlExecutor.matchesAsJexlExpression(
        rbelMessage,
        getTigerProxyConfiguration().getTrafficEndpointFilterString(),
        Optional.empty());
  }

  @Override
  public void close() {
    super.close();
    log.debug("Stopping websocket client with remote URL '{}'", remoteProxyUrl);
    if (stompSession.get() != null && stompSession.get().isConnected()) {
      stompSession.get().disconnect();
    }
    tigerProxyStompClient.stop();
    webSocketClient.stop();
  }

  void receiveNewMessagePart(TracingMessagePart tracingMessagePart) {
    final PartialTracingMessage tracingMessage =
        retrieveOrInitializePartialMessage(
            tracingMessagePart.getUuid(), PartialTracingMessage.builder().build());

    tracingMessage.getMessageParts().add(tracingMessagePart);
    checkForCompletion(tracingMessage, tracingMessagePart.getUuid());
  }

  private PartialTracingMessage retrieveOrInitializePartialMessage(
      String uuid, PartialTracingMessage message) {
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
      tracingMessage.getMessageFrame().checkForCompletePairAndPropagateIfComplete();
      partiallyReceivedMessageMap.remove(messageUuid);
    }
  }

  public void triggerPartialMessageCleanup() {
    final ZonedDateTime cutoff = ZonedDateTime.now().minus(maximumPartialMessageAge);
    synchronized (partiallyReceivedMessageMap) {
      final Iterator<PartialTracingMessage> entryIterator =
          partiallyReceivedMessageMap.values().iterator();
      while (entryIterator.hasNext()) {
        PartialTracingMessage next = entryIterator.next();
        log.trace("Trying to remove {}, cutoff is {}", next.getReceivedTime(), cutoff);
        if (cutoff.isAfter(next.getReceivedTime())) {
          entryIterator.remove();
        }
      }
    }
  }

  public boolean messageUuidKnown(final String messageUuid) {
    return getRbelLogger().getRbelConverter().isMessageUuidAlreadyKnown(messageUuid);
  }

  public boolean isConnected() {
    return Optional.ofNullable(stompSession)
        .map(AtomicReference::get)
        .map(StompSession::isConnected)
        .orElse(false);
  }

  @Override
  public void triggerListener(RbelElement element) {
    if (masterTigerProxy != null) {
      masterTigerProxy.triggerListener(element);
    } else {
      super.triggerListener(element);
    }
  }

  @Override
  public List<IRbelMessageListener> getRbelMessageListeners() {
    if (masterTigerProxy != null) {
      return masterTigerProxy.getRbelMessageListeners();
    } else {
      return super.getRbelMessageListeners();
    }
  }

  @Override
  public void addRbelMessageListener(IRbelMessageListener listener) {
    if (masterTigerProxy != null) {
      masterTigerProxy.addRbelMessageListener(listener);
    } else {
      super.addRbelMessageListener(listener);
    }
  }

  @Override
  public void removeRbelMessageListener(IRbelMessageListener listener) {
    if (masterTigerProxy != null) {
      masterTigerProxy.removeRbelMessageListener(listener);
    } else {
      super.removeRbelMessageListener(listener);
    }
  }
}
