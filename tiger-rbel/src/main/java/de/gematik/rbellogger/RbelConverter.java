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
package de.gematik.rbellogger;

import static de.gematik.rbellogger.RbelConversionPhase.*;
import static de.gematik.rbellogger.util.MemoryConstants.KB;
import static de.gematik.rbellogger.util.MemoryConstants.MB;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.unboundid.util.NotNull;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.converter.brainpool.BrainpoolCurves;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.RbelHostnameFacet;
import de.gematik.rbellogger.data.core.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.data.facet.RbelNonTransmissionMarkerFacet;
import de.gematik.rbellogger.exceptions.RbelConversionException;
import de.gematik.rbellogger.key.RbelKeyManager;
import de.gematik.rbellogger.util.ConverterPluginMap;
import de.gematik.rbellogger.util.RbelMessagesDequeFacade;
import de.gematik.rbellogger.util.RbelValueShader;
import de.gematik.test.tiger.common.config.TigerTypedConfigurationKey;
import de.gematik.test.tiger.common.util.TigerSecurityProviderInitialiser;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PUBLIC, toBuilder = true)
@Slf4j
public class RbelConverter implements RbelConverterInterface {

  private static final String RECEIVER_METADATA_KEY = "receiver";
  private static final String SENDER_METADATA_KEY = "sender";

  static {
    BrainpoolCurves.init();
    TigerSecurityProviderInitialiser.initialize();
  }

  @Getter private final Deque<RbelElement> messageHistory = new ConcurrentLinkedDeque<>();

  @Getter
  private final KnownUuidsContainer knownMessageUuids = new KnownUuidsContainer(messageHistory);

  private final Set<String> messageUuidsWaitingForNextMessage = ConcurrentHashMap.newKeySet();
  private final RbelMultiMap<CompletableFuture<RbelElement>> messagesWaitingForCompletion =
      new RbelMultiMap<>();
  @Getter private final RbelKeyManager rbelKeyManager;
  @Getter private final RbelValueShader rbelValueShader = new RbelValueShader();
  private final List<Runnable> historyClearCallbacks = new LinkedList<>();
  private final List<Consumer<RbelElement>> messageRemovedFromHistoryCallbacks = new LinkedList<>();

  @Getter private final ConverterPluginMap converterPlugins = new ConverterPluginMap();
  private final ExecutorService executorService =
      new ThreadPoolExecutor(
          0,
          Integer.MAX_VALUE,
          60L,
          TimeUnit.SECONDS,
          new SynchronousQueue<>(),
          new ThreadFactoryBuilder().setNameFormat("rbel-converter-thread-%d").build());

  @Builder.Default int rbelBufferSizeInMb = KB;
  @Builder.Default boolean manageBuffer = false;
  @Getter @Builder.Default private long currentBufferSize = 0;
  @Builder.Default long messageSequenceNumber = 0;
  @Builder.Default int skipParsingWhenMessageLargerThanKb = -1;
  @Builder.Default List<String> activateRbelParsingFor = List.of();
  @Builder.Default private volatile boolean shallInitializeConverters = true;
  @Builder.Default @Getter boolean isActivateRbelParsing = true;
  @Builder.Default @Setter @Getter String name = "<>";

  @Builder.Default
  private List<RbelConversionPhase> conversionPhases =
      List.of(PREPARATION, PROTOCOL_PARSING, CONTENT_PARSING, CONTENT_ENRICHMENT, TRANSMISSION);

  public static final TigerTypedConfigurationKey<Integer> RAW_STRING_MAX_TRACE_LENGTH =
      new TigerTypedConfigurationKey<>(
          "tiger.rbel.rawstring.max.trace.length", Integer.class, 1000);

  public void initializeConverters(RbelConfiguration rbelConfiguration) {
    if (shallInitializeConverters) {
      // the outside check is done to avoid the synchronized overhead for most calls
      synchronized (converterPlugins) {
        if (shallInitializeConverters) {
          new RbelConverterInitializer(this, rbelConfiguration, activateRbelParsingFor)
              .addConverters();
          shallInitializeConverters = false;
        }
      }
    }
  }

