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

package de.gematik.rbellogger.converter;

import de.gematik.rbellogger.RbelConverterInitializer;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelElementConvertionPair;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.facet.*;
import de.gematik.rbellogger.exceptions.RbelConversionException;
import de.gematik.rbellogger.key.RbelKeyManager;
import de.gematik.rbellogger.util.RbelMessagesDequeFacade;
import de.gematik.test.tiger.common.config.TigerTypedConfigurationKey;
import de.gematik.test.tiger.common.util.TigerSecurityProviderInitialiser;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PUBLIC)
@Slf4j
public class RbelConverter {

  static {
    TigerSecurityProviderInitialiser.initialize();
  }

  private final Deque<RbelElement> messageHistory = new ConcurrentLinkedDeque<>();
  private final Set<String> knownMessageUuids = ConcurrentHashMap.newKeySet();
  private final RbelMultiMap<CompletableFuture<RbelElement>> messagesWaitingForCompletion =
      new RbelMultiMap<>();
  @Getter private final RbelKeyManager rbelKeyManager;
  @Getter private final RbelValueShader rbelValueShader = new RbelValueShader();
  @Getter private final List<RbelConverterPlugin> postConversionListeners = new ArrayList<>();
  private final List<RbelConverterPlugin> converterPlugins = new ArrayList<>();
  @Builder.Default private int rbelBufferSizeInMb = 1024;
  @Builder.Default private boolean manageBuffer = false;
  @Getter @Builder.Default private long currentBufferSize = 0;
  @Builder.Default private long messageSequenceNumber = 0;
  @Builder.Default private int skipParsingWhenMessageLargerThanKb = -1;
  @Builder.Default private List<String> activateRbelParsingFor = List.of();

  @Builder.Default private volatile boolean shallInitializeConverters = true;

  private final AtomicReference<PreviousMessageFacet> lastConvertedMessage =
      new AtomicReference<>();

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

