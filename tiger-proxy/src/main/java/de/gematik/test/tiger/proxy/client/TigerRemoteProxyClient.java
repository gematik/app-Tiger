/*
 *
 * Copyright 2021-2025 gematik GmbH
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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.test.tiger.proxy.client;

import static de.gematik.rbellogger.data.RbelMessageMetadata.PREVIOUS_MESSAGE_UUID;
import static de.gematik.rbellogger.util.MemoryConstants.MB;

import de.gematik.rbellogger.RbelConversionPhase;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMessageKind;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.data.core.RbelFacet;
import de.gematik.rbellogger.util.IRbelMessageListener;
import de.gematik.test.tiger.common.RingBufferHashMap;
import de.gematik.test.tiger.common.RingBufferHashSet;
import de.gematik.test.tiger.common.config.RbelModificationDescription;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.jexl.TigerJexlExecutor;
import de.gematik.test.tiger.proxy.AbstractTigerProxy;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.TigerProxyMessageDeletedPlugin;
import de.gematik.test.tiger.proxy.TigerProxyRemoteTransmissionConversionPlugin;
import de.gematik.test.tiger.proxy.data.TigerProxyRoute;
import de.gematik.test.tiger.proxy.data.TigerRouteDto;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyStartupException;
import de.gematik.test.tiger.proxy.handler.MultipleBinaryConnectionParser;
import de.gematik.test.tiger.proxy.handler.SingleConnectionParser;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import kong.unirest.core.GenericType;
import kong.unirest.core.Unirest;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.Assert;
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
  private static final Duration EXPIRED_MESSAGE_CHECK_INTERVAL = Duration.ofSeconds(1);
  private static final Duration REDOWNLOAD_RECHECK_INTERVAL = Duration.ofSeconds(1);
  private static final Duration INITIAL_RECONNECT_BACKOFF = Duration.ofSeconds(1);
  private static final Duration MAX_RECONNECT_BACKOFF = Duration.ofSeconds(30);
  @Getter private final String remoteProxyUrl;
  @Getter private String connectedRemoteProxyUrl;
  private final String remoteProxyControlUrl;
  private final WebSocketStompClient tigerProxyStompClient;

  @Getter private final List<TigerExceptionDto> receivedRemoteExceptions = new ArrayList<>();

  @Getter private final MultipleBinaryConnectionParser binaryChunksBuffer;

  @Getter private final TigerStompSessionHandler tigerStompSessionHandler;
  @Nullable private final TigerProxy masterTigerProxy;
  @Getter private final PartialMessageAssembler messageAssembler;
  private final AtomicReference<StompSession> stompSession = new AtomicReference<>();
  @Getter private final AtomicReference<String> lastMessageUuid = new AtomicReference<>();
  private final ThreadPoolTaskScheduler heartbeatScheduler;
  private final AtomicBoolean reconnectInFlight = new AtomicBoolean(false);

  @Getter(AccessLevel.PACKAGE)
  private final AtomicInteger consecutiveConnectFailures = new AtomicInteger(0);

  @Setter(AccessLevel.PACKAGE)
  private Duration initialReconnectBackoff = INITIAL_RECONNECT_BACKOFF;

  private final SockJsClient webSocketClient;
  private final int connectionTimeoutInSeconds;

  @Getter
  private final ScheduledExecutorService meshHandlerPool =
      Executors.newScheduledThreadPool(
          0,
          r -> {
            Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setName("TigerProxyClient-%s-%d".formatted(getName(), t.getId()));
            return t;
          });

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
    this.remoteProxyControlUrl = normalizeLoopbackRemoteProxyUrl(remoteProxyUrl);
    this.connectedRemoteProxyUrl = this.remoteProxyControlUrl;
    this.masterTigerProxy = masterTigerProxy;
    this.binaryChunksBuffer =
        new MultipleBinaryConnectionParser(
            masterTigerProxy == null ? this : masterTigerProxy, null);

    WebSocketContainer container = ContainerProvider.getWebSocketContainer();
    var perMessageBufferSize = configuration.getPerMessageBufferSizeInMb() * MB;
    container.setDefaultMaxBinaryMessageBufferSize(perMessageBufferSize);
    container.setDefaultMaxTextMessageBufferSize(perMessageBufferSize);

    final JacksonJsonMessageConverter messageConverter = new JacksonJsonMessageConverter();

    StandardWebSocketClient wsClient = new StandardWebSocketClient(container);
    webSocketClient = new SockJsClient(List.of(new WebSocketTransport(wsClient)));
    tigerProxyStompClient = new WebSocketStompClient(webSocketClient);
    tigerProxyStompClient.setMessageConverter(messageConverter);
    tigerProxyStompClient.setInboundMessageSizeLimit(
        configuration.getStompClientBufferSizeInMb() * MB);
    heartbeatScheduler = buildHeartbeatScheduler();
    tigerProxyStompClient.setTaskScheduler(heartbeatScheduler);
    final long heartbeat =
        Duration.ofSeconds(configuration.getStompHeartbeatInSeconds()).toMillis();
    tigerProxyStompClient.setDefaultHeartbeat(new long[] {heartbeat, heartbeat});
    tigerStompSessionHandler = new TigerStompSessionHandler(this);
    messageAssembler =
        new PartialMessageAssembler(
            log,
            () -> getRbelLogger().getRbelConverter().getKnownMessageUuids(),
            this::signalNewCompletedMessage,
            Duration.ofSeconds(configuration.getMaximumPartialMessageAgeInSeconds()));
    scheduleExpiredMessageRemoval();
    connectionTimeoutInSeconds = configuration.getConnectionTimeoutInSeconds();

    addRbelMessageListener(this::signalNewCompletedMessage);
    var converter = getRbelLogger().getRbelConverter();
    converter.addConverter(new TigerProxyMessageDeletedPlugin(this));
    converter.addClearHistoryCallback(this::discardDelayedParsingTasks);
    converter.addMessageRemovedFromHistoryCallback(this::handleMessageRemovalFromHistory);
  }

  private void scheduleExpiredMessageRemoval() {
    if (meshHandlerPool.isShutdown()) {
      return;
    }
    meshHandlerPool.schedule(
        this::removeExpiredMessagesAndRearm,
        EXPIRED_MESSAGE_CHECK_INTERVAL.toMillis(),
        TimeUnit.MILLISECONDS);
  }

  private void removeExpiredMessagesAndRearm() {
    try {
      messageAssembler.removeExpiredMessages();
    } catch (RuntimeException e) {
      log.warn(
          "Could not remove expired partial messages, retrying in {}",
          EXPIRED_MESSAGE_CHECK_INTERVAL,
          e);
    } finally {
      scheduleExpiredMessageRemoval();
    }
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

  static String normalizeLoopbackRemoteProxyUrl(String remoteProxyUrl) {
    try {
      URI uri = new URI(remoteProxyUrl);
      if (uri.getHost() == null) {
        return remoteProxyUrl;
      }

      InetAddress inetAddress = InetAddress.getByName(uri.getHost());
      if (!inetAddress.isLoopbackAddress() || "localhost".equalsIgnoreCase(uri.getHost())) {
        return remoteProxyUrl;
      }

      return new URI(
              uri.getScheme(),
              uri.getUserInfo(),
              "localhost",
              uri.getPort(),
              uri.getPath(),
              uri.getQuery(),
              uri.getFragment())
          .toString();
    } catch (URISyntaxException | UnknownHostException e) {
      return remoteProxyUrl;
    }
  }

  String getRemoteProxyControlUrl() {
    return remoteProxyControlUrl;
  }

  private void downloadTrafficFromRemoteProxy() {
    var discardedUuids = messageAssembler.discardIncompleteMessagesForRedownload();
    try {
      new TigerRemoteTrafficDownloader(this).execute();
    } finally {
      messageAssembler.finishRedownload(discardedUuids);
    }
  }

  void connectToRemoteUrl(
      TigerStompSessionHandler tigerStompSessionHandler,
      int connectionTimeoutInSeconds,
      boolean downloadTraffic) {
    if (isShuttingDown()) {
      return;
    }
    if (!claimTheConnectionAttempt()) {
      log.debug(
          "Connection attempt to {} already in progress, ignoring overlapping request",
          remoteProxyUrl);
      return;
    }
    try {
      connectedRemoteProxyUrl = waitForRemoteTigerProxyToBeOnline(remoteProxyControlUrl);
    } catch (RuntimeException e) {
      reconnectInFlight.set(false);
      consecutiveConnectFailures.incrementAndGet();
      throw e;
    }
    if (isShuttingDown()) {
      reconnectInFlight.set(false);
      return;
    }
    log.info("remote proxy at {} is online, now connecting...", connectedRemoteProxyUrl);
    final var tracingWebSocketUrl = getTracingWebSocketUrl(connectedRemoteProxyUrl);
    tigerStompSessionHandler.setOnConnectedCallback(
        () -> meshHandlerPool.execute(() -> finishConnecting(downloadTraffic)));
    tigerProxyStompClient
        .connectAsync(tracingWebSocketUrl, tigerStompSessionHandler)
        .orTimeout(connectionTimeoutInSeconds, TimeUnit.SECONDS)
        .thenAccept(
            stompSessionInCallback -> {
              log.info(
                  "Successfully opened stomp session {} to url {}",
                  stompSessionInCallback.getSessionId(),
                  tracingWebSocketUrl);
              stompSession.set(stompSessionInCallback);
            })
        .exceptionally(
            throwable -> {
              reconnectInFlight.set(false);
              consecutiveConnectFailures.incrementAndGet();
              log.warn(
                  "Exception while opening tracing-connection to {}",
                  tracingWebSocketUrl,
                  throwable);
              if (!isShuttingDown()) {
                scheduleReconnect(tigerStompSessionHandler);
              }
              return null;
            });
  }

  private void reconnect(TigerStompSessionHandler tigerStompSessionHandler) {
    try {
      connectToRemoteUrl(
          tigerStompSessionHandler,
          getTigerProxyConfiguration().getConnectionTimeoutInSeconds(),
          true);
    } catch (RuntimeException e) {
      log.warn("Reconnecting to {} failed", remoteProxyUrl, e);
      if (!isShuttingDown()) {
        scheduleReconnect(tigerStompSessionHandler);
      }
    }
  }

  /**
   * Schedules a reconnect after a delay that grows with the number of consecutive failures, so a
   * permanently unreachable remote does not get hammered with back-to-back attempts.
   */
  void scheduleReconnect(TigerStompSessionHandler tigerStompSessionHandler) {
    final var delay = computeReconnectBackoff(consecutiveConnectFailures.get());
    log.info("Reconnecting to {} in {}", remoteProxyUrl, delay);
    meshHandlerPool.schedule(
        () -> reconnect(tigerStompSessionHandler), delay.toMillis(), TimeUnit.MILLISECONDS);
  }

  private Duration computeReconnectBackoff(int consecutiveFailures) {
    final var delay = initialReconnectBackoff.multipliedBy(1L << Math.min(consecutiveFailures, 5));
    return delay.compareTo(MAX_RECONNECT_BACKOFF) > 0 ? MAX_RECONNECT_BACKOFF : delay;
  }

  private ThreadPoolTaskScheduler buildHeartbeatScheduler() {
    val scheduler = new ThreadPoolTaskScheduler();
    scheduler.setThreadNamePrefix("TigerProxyClientHeartbeat-%s-".formatted(getName()));
    scheduler.setPoolSize(1);
    scheduler.setDaemon(true);
    scheduler.initialize();
    return scheduler;
  }

  private boolean claimTheConnectionAttempt() {
    return reconnectInFlight.compareAndSet(false, true);
  }

  private void finishConnecting(boolean downloadTraffic) {
    try {
      webSocketConnectionStartTime.set(ZonedDateTime.now());
      remoteClockOffset =
          ClockSkewEstimator.estimateOffset(
                  connectedRemoteProxyUrl, getTigerProxyConfiguration().getClockSyncSamples())
              .orElse(Duration.ZERO);
      log.info(
          "Connected to remote proxy at {}, now downloading traffic...", connectedRemoteProxyUrl);
      if (downloadTraffic) {
        downloadTrafficFromRemoteProxy();
      }
      log.info("Successfully downloaded traffic from remote proxy at {}", connectedRemoteProxyUrl);
      consecutiveConnectFailures.set(0);
    } catch (RuntimeException e) {
      log.error("Error while finishing connection setup to {}", connectedRemoteProxyUrl, e);
    } finally {
      reconnectInFlight.set(false);
    }
  }

  @Override
  public TigerProxyRoute addRoute(TigerProxyRoute tigerRoute) {
    return Unirest.put(getBaseUrl() + "/route")
        .body(TigerRouteDto.create(tigerRoute))
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

    final var isInternalOptional =
        Unirest.get(getBaseUrl() + "/route")
            .asObject(new GenericType<List<TigerProxyRoute>>() {})
            .getBody()
            .stream()
            .filter(route -> Strings.CS.equals(route.getId(), routeId))
            .findFirst()
            .map(TigerProxyRoute::isInternalRoute);
    if (isInternalOptional.isEmpty()) {
      return; // route does not exist on remote, delete therefore successful
    }

    if (isInternalOptional.get()) {
      throw new TigerRemoteProxyClientException(
          "Could not delete route with id '" + routeId + "': Is internal route!");
    }

    Unirest.delete(getBaseUrl() + "/route/" + routeId)
        .asString()
        .ifFailure(
            httpResponse -> {
              throw new TigerRemoteProxyClientException(
                  "Unable to remove route. Got " + httpResponse.getBody());
            });
  }

  @Override
  public void clearAllRoutes() {
    Unirest.get(getBaseUrl() + "/route")
        .asObject(new GenericType<List<TigerProxyRoute>>() {})
        .getBody()
        .stream()
        .filter(route -> !route.isInternalRoute())
        .map(TigerProxyRoute::getId)
        .forEach(this::removeRoute);
  }

  @Override
  public String getBaseUrl() {
    return connectedRemoteProxyUrl;
  }

  @Override
  public int getProxyPort() {
    return 0;
  }

  @Override
  public List<TigerProxyRoute> getRoutes() {
    return Unirest.get(getBaseUrl() + "/route")
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
    return Unirest.put(getBaseUrl() + "/modification")
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
    return Unirest.get(getBaseUrl() + "/modification")
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
    Unirest.delete(getBaseUrl() + "/modification/" + modificationName)
        .asEmpty()
        .ifFailure(
            httpResponse -> {
              throw new TigerRemoteProxyClientException(
                  "Unable to remove modification. Got " + httpResponse);
            });
  }

  void tryParseMessages(PartialTracingMessage message, Consumer<RbelElement> messagePreProcessor) {
    var messageUuid = message.getTracingDto().getMessageUuid();
    log.trace("Trying to parse message with UUID {}", messageUuid);
    if (!getRbelLogger()
        .getRbelConverter()
        .getKnownMessageUuids()
        .isAlreadyConverted(messageUuid)) {
      getBinaryChunksBuffer()
          .addToBuffer(
              messageUuid,
              message.getSender(),
              message.getReceiver(),
              message.buildCompleteContent().toByteArray(),
              ClockSkewEstimator.withCompensatedTransmissionTime(
                  message.getAdditionalInformation(), getRemoteClockOffset()),
              message.getTracingDto().isRequest()
                  ? RbelMessageKind.REQUEST
                  : RbelMessageKind.RESPONSE,
              messagePreProcessor,
              Optional.ofNullable(
                      message.getAdditionalInformation().get(PREVIOUS_MESSAGE_UUID.getKey()))
                  .map(Object::toString)
                  .orElse(null));
    }
  }

  @Override
  protected boolean isTigerProxyMatching(TigerProxyRemoteTransmissionConversionPlugin plugin) {
    return plugin.getTigerProxy().getRbelLogger() == getRbelLogger();
  }

  @Override
  public RbelLogger getRbelLogger() {
    if (masterTigerProxy != null) {
      return masterTigerProxy.getRbelLogger();
    } else {
      return super.getRbelLogger();
    }
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
    log.debug("Stopping websocket client with remote URL '{}'", remoteProxyUrl);
    if (stompSession.get() != null && stompSession.get().isConnected()) {
      stompSession.get().disconnect();
    }
    tigerProxyStompClient.stop();
    webSocketClient.stop();
    heartbeatScheduler.shutdown();
    meshHandlerPool.shutdownNow();
  }

  public boolean isConnected() {
    return Optional.ofNullable(stompSession)
        .map(AtomicReference::get)
        .map(StompSession::isConnected)
        .orElse(false);
  }

  @Override
  public void triggerListener(RbelElement element, RbelMessageMetadata metadata) {
    if (masterTigerProxy != null) {
      masterTigerProxy.triggerListener(element, metadata);
    } else {
      super.triggerListener(element, metadata);
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

  public void propagateException(RuntimeException e) {
    if (masterTigerProxy != null) {
      masterTigerProxy.propagateException(e);
    } else {
      log.atWarn()
          .addArgument(this::proxyName)
          .log("Exception thrown (isolated TigerRemoteProxyClient instance {})", e);
    }
  }

  private final RingBufferHashMap<String, List<Runnable>> parsingTasksWaitingForUuid =
      new RingBufferHashMap<>(10_000);

  /**
   * The messages that have been fully parsed, but since removed from the message history and that
   * potentially still will have follow-up messages that see one of these messages as their previous
   * message.
   *
   * <p>If a message does not have a follow-up message, it will not be removed from this map until
   * the buffer is full.
   */
  private final RingBufferHashSet<String> removedMessageUuids = new RingBufferHashSet<>(10_000);

  /**
   * When this WebSocket connection started delivering messages. Used for timestamp-based detection
   * of messages from previous proxy instances.
   */
  @Getter
  private final AtomicReference<ZonedDateTime> webSocketConnectionStartTime =
      new AtomicReference<>();

  /**
   * Estimated clock offset ({@code remoteClock - localClock}). Subtract this from remote timestamps
   * to obtain local-equivalent times.
   */
  @Getter private volatile Duration remoteClockOffset = Duration.ZERO;

  private void handleMessageRemovalFromHistory(RbelElement element) {
    if (!element.hasFacet(NextMessageParsedFacet.class)) {
      removedMessageUuids.add(element.getUuid());
    }
  }

  public int getParsingTasksWaitingForAPredecessor() {
    synchronized (parsingTasksWaitingForUuid) {
      return parsingTasksWaitingForUuid.entries().stream()
          .mapToInt(waitingTasks -> waitingTasks.getValue().size())
          .sum();
    }
  }

  private void discardDelayedParsingTasks() {
    synchronized (parsingTasksWaitingForUuid) {
      logDiscardedParsingTasks();
      parsingTasksWaitingForUuid.clear();
      removedMessageUuids.clear();
    }
  }

  private void logDiscardedParsingTasks() {
    val discardedTasks =
        parsingTasksWaitingForUuid.entries().stream()
            .mapToInt(waitingTasks -> waitingTasks.getValue().size())
            .sum();
    if (discardedTasks > 0) {
      log.warn(
          "Discarding {} parsing task(s) from {} which were still waiting for {} predecessor"
              + " message(s). These messages will not be parsed.",
          discardedTasks,
          remoteProxyUrl,
          parsingTasksWaitingForUuid.size());
    }
  }

  public void scheduleAfterMessage(
      String previousMessageUuid, Runnable parseMessageTask, String thisMessageUuid) {
    scheduleAfterMessage(previousMessageUuid, parseMessageTask, thisMessageUuid, null);
  }

  public void scheduleAfterMessage(
      String previousMessageUuid,
      Runnable parseMessageTask,
      String thisMessageUuid,
      @Nullable ZonedDateTime previousMessageTimestamp) {
    synchronized (parsingTasksWaitingForUuid) {
      if (removedMessageUuids.contains(previousMessageUuid)) {
        log.trace(
            "parsing {} immediately, prev {} is already finished and removed",
            thisMessageUuid,
            previousMessageUuid);
        meshHandlerPool.submit(parseMessageTask);
        removedMessageUuids.remove(previousMessageUuid);
        return;
      }

      Optional<RbelElement> previousMessage =
          getRbelLogger().getRbelConverter().findMessageByUuid(previousMessageUuid);
      final Optional<RbelConversionPhase> previousMessageConversionPhase =
          previousMessage.map(RbelElement::getConversionPhase);

      if (previousMessageConversionPhase.map(RbelConversionPhase::isFinished).orElse(false)) {
        log.trace(
            "parsing {} immediately, prev {} has status {}",
            thisMessageUuid,
            previousMessageUuid,
            previousMessageConversionPhase);
        previousMessage.ifPresent(msg -> msg.addFacet(new NextMessageParsedFacet()));
        meshHandlerPool.submit(parseMessageTask);
        return;
      }

      if (previousMessageTimestamp != null
          && previousMessage.isEmpty()
          && scheduleDirectlyAfterOldPreviousMessage(
              parseMessageTask, thisMessageUuid, previousMessageTimestamp)) {
        return;
      }
      log.atTrace()
          .addArgument(thisMessageUuid)
          .addArgument(previousMessageUuid)
          .addArgument(previousMessageConversionPhase)
          .addArgument(parsingTasksWaitingForUuid::size)
          .addArgument(
              () ->
                  parsingTasksWaitingForUuid.entries().stream()
                      .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size())))
          .log(
              "Queueing {} behind {} ({}), currently {} messages waiting ({}) from {}",
              remoteProxyUrl);
      parsingTasksWaitingForUuid
          .getOrPutDefault(previousMessageUuid, LinkedList::new)
          .add(parseMessageTask);
    }
    scheduleReleaseCheck(thisMessageUuid, previousMessageUuid, parseMessageTask);
  }

  private boolean scheduleDirectlyAfterOldPreviousMessage(
      Runnable parseMessageTask, String thisMessageUuid, ZonedDateTime previousMessageTimestamp) {
    // Early detection of "will never arrive" using timestamp heuristic

    ZonedDateTime connectionStart = webSocketConnectionStartTime.get();
    if (connectionStart != null) {
      ZonedDateTime now = ZonedDateTime.now();
      Duration timeSinceConnectionStart = Duration.between(connectionStart, now);
      Duration timeSincePreviousMessage = Duration.between(previousMessageTimestamp, now);

      // Use configurable thresholds from proxy configuration
      Duration gracePeriod =
          Duration.ofMillis(
              (long)
                  (getTigerProxyConfiguration()
                          .getPreviousMessageTimeoutDetectionGracePeriodInSeconds()
                      * 1000));
      Duration timeGapThreshold =
          Duration.ofMillis(
              (long)
                  (getTigerProxyConfiguration()
                          .getPreviousMessageTimeoutDetectionTimeGapThresholdInSeconds()
                      * 1000));
      Duration recentConnectionThreshold =
          Duration.ofMillis(
              (long)
                  (getTigerProxyConfiguration()
                          .getPreviousMessageTimeoutDetectionRecentConnectionThresholdInMinutes()
                      * 60
                      * 1000));

      if (previousMessageTimestamp.isBefore(connectionStart.minus(gracePeriod))
          && (timeSincePreviousMessage.compareTo(timeGapThreshold) > 0
              && timeSinceConnectionStart.compareTo(recentConnectionThreshold) < 0)) {
        log.atDebug()
            .addArgument(thisMessageUuid)
            .addArgument(previousMessageTimestamp)
            .addArgument(connectionStart)
            .addArgument(timeSincePreviousMessage::toMinutes)
            .addArgument(timeSinceConnectionStart::getSeconds)
            .log(
                "parsing {} immediately, prev message timestamp {} predates connection start {} "
                    + "or shows large time gap (prev: {}min ago, connection: {}sec ago)");
        meshHandlerPool.submit(parseMessageTask);
        return true;
      }
    }
    return false;
  }

  private void scheduleReleaseCheck(String messageUuid, String previousMessageUuid, Runnable task) {
    scheduleReleaseCheck(messageUuid, previousMessageUuid, task, previousMessageTimeout());
  }

  private void scheduleReleaseCheck(
      String messageUuid, String previousMessageUuid, Runnable task, Duration delay) {
    if (meshHandlerPool.isShutdown()) {
      return;
    }
    meshHandlerPool.schedule(
        () -> releaseIfPreviousMessageWillNotArrive(messageUuid, previousMessageUuid, task),
        delay.toMillis(),
        TimeUnit.MILLISECONDS);
  }

  private void releaseIfPreviousMessageWillNotArrive(
      String messageUuid, String previousMessageUuid, Runnable task) {
    if (!isWaitingFor(previousMessageUuid, task)) {
      return;
    }
    if (messageAssembler.isPendingRedownload(previousMessageUuid)) {
      scheduleReleaseCheck(messageUuid, previousMessageUuid, task, REDOWNLOAD_RECHECK_INTERVAL);
      return;
    }
    val remainingAssemblyTime = messageAssembler.remainingAssemblyTime(previousMessageUuid);
    val pendingAssembly = remainingAssemblyTime.filter(remaining -> remaining.toMillis() > 0);
    if (pendingAssembly.isPresent()) {
      scheduleReleaseCheck(messageUuid, previousMessageUuid, task, pendingAssembly.get());
      return;
    }
    val assemblyWasAbandoned = remainingAssemblyTime.isPresent();
    if (stopWaitingFor(previousMessageUuid, task, assemblyWasAbandoned)) {
      meshHandlerPool.submit(task);
      logReleaseOfWaitingTask(messageUuid, previousMessageUuid, assemblyWasAbandoned);
    }
  }

  private boolean isWaitingFor(String previousMessageUuid, Runnable task) {
    synchronized (parsingTasksWaitingForUuid) {
      return parsingTasksWaitingForUuid
          .get(previousMessageUuid)
          .map(waitingTasks -> waitingTasks.contains(task))
          .orElse(false);
    }
  }

  private boolean stopWaitingFor(
      String previousMessageUuid, Runnable task, boolean previousMessageAbandoned) {
    synchronized (parsingTasksWaitingForUuid) {
      val waitingTasks = parsingTasksWaitingForUuid.get(previousMessageUuid).orElse(null);
      if (waitingTasks == null || !waitingTasks.contains(task)) {
        return false;
      }
      if (removedMessageUuids.contains(previousMessageUuid)) {
        return false;
      }
      if (!previousMessageAbandoned && isKnownToConverter(previousMessageUuid)) {
        return false;
      }
      removeFromWaitingTasks(previousMessageUuid, task, waitingTasks);
      return true;
    }
  }

  private boolean isKnownToConverter(String messageUuid) {
    return getRbelLogger().getRbelConverter().getKnownMessageUuids().contains(messageUuid);
  }

  private void logReleaseOfWaitingTask(
      String messageUuid, String previousMessageUuid, boolean previousMessageAbandoned) {
    if (previousMessageAbandoned) {
      log.warn(
          "Parsing task for message {} triggered because previous message {} stayed incomplete for"
              + " more than {}.",
          messageUuid,
          previousMessageUuid,
          messageAssembler.getMaximumMessageAge());
    } else {
      log.warn(
          "Parsing task for message {} triggered by timeout after {}. Previous message {} did not"
              + " even arrive partially.",
          messageUuid,
          previousMessageTimeout(),
          previousMessageUuid);
    }
  }

  private Duration previousMessageTimeout() {
    return Duration.ofMillis(
        (long)
            (getTigerProxyConfiguration().getWaitForPreviousMessageBeforeParsingInSeconds()
                * 1000));
  }

  private void removeFromWaitingTasks(String messageUuid, Runnable task, List<Runnable> tasks) {
    synchronized (parsingTasksWaitingForUuid) {
      tasks.remove(task);
      if (tasks.isEmpty()) {
        parsingTasksWaitingForUuid.remove(messageUuid);
      }
    }
  }

  public void signalNewCompletedMessage(RbelElement msg) {
    signalNewCompletedMessage(msg.getUuid());
    msg.getFacet(SingleConnectionParser.SingleConnectionParserMarkerFacet.class)
        .map(SingleConnectionParser.SingleConnectionParserMarkerFacet::getSourceUuids)
        .ifPresent(
            sourceUuids -> {
              List<String> sourceUuidsToSignal =
                  sourceUuids.stream()
                      .distinct()
                      .filter(uuid -> !uuid.equals(msg.getUuid()))
                      .toList();
              if (!sourceUuidsToSignal.isEmpty()) {
                log.atDebug()
                    .addArgument(msg::getUuid)
                    .addArgument(() -> String.join(", ", sourceUuidsToSignal))
                    .log(
                        "Signaling source UUIDs for transformed message effectiveUuid={}"
                            + " sourceUuids=[{}]");
                sourceUuidsToSignal.forEach(this::signalNewCompletedMessage);
              }
            });
  }

  private void signalNewCompletedMessage(String uuid) {
    List<Runnable> parsingTasks = new ArrayList<>();
    synchronized (parsingTasksWaitingForUuid) {
      parsingTasksWaitingForUuid
          .get(uuid)
          .ifPresent(
              waitingParsingTasks -> {
                log.atDebug()
                    .addArgument(uuid)
                    .addArgument(
                        () ->
                            getRbelLogger()
                                .getRbelConverter()
                                .findMessageByUuid(uuid)
                                .map(RbelElement::getConversionPhase))
                    .addArgument(waitingParsingTasks::size)
                    .log(
                        "Signal new completed message {} (Phase {}) from {} - releasing {} queued"
                            + " tasks",
                        remoteProxyUrl);
                if (removedMessageUuids.contains(uuid)) {
                  removedMessageUuids.remove(uuid);
                } else {
                  getRbelLogger()
                      .getRbelConverter()
                      .findMessageByUuid(uuid)
                      .ifPresent(e -> e.addFacet(new NextMessageParsedFacet()));
                }
                parsingTasksWaitingForUuid.remove(uuid);
                parsingTasks.addAll(waitingParsingTasks);
              });
    }
    if (!parsingTasks.isEmpty()) {
      log.trace("Submitting {} parsing tasks after completing {}", parsingTasks.size(), uuid);
      parsingTasks.forEach(meshHandlerPool::submit);
    }
  }

  public void waitForAllParsingTasksToBeFinished() {
    binaryChunksBuffer.waitForAllParsingTasksToBeFinished();
  }

  public static class NextMessageParsedFacet implements RbelFacet {
    // This is a marker facet to indicate that the next message has been parsed
  }
}