  public void addClearHistoryCallback(Runnable runnable) {
    historyClearCallbacks.add(runnable);
  }

  public void addMessageRemovedFromHistoryCallback(Consumer<RbelElement> consumer) {
    messageRemovedFromHistoryCallbacks.add(consumer);
  }

  public RbelElement convertElement(RbelElement rbelElement) {
    return convertElement(rbelElement, conversionPhases);
  }

  public RbelElement convertElement(
      RbelElement rbelElement, List<RbelConversionPhase> conversionPhases) {
    return new RbelConversionExecutor(
            this, rbelElement, skipParsingWhenMessageLargerThanKb, rbelKeyManager, conversionPhases)
        .execute();
  }

  public RbelElement convertElement(final byte[] input, RbelElement parentNode) {
    return convertElement(RbelElement.builder().parentNode(parentNode).rawContent(input).build());
  }

  public List<RbelConverterPlugin> getPlugins(Predicate<RbelConverterPlugin> filter) {
    synchronized (converterPlugins) {
      return converterPlugins.stream().filter(filter).toList();
    }
  }

  public RbelElement convertElement(final String input, RbelElement parentNode) {
    return convertElement(
        RbelElement.builder()
            .parentNode(parentNode)
            .rawContent(
                input.getBytes(
                    Optional.ofNullable(parentNode)
                        .map(RbelElement::getElementCharset)
                        .orElse(StandardCharsets.UTF_8)))
            .build());
  }

  public void addConverter(RbelConverterPlugin converter) {
    converterPlugins.put(converter);
  }

  public RbelElement parseMessage(byte[] content, RbelMessageMetadata conversionMetadata) {
    final RbelElement messageElement = RbelElement.builder().rawContent(content).build();
    return parseMessage(messageElement, conversionMetadata);
  }

  public RbelElement parseMessage(
      @NonNull final RbelElement message, @NonNull final RbelMessageMetadata conversionMetadata) {
    try {
      return parseMessageAsync(message, conversionMetadata)
          .exceptionally(
              t -> {
                log.error("Error while parsing message", t);
                return null;
              })
          .get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RbelConversionException(e);
    } catch (ExecutionException e) {
      throw new RbelConversionException(e);
    }
  }

  public CompletableFuture<RbelElement> parseMessageAsync(
      @NonNull final RbelElement messageElement,
      @NonNull final RbelMessageMetadata conversionMetadata) {
    if (messageElement.getContent().isNull()) {
      throw new RbelConversionException("content is empty");
    }
    if (knownMessageUuids.isAlreadyConverted(messageElement.getUuid())) {
      log.atTrace()
          .addArgument(this::getName)
          .addArgument(messageElement::getUuid)
          .log("{} skipping parsing of message with UUID {}: UUID already known");
      return CompletableFuture.failedFuture(new RbelConversionException("UUID is already known"));
    }

    long seqNumber = addMessageToHistoryWithNextSequenceNumber(messageElement);

    // TODO extract into a metadata pre processing plugin
    if (!messageElement.hasFacet(RbelTcpIpMessageFacet.class)) {
      messageElement.addFacet(
          RbelTcpIpMessageFacet.builder()
              .receiver(
                  RbelMessageMetadata.MESSAGE_RECEIVER
                      .getValue(conversionMetadata)
                      .map(h -> RbelHostnameFacet.buildRbelHostnameFacet(messageElement, h))
                      .orElse(RbelHostnameFacet.buildRbelHostnameFacet(messageElement, null)))
              .sender(
                  RbelMessageMetadata.MESSAGE_SENDER
                      .getValue(conversionMetadata)
                      .map(h -> RbelHostnameFacet.buildRbelHostnameFacet(messageElement, h))
                      .orElse(RbelHostnameFacet.buildRbelHostnameFacet(messageElement, null)))
              .sequenceNumber(seqNumber)
              .build());
    }
    messageElement.addFacet(conversionMetadata);

    return CompletableFuture.supplyAsync(() -> convertElement(messageElement), executorService);
  }

