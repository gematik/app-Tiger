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

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import de.gematik.test.tiger.proxy.exceptions.TigerProxyStartupException;
import de.gematik.test.tiger.proxy.handler.MultipleBinaryConnectionParser;
import de.gematik.test.tiger.proxy.handler.SingleConnectionParser;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import kong.unirest.core.GenericType;
import kong.unirest.core.Unirest;
import lombok.Getter;
import lombok.Setter;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompSession;
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
  @Getter private final String remoteProxyUrl;
  private final WebSocketStompClient tigerProxyStompClient;

  @Getter private final List<TigerExceptionDto> receivedRemoteExceptions = new ArrayList<>();

  @Getter
  private final Map<String, PartialTracingMessage> partiallyReceivedMessageMap =
      new LinkedHashMap<>();

  @Getter private final MultipleBinaryConnectionParser binaryChunksBuffer;

  @Getter private final TigerStompSessionHandler tigerStompSessionHandler;
  @Nullable private final TigerProxy masterTigerProxy;
  @Getter @Setter private Duration maximumPartialMessageAge;
  private final AtomicReference<StompSession> stompSession = new AtomicReference<>();
  @Getter private final AtomicReference<String> lastMessageUuid = new AtomicReference<>();
  private final SockJsClient webSocketClient;
  private final int connectionTimeoutInSeconds;

  @Getter
  private final ScheduledExecutorService meshHandlerPool =
      Executors.newScheduledThreadPool(
          0,
          r -> {
            Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setName(
                "TigerProxyClient" + getName().map(n -> "-" + n).orElse("") + "-" + t.getId());
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
    this.masterTigerProxy = masterTigerProxy;
    this.binaryChunksBuffer =
        new MultipleBinaryConnectionParser(
            masterTigerProxy == null ? this : masterTigerProxy, null);

    WebSocketContainer container = ContainerProvider.getWebSocketContainer();
    var perMessageBufferSize = configuration.getPerMessageBufferSizeInMb() * MB;
    container.setDefaultMaxBinaryMessageBufferSize(perMessageBufferSize);
    container.setDefaultMaxTextMessageBufferSize(perMessageBufferSize);

    final MappingJackson2MessageConverter messageConverter = new MappingJackson2MessageConverter();
    messageConverter.getObjectMapper().registerModule(new JavaTimeModule());

    StandardWebSocketClient wsClient = new StandardWebSocketClient(container);
    webSocketClient = new SockJsClient(List.of(new WebSocketTransport(wsClient)));
    tigerProxyStompClient = new WebSocketStompClient(webSocketClient);
    tigerProxyStompClient.setMessageConverter(messageConverter);
    tigerProxyStompClient.setInboundMessageSizeLimit(
        configuration.getStompClientBufferSizeInMb() * MB);
    tigerStompSessionHandler = new TigerStompSessionHandler(this);
    maximumPartialMessageAge =
        Duration.ofSeconds(configuration.getMaximumPartialMessageAgeInSeconds());
    connectionTimeoutInSeconds = configuration.getConnectionTimeoutInSeconds();

    addRbelMessageListener(this::signalNewCompletedMessage);
    var converter = getRbelLogger().getRbelConverter();
    converter.addConverter(new TigerProxyMessageDeletedPlugin(this));
    converter.addClearHistoryCallback(this::discardDelayedParsingTasks);
    converter.addMessageRemovedFromHistoryCallback(this::handleMessageRemovalFromHistory);
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
    tigerProxyStompClient
        .connectAsync(tracingWebSocketUrl, tigerStompSessionHandler)
        .orTimeout(connectionTimeoutInSeconds, TimeUnit.SECONDS)
        .thenApply(
            stompSessionInCallback -> {
              log.info(
                  "Successfully opened stomp session {} to url {}",
                  stompSessionInCallback.getSessionId(),
                  tracingWebSocketUrl);
              tigerStompSessionHandler.setOnConnectedCallback(
                  () -> {
                    log.info(
                        "Connected to remote proxy at {}, now downloading traffic...",
                        remoteProxyUrl);
                    if (downloadTraffic) {
                      downloadTrafficFromRemoteProxy();
                    }
                    log.info(
                        "Successfully downloaded traffic from remote proxy at {}", remoteProxyUrl);
                  });
              return stompSessionInCallback;
            })
        .thenAccept(stompSession::set)
        .exceptionally(
            throwable -> {
              throw new TigerRemoteProxyClientException(
                  "Exception while opening tracing-connection to " + tracingWebSocketUrl,
                  throwable);
            });
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

    final Optional<Boolean> isInternalOptional =
        Unirest.get(remoteProxyUrl + "/route")
            .asObject(new GenericType<List<TigerProxyRoute>>() {})
            .getBody()
            .stream()
            .filter(route -> StringUtils.equals(route.getId(), routeId))
            .findFirst()
            .map(TigerProxyRoute::isInternalRoute);
    if (isInternalOptional.isEmpty()) {
      return; // route does not exist on remote, delete therefore successful
    }

    if (isInternalOptional.get()) {
      throw new TigerRemoteProxyClientException(
          "Could not delete route with id '" + routeId + "': Is internal route!");
    }

    Unirest.delete(remoteProxyUrl + "/route/" + routeId)
        .asString()
        .ifFailure(
            httpResponse -> {
              throw new TigerRemoteProxyClientException(
                  "Unable to remove route. Got " + httpResponse.getBody());
            });
  }

  @Override
  public void clearAllRoutes() {
    Unirest.get(remoteProxyUrl + "/route")
        .asObject(new GenericType<List<TigerProxyRoute>>() {})
        .getBody()
        .stream()
        .filter(route -> !route.isInternalRoute())
        .map(TigerProxyRoute::getId)
        .forEach(this::removeRoute);
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

  void tryParseMessages(PartialTracingMessage message, Consumer<RbelElement> messagePreProcessor) {
    var messageUuid = message.getTracingDto().getMessageUuid();
    log.trace("Trying to parse message with UUID {}", messageUuid);
    if (getRbelLogger().getRbelConverter().getKnownMessageUuids().add(messageUuid)) {
      getBinaryChunksBuffer()
          .addToBuffer(
              messageUuid,
              message.getSender(),
              message.getReceiver(),
              message.buildCompleteContent().toByteArray(),
              message.getAdditionalInformation(),
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
    meshHandlerPool.shutdownNow();
  }

  void receiveNewMessagePart(TracingMessagePart tracingMessagePart) {
    final PartialTracingMessage tracingMessage =
        retrieveOrInitializePartialMessage(
            tracingMessagePart.getUuid(), PartialTracingMessage.builder().build());

    tracingMessage.addMessagePart(tracingMessagePart);
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
    PartialTracingMessage oldMessage = null;
    synchronized (partiallyReceivedMessageMap) {
      if (partiallyReceivedMessageMap.containsKey(uuid)) {
        oldMessage = partiallyReceivedMessageMap.get(uuid);
      }
      partiallyReceivedMessageMap.put(uuid, partialTracingMessage);
    }
    if (oldMessage != null) {
      partialTracingMessage.addMessageParts(oldMessage);
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
        } else {
          // everything after this is newer than the cutoff, so we can stop here
          break;
        }
      }
    }
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

  private void handleMessageRemovalFromHistory(RbelElement element) {
    if (!element.hasFacet(NextMessageParsedFacet.class)) {
      removedMessageUuids.add(element.getUuid());
    }
  }

  private void discardDelayedParsingTasks() {
    synchronized (parsingTasksWaitingForUuid) {
      parsingTasksWaitingForUuid.clear();
      removedMessageUuids.clear();
    }
  }

  public void scheduleAfterMessage(
      String previousMessageUuid, Runnable parseMessageTask, String thisMessageUuid) {
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
      } else {
        log.atTrace()
            .addArgument(thisMessageUuid)
            .addArgument(previousMessageUuid)
            .addArgument(previousMessageConversionPhase)
            .addArgument(parsingTasksWaitingForUuid::size)
            .addArgument(
                () ->
                    parsingTasksWaitingForUuid.entries().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size())))
            .log("Queueing {} behind {} ({}), currently {} messages waiting ({})");
        parsingTasksWaitingForUuid
            .getOrPutDefault(previousMessageUuid, LinkedList::new)
            .add(parseMessageTask);

        scheduleDirectParsingIfPreviousMessageHasNotEvenPartiallyArrived(
            thisMessageUuid, previousMessageUuid, parseMessageTask);
      }
    }
  }

  private void scheduleDirectParsingIfPreviousMessageHasNotEvenPartiallyArrived(
      String messageUuid, String previousMessageUuid, Runnable task) {
    val parsingTimeoutInSeconds =
        getTigerProxyConfiguration().getWaitForPreviousMessageBeforeParsingInSeconds();
    meshHandlerPool.schedule(
        () -> {
          boolean schedule = false;
          synchronized (parsingTasksWaitingForUuid) {
            val waitingTasks = parsingTasksWaitingForUuid.get(previousMessageUuid).orElse(null);
            if (waitingTasks != null
                && waitingTasks.contains(task)
                && !partiallyReceivedMessageMap.containsKey(previousMessageUuid)) {
              removeFromWaitingTasks(previousMessageUuid, task, waitingTasks);
              schedule = true;
            }
          }
          if (schedule) {
            meshHandlerPool.submit(task);
            log.warn(
                "Parsing task for message {} triggered by timeout after {} seconds. "
                    + "Previous message {} did not even arrive partially.",
                messageUuid,
                parsingTimeoutInSeconds,
                previousMessageUuid);
          }
        },
        (int) (parsingTimeoutInSeconds * 1000),
        TimeUnit.MILLISECONDS);
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
    msg
        .getFacet(SingleConnectionParser.SingleConnectionParserMarkerFacet.class)
        .map(SingleConnectionParser.SingleConnectionParserMarkerFacet::getSourceUuids)
        .stream()
        .flatMap(Collection::stream)
        .distinct()
        .filter(uuid -> !uuid.equals(msg.getUuid()))
        .forEach(this::signalNewCompletedMessage);
  }

  private void signalNewCompletedMessage(String uuid) {
    List<Runnable> parsingTasks = new ArrayList<>();
    log.atDebug()
        .addArgument(uuid)
        .addArgument(
            () ->
                getRbelLogger()
                    .getRbelConverter()
                    .findMessageByUuid(uuid)
                    .map(RbelElement::getConversionPhase))
        .log("Signal new completed message {} (Status is {})");
    synchronized (parsingTasksWaitingForUuid) {
      parsingTasksWaitingForUuid
          .get(uuid)
          .ifPresent(
              waitingParsingTasks -> {
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
    log.trace("Submitting {} parsing tasks after completing {}", parsingTasks.size(), uuid);
    parsingTasks.forEach(meshHandlerPool::submit);
  }

  public void waitForAllParsingTasksToBeFinished() {
    binaryChunksBuffer.waitForAllParsingTasksToBeFinished();
  }

  public static class NextMessageParsedFacet implements RbelFacet {
    // This is a marker facet to indicate that the next message has been parsed
  }
}