  public RbelElement convertElement(final byte[] input, RbelElement parentNode) {
    return convertElement(RbelElement.builder().parentNode(parentNode).rawContent(input).build());
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

  public RbelElement convertElement(final RbelElement convertedInput) {
    initializeConverters(new RbelConfiguration());
    boolean elementIsOversized =
        skipParsingWhenMessageLargerThanKb > -1
            && (convertedInput.getSize() > skipParsingWhenMessageLargerThanKb * 1024L);
    boolean inputWasIgnoredDueToOversize = false;
    for (RbelConverterPlugin plugin : converterPlugins) {
      if (elementIsOversized && !plugin.ignoreOversize()) {
        inputWasIgnoredDueToOversize = true;
        continue;
      }
      try {
        plugin.consumeElement(convertedInput, this);
      } catch (RuntimeException e) {
        val conversionException =
            RbelConversionException.wrapIfNotAConversionException(e, plugin, convertedInput);
        conversionException.printDetailsToLog(log);
        conversionException.addErrorNoteFacetToElement();
      }
    }
    if (inputWasIgnoredDueToOversize
        && convertedInput.getParentNode() == null
        && convertedInput.getFacets().stream()
            .noneMatch(
                f ->
                    f instanceof RbelRootFacet
                        || f instanceof RbelResponseFacet
                        || f instanceof RbelRequestFacet)) {
      convertedInput.addOrReplaceFacet(new UnparsedChunkFacet());
    }
    return convertedInput;
  }

  public void registerListener(final RbelConverterPlugin listener) {
    postConversionListeners.add(listener);
  }

  public void triggerPostConversionListenerFor(RbelElement element) {
    for (RbelConverterPlugin postConversionListener : postConversionListeners) {
      postConversionListener.consumeElement(element, this);
    }
  }

  public void addConverter(RbelConverterPlugin converter) {
    synchronized (converterPlugins) {
      converterPlugins.add(converter);
    }
  }

  public enum FinishProcessing {
    YES,
    NO
  }

  public RbelElement parseMessage(
      byte[] content,
      RbelHostname sender,
      RbelHostname receiver,
      Optional<ZonedDateTime> transmissionTime) {
    final RbelElement messageElement = RbelElement.builder().rawContent(content).build();
    return parseMessage(
        new RbelElementConvertionPair(messageElement),
        sender,
        receiver,
        transmissionTime,
        FinishProcessing.YES);
  }

  public RbelElement parseMessage(
      @NonNull final RbelElementConvertionPair messagePair,
      final RbelHostname sender,
      final RbelHostname receiver,
      final Optional<ZonedDateTime> transmissionTime) {
    return parseMessage(messagePair, sender, receiver, transmissionTime, FinishProcessing.YES);
  }

  public RbelElement parseMessage(
      @NonNull final RbelElementConvertionPair messagePair,
      final RbelHostname sender,
      final RbelHostname receiver,
      final Optional<ZonedDateTime> transmissionTime,
      FinishProcessing finishProcessing) {
    try {
      return parseMessageAsync(messagePair, sender, receiver, transmissionTime).get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RbelConversionException(e);
    } catch (ExecutionException e) {
      throw new RbelConversionException(e);
    } finally {
      if (finishProcessing == FinishProcessing.YES) {
        setMessageFullyProcessed(messagePair.getMessage());
      }
    }
  }

  public CompletableFuture<RbelElement> parseMessageAsync(
      @NonNull final RbelElementConvertionPair messagePair,
      final RbelHostname sender,
      final RbelHostname receiver,
      final Optional<ZonedDateTime> transmissionTime) {
    final var messageElement = messagePair.getMessage();
    if (messageElement.getContent().isNull()) {
      throw new RbelConversionException("content is empty");
    }
    long seqNumber = addMessageToHistoryWithNextSequenceNumber(messageElement);

    messageElement.addFacet(
        RbelTcpIpMessageFacet.builder()
            .receiver(RbelHostnameFacet.buildRbelHostnameFacet(messageElement, receiver))
            .sender(RbelHostnameFacet.buildRbelHostnameFacet(messageElement, sender))
            .sequenceNumber(seqNumber)
            .build());

    messageElement.addFacet(new RbelParsingNotCompleteFacet(this));
    messagePair
        .getPairedRequest()
        .ifPresent(
            requestFuture ->
                requestFuture.thenAccept(
                    request -> {
                      final var pairFacet =
                          TracingMessagePairFacet.builder()
                              .response(messageElement)
                              .request(request)
                              .build();
                      request.addOrReplaceFacet(pairFacet);
                      messageElement.addOrReplaceFacet(pairFacet);
                    }));

    return CompletableFuture.supplyAsync(
        () -> {
          try {
            convertElement(messageElement);
            doMessagePostConversion(messagePair, transmissionTime);
            return messageElement;
          } catch (Exception e) {
            setMessageFullyProcessed(messageElement);
            throw e;
          } finally {
            messageElement.removeFacetsOfType(RbelParsingNotCompleteFacet.class);
          }
        });
  }

  public RbelElement doMessagePostConversion(
      @NonNull final RbelElementConvertionPair messagePair,
      Optional<ZonedDateTime> transmissionTime) {
    var message = messagePair.getMessage();
    transmissionTime.ifPresent(
        tt -> message.addFacet(RbelMessageTimingFacet.builder().transmissionTime(tt).build()));

    if (message
        .getFacet(RbelHttpResponseFacet.class)
        .map(resp -> resp.getRequest() == null)
        .orElse(false)) {
      final var request = messagePair.getPairedRequest().map(CompletableFuture::join);
      RbelHttpResponseFacet.updateRequestOfResponseFacet(message, request.orElse(null));
      request.ifPresent(req -> RbelHttpRequestFacet.updateResponseOfRequestFacet(req, message));
    }

    message.triggerPostConversionListener(this);
    return message;
  }

  private long addMessageToHistoryWithNextSequenceNumber(RbelElement rbelElement) {
    long seqNumber;
    synchronized (messageHistory) {
      Optional.ofNullable(lastConvertedMessage.getAndSet(new PreviousMessageFacet(rbelElement)))
          .ifPresent(rbelElement::addFacet);
      currentBufferSize += rbelElement.getSize();
      knownMessageUuids.add(rbelElement.getUuid());
      messageHistory.add(rbelElement);
      seqNumber = messageSequenceNumber++;
    }
    manageRbelBufferSize();
    return seqNumber;
  }

  public RbelConverter addLastPostConversionListener(RbelConverterPlugin postConversionListener) {
    postConversionListeners.add(postConversionListener);
    return this;
  }

  public RbelConverter addFirstPostConversionListener(RbelConverterPlugin postConversionListener) {
    postConversionListeners.add(0, postConversionListener);
    return this;
  }

  public void removeAllConverterPlugins() {
    converterPlugins.clear();
  }

  public void manageRbelBufferSize() {
    if (manageBuffer) {
      synchronized (messageHistory) {
        if (rbelBufferSizeInMb <= 0 && !messageHistory.isEmpty()) {
          currentBufferSize = 0;
          messageHistory.clear();
          knownMessageUuids.clear();
        }
        if (rbelBufferSizeInMb > 0) {
          long exceedingLimit = getExceedingLimit(currentBufferSize);
          if (exceedingLimit > 0) {
            log.trace(
                "Buffer is currently at {} Mb which exceeds the limit of {} Mb",
                currentBufferSize / (1024 ^ 2),
                rbelBufferSizeInMb);
          }
          while (exceedingLimit > 0 && !messageHistory.isEmpty()) {
            log.trace("Exceeded buffer size, dropping oldest message in history");
            final RbelElement messageToDrop = messageHistory.removeFirst();
            exceedingLimit -= messageToDrop.getSize();
            currentBufferSize -= messageToDrop.getSize();
            knownMessageUuids.remove(messageToDrop.getUuid());
          }
        }
      }
    }
  }

  private long getExceedingLimit(long messageHistorySize) {
    return messageHistorySize - ((long) rbelBufferSizeInMb * 1024 * 1024);
  }

  public boolean isMessageUuidAlreadyKnown(String msgUuid) {
    return knownMessageUuids.contains(msgUuid);
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
          .takeWhile(msg -> !msg.hasFacet(RbelParsingNotCompleteFacet.class))
          .toList();
    }
  }