  public long addMessageToHistoryWithNextSequenceNumber(RbelElement rbelElement) {
    long seqNumber;
    synchronized (messageHistory) {
      currentBufferSize += rbelElement.getSize();
      knownMessageUuids.markAsConverted(rbelElement.getUuid());
      messageHistory.add(rbelElement);
      seqNumber = messageSequenceNumber++;
    }
    manageRbelBufferSize();
    return seqNumber;
  }

  public void deactivateRbelParsing() {
    isActivateRbelParsing = false;
    conversionPhases = List.of(PREPARATION, CONTENT_ENRICHMENT, TRANSMISSION);
  }

  public void manageRbelBufferSize() {
    if (manageBuffer) {
      synchronized (messageHistory) {
        if (rbelBufferSizeInMb <= 0 && !messageHistory.isEmpty()) {
          currentBufferSize = 0;
          messageHistory.forEach(e -> messageRemovedFromHistoryCallbacks.forEach(h -> h.accept(e)));
          messageHistory.clear();
          knownMessageUuids.clear();
        }
        if (rbelBufferSizeInMb > 0) {
          long exceedingLimit = getExceedingLimit(currentBufferSize);
          if (exceedingLimit > 0) {
            log.atTrace()
                .addArgument(() -> ((double) currentBufferSize / MB))
                .addArgument(rbelBufferSizeInMb)
                .log("Buffer is currently at {} Mb which exceeds the limit of {} Mb");
          }
          while (exceedingLimit > 0 && !messageHistory.isEmpty()) {
            log.trace("Exceeded buffer size, dropping oldest message in history");
            final RbelElement messageToDrop = messageHistory.removeFirst();
            messageRemovedFromHistoryCallbacks.forEach(h -> h.accept(messageToDrop));
            exceedingLimit -= messageToDrop.getSize();
            currentBufferSize -= messageToDrop.getSize();
            knownMessageUuids.remove(messageToDrop.getUuid());
          }
        }
      }
    }
  }

  private long getExceedingLimit(long messageHistorySize) {
    return messageHistorySize - ((long) rbelBufferSizeInMb * MB);
  }

  public Stream<RbelElement> messagesStreamLatestFirst() {
    return StreamSupport.stream(
        Spliterators.spliteratorUnknownSize(
            messageHistory.descendingIterator(), Spliterator.ORDERED),
        false);
  }

  /**
   * Returns a list of all fully parsed messages. This list does not include messages that are not
   * parsed yet. To guarantee consistent sequence numbers the list stops before the first unparsed
   * message.
   */
  public List<RbelElement> getMessageList() {
    synchronized (messageHistory) {
      return messageHistory.stream()
          .filter(e -> !e.hasFacet(RbelNonTransmissionMarkerFacet.class))
          .takeWhile(msg -> msg.getConversionPhase().isFinished())
          .toList();
    }
  }

  public Optional<RbelElement> findMessageByUuid(String uuid) {
    synchronized (messageHistory) {
      return messageHistory.stream().filter(msg -> msg.getUuid().equals(uuid)).findAny();
    }
  }

  /**
   * Gives a view of the current messages. This view includes messages that are not yet fully
   * parsed.
   */
  public RbelMessagesDequeFacade getMessageHistoryAsync() {
    synchronized (messageHistory) {
      return new RbelMessagesDequeFacade(messageHistory, this);
    }
  }

  public void clearAllMessages() {
    synchronized (messageHistory) {
      currentBufferSize = 0;
      messageHistory.clear();
      knownMessageUuids.clear();
      historyClearCallbacks.forEach(Runnable::run);
    }
  }

  public void removeMessage(RbelElement rbelMessage) {
    synchronized (messageHistory) {
      log.trace("Removing message {}", rbelMessage.getUuid());
      final Iterator<RbelElement> iterator = messageHistory.descendingIterator();
      while (iterator.hasNext()) {
        if (iterator.next().equals(rbelMessage)) {
          iterator.remove();
          messageRemovedFromHistoryCallbacks.forEach(r -> r.accept(rbelMessage));
          currentBufferSize -= rbelMessage.getSize();
          knownMessageUuids.remove(rbelMessage.getUuid());
        }
      }
    }
  }

  public void waitForGivenElementToBeParsed(RbelElement result) {
    waitForGivenMessagesToBeParsed(List.of(result));
  }

  public void waitForAllElementsBeforeGivenToBeParsed(RbelElement element) {
    // Collect unfinished messages
    List<RbelElement> unfinishedMessages = new ArrayList<>();
    synchronized (messageHistory) {
      for (RbelElement msg : messageHistory) {
        if (msg == element || msg.getUuid().equals(element.getUuid())) {
          break;
        }
        if (!msg.getConversionPhase().isFinished()) {
          unfinishedMessages.add(msg);
        }
      }
    }
    waitForGivenMessagesToBeParsed(unfinishedMessages);
  }

  public void waitForAllCurrentMessagesToBeParsed() {
    waitForGivenMessagesToBeParsed(new ArrayList<>(messageHistory));
  }

  private void waitForGivenMessagesToBeParsed(List<RbelElement> unfinishedMessages) {
    // register callbacks
    final List<Pair<CompletableFuture<RbelElement>, RbelElement>> callbacks;
    synchronized (messagesWaitingForCompletion) {
      callbacks =
          unfinishedMessages.stream()
              .filter(msg -> msg.getConversionPhase() != RbelConversionPhase.COMPLETED)
              .map(
                  msg -> {
                    final CompletableFuture<RbelElement> future = new CompletableFuture<>();
                    messagesWaitingForCompletion.put(msg.getUuid(), future);
                    return Pair.of(future, msg);
                  })
              .toList();
    }
    // wait for completion
    for (Pair<CompletableFuture<RbelElement>, RbelElement> future : callbacks) {
      try {
        future.getKey().get(100, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RbelConversionException(e, future.getValue());
      } catch (ExecutionException e) {
        throw new RbelConversionException(e, future.getValue());
      } catch (TimeoutException e) {
        throw new RbelConversionException(
            "Tripped the timeout of 100 seconds while waiting for message "
                + future.getValue().getUuid()
                + " to finish parsing",
            e,
            future.getValue());
      }
    }
  }

  void signalMessageParsingIsComplete(RbelElement element) {
    final List<CompletableFuture<RbelElement>> completableFutures;
    synchronized (messagesWaitingForCompletion) {
      completableFutures = messagesWaitingForCompletion.removeAll(element.getUuid());
    }
    completableFutures.forEach(future -> future.complete(element));
  }

  /**
   * Triggers the "transmission" phase on the given element. The element is not stored in the
   * messages list
   *
   * @param rbelElement
   */
  public void transmitElement(RbelElement rbelElement) {
    convertElement(
        rbelElement, List.of(RbelConversionPhase.PREPARATION, RbelConversionPhase.TRANSMISSION));
  }

  private static @NotNull List<String> getTrimmedListElements(String commaSeparatedList) {
    return Arrays.stream(commaSeparatedList.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toList();
  }

  public void deactivateParsingFor(String parsersToDeactivate) {
    List<String> idsToDeactivate = getTrimmedListElements(parsersToDeactivate);
    getConverterPlugins().stream()
        .filter(plugin -> plugin.isParserFor(idsToDeactivate))
        .forEach(RbelConverterPlugin::deactivate);
  }

  public void activateParsingForAll() {
    getConverterPlugins().forEach(RbelConverterPlugin::activate);
  }

  @Deprecated(since = "4.0.0")
  public void reactivateParsingForAll() {
    activateParsingForAll();
  }

  public void deactivateOptionalParsing() {
    getConverterPlugins().stream()
        .filter(RbelConverterPlugin::isOptional)
        .forEach(RbelConverterPlugin::deactivate);
  }

  public void activateParsingFor(String parsersToActivate) {
    List<String> idsToActivate = getTrimmedListElements(parsersToActivate);
    getConverterPlugins().stream()
        .filter(plugin -> plugin.isParserFor(idsToActivate))
        .forEach(RbelConverterPlugin::activate);
  }
}