  public Optional<RbelElement> findMessageByUuid(String uuid) {
    return getMessageHistoryAsync().stream().filter(msg -> msg.getUuid().equals(uuid)).findAny();
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
    }
  }

  public void removeMessage(RbelElement rbelMessage) {
    synchronized (messageHistory) {
      final Iterator<RbelElement> iterator = messageHistory.descendingIterator();
      while (iterator.hasNext()) {
        if (iterator.next().equals(rbelMessage)) {
          iterator.remove();
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
        if (msg.hasFacet(RbelParsingNotCompleteFacet.class)) {
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
              .filter(msg -> msg.hasFacet(RbelParsingNotCompleteFacet.class))
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
        future.getKey().get();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RbelConversionException(e, future.getValue());
      } catch (ExecutionException e) {
        throw new RbelConversionException(e, future.getValue());
      }
    }
  }

  public void signalMessageParsingIsComplete(RbelElement element) {
    final List<CompletableFuture<RbelElement>> completableFutures;
    synchronized (messagesWaitingForCompletion) {
      completableFutures = messagesWaitingForCompletion.removeAll(element.getUuid());
    }
    completableFutures.forEach(future -> future.complete(element));
  }

  public static void setMessageFullyProcessed(RbelElement element) {
    element
        .getFacet(MessageProcessingStateFacet.class)
        .map(MessageProcessingStateFacet::getProcessed)
        .ifPresent(future -> future.complete(true));
    element.removeFacetsOfType(MessageProcessingStateFacet.class);
  }

  public static void waitUntilFullyProcessed(RbelElement msg) {
    msg.getFacet(MessageProcessingStateFacet.class)
        .map(MessageProcessingStateFacet::getProcessed)
        .ifPresent(CompletableFuture::join);
  }
}
