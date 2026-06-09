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

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.facet.RbelNonTransmissionMarkerFacet;
import de.gematik.rbellogger.facets.timing.RbelMessageTimingFacet;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
public class RbelMessageHistory {

  private final boolean manageBuffer;
  private final int rbelBufferSizeInMb;
  private final NavigableMap<Long, RbelElement> messageHistory = new TreeMap<>();
  private final Map<String, RbelElement> messageByUuid = new HashMap<>();
  private final NavigableMap<Long, RbelElement> unfinishedMessages = new TreeMap<>();
  public static final Comparator<RbelElement> TIMESTAMP_SEQ_COMPARATOR =
      Comparator.<RbelElement, ZonedDateTime>comparing(
              el ->
                  el.getFacet(RbelMessageTimingFacet.class)
                      .map(RbelMessageTimingFacet::getTransmissionTime)
                      .orElse(null),
              Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparingLong(el -> el.getSequenceNumber().orElse(Long.MAX_VALUE));

  private final TreeSet<RbelElement> timestampSortedMessages =
      new TreeSet<>(TIMESTAMP_SEQ_COMPARATOR);
  @Getter private final KnownUuidsContainer knownMessageUuids;
  private final RbelMultiMap<CompletableFuture<RbelElement>> messagesWaitingForCompletion =
      new RbelMultiMap<>();

  private final List<Runnable> historyClearCallbacks = new LinkedList<>();
  private final List<Consumer<RbelElement>> messageRemovedFromHistoryCallbacks = new LinkedList<>();

  private long messageSequenceNumber = 0;

  @Getter private long currentBufferSize = 0;

  public RbelMessageHistory(RbelConverter converter) {
    this.manageBuffer = converter.manageBuffer;
    this.rbelBufferSizeInMb = converter.rbelBufferSizeInMb;
    this.knownMessageUuids = new KnownUuidsContainer(this);
  }

  /**
   * Returns an unmodifiable view of the parsed messages in the message history in ascending order
   * of sequence numbers.
   */
  public MessageHistory getMessageHistory() {
    return new MessageHistoryImpl(false);
  }

  public MessageHistory getMessageHistoryAsync() {
    return new MessageHistoryImpl(true);
  }

  public void addClearHistoryCallback(Runnable runnable) {
    historyClearCallbacks.add(runnable);
  }

  public void addMessageRemovedFromHistoryCallback(Consumer<RbelElement> consumer) {
    messageRemovedFromHistoryCallbacks.add(consumer);
  }

  synchronized void addMessageToHistory(RbelElement rbelElement, ZonedDateTime transmissionTime) {
    long seqNumber = addSequenceNumber(rbelElement);
    currentBufferSize += rbelElement.getSize();
    knownMessageUuids.markAsConverted(rbelElement.getUuid());
    messageHistory.put(seqNumber, rbelElement);
    messageByUuid.put(rbelElement.getUuid(), rbelElement);

    rbelElement.addOrReplaceFacet(
        RbelMessageTimingFacet.builder().transmissionTime(transmissionTime).build());
    timestampSortedMessages.add(rbelElement);

    if (!rbelElement.getConversionPhase().isFinished()) {
      unfinishedMessages.put(seqNumber, rbelElement);
    }
    manageRbelBufferSize();
  }

  private long addSequenceNumber(RbelElement rbelElement) {
    long seqNumber;
    final Optional<Long> existingSequenceNumber = rbelElement.getSequenceNumber();
    if (existingSequenceNumber.isPresent()) {
      if (existingSequenceNumber.get() < messageSequenceNumber) {
        throw new IllegalArgumentException(
            "Element has sequence number "
                + existingSequenceNumber.get()
                + " but expected at least "
                + messageSequenceNumber);
      }
      seqNumber = existingSequenceNumber.get();
      messageSequenceNumber = seqNumber + 1;
    } else {
      seqNumber = messageSequenceNumber++;
    }
    rbelElement.setSequenceNumber(seqNumber);
    return seqNumber;
  }

  public synchronized void manageRbelBufferSize() {
    if (manageBuffer) {
      if (rbelBufferSizeInMb <= 0 && !messageHistory.isEmpty()) {
        currentBufferSize = 0;
        messageHistory
            .values()
            .forEach(e -> messageRemovedFromHistoryCallbacks.forEach(h -> h.accept(e)));
        messageHistory.clear();
        knownMessageUuids.clear();
        messageByUuid.clear();
        unfinishedMessages.clear();
        timestampSortedMessages.clear();
      }
      if (rbelBufferSizeInMb > 0) {
        long exceedingLimit = currentBufferSize - ((long) rbelBufferSizeInMb * 1024 * 1024);
        if (exceedingLimit > 0) {
          log.atTrace()
              .addArgument(() -> ((double) currentBufferSize / (1024 * 1024)))
              .addArgument(rbelBufferSizeInMb)
              .log("Buffer is currently at {} MB which exceeds the limit of {} MB");
        }
        while (exceedingLimit > 0 && !messageHistory.isEmpty()) {
          log.trace("Exceeded buffer size, dropping oldest message in history");
          final RbelElement messageToDrop = messageHistory.pollFirstEntry().getValue();
          messageRemovedFromHistoryCallbacks.forEach(h -> h.accept(messageToDrop));
          exceedingLimit -= messageToDrop.getSize();
          currentBufferSize -= messageToDrop.getSize();
          knownMessageUuids.remove(messageToDrop.getUuid());
          messageByUuid.remove(messageToDrop.getUuid());
          messageToDrop.getSequenceNumber().ifPresent(unfinishedMessages::remove);
          timestampSortedMessages.remove(messageToDrop);
        }
      }
    }
  }

  public Stream<RbelElement> messagesStreamLatestFirst() {
    return messageHistory.descendingMap().values().stream();
  }

  public synchronized Optional<RbelElement> findMessageByUuid(String uuid) {
    return Optional.ofNullable(messageByUuid.get(uuid));
  }

  public Collection<RbelElement> getMessagesNewerThan(String lastMsgUuid) {
    return Optional.ofNullable(lastMsgUuid)
        .filter(uuid -> getKnownMessageUuids().isAlreadyConverted(lastMsgUuid))
        .flatMap(
            uuid ->
                findMessageByUuid(lastMsgUuid)
                    .map(
                        msg ->
                            getMessageHistory()
                                .getMessagesAfter(msg, false, MessageSortOrder.SEQUENCE)))
        .orElseGet(getMessageHistory()::getMessages);
  }

  public synchronized void clearAllMessages() {
    currentBufferSize = 0;
    messageHistory.clear();
    knownMessageUuids.clear();
    messageByUuid.clear();
    unfinishedMessages.clear();
    timestampSortedMessages.clear();
    historyClearCallbacks.forEach(Runnable::run);
  }

  public synchronized void removeMessage(RbelElement rbelMessage) {
    log.trace("Removing message {}", rbelMessage.getUuid());
    rbelMessage
        .getSequenceNumber()
        .ifPresent(
            seq -> {
              if (messageHistory.remove(seq) != null) {
                messageRemovedFromHistoryCallbacks.forEach(r -> r.accept(rbelMessage));
                currentBufferSize -= rbelMessage.getSize();
                knownMessageUuids.remove(rbelMessage.getUuid());
                messageByUuid.remove(rbelMessage.getUuid());
                unfinishedMessages.remove(seq);
                timestampSortedMessages.remove(rbelMessage);
              }
            });
  }

  public void waitForGivenElementToBeParsed(RbelElement result) {
    if (!result.getConversionPhase().isFinished()) {
      waitForGivenMessagesToBeParsed(List.of(result));
    }
  }

  public void waitForAllElementsBeforeGivenToBeParsed(RbelElement element) {
    var seqNumber =
        element
            .getSequenceNumber()
            .or(
                () ->
                    Optional.ofNullable(element.getUuid())
                        .flatMap(this::findMessageByUuid)
                        .flatMap(RbelElement::getSequenceNumber));
    List<RbelElement> messagesToWaitFor;
    synchronized (this) {
      SortedMap<Long, RbelElement> precedingMessages =
          seqNumber.map(unfinishedMessages::headMap).orElse(unfinishedMessages);
      messagesToWaitFor = new ArrayList<>(precedingMessages.values());
    }
    waitForGivenMessagesToBeParsed(messagesToWaitFor);
  }

  public void waitForAllCurrentMessagesToBeParsed() {
    log.atTrace()
        .addArgument(unfinishedMessages.values().stream().map(RbelElement::getUuid)::toList)
        .log("Waiting for all current messages: {}");
    waitForGivenMessagesToBeParsed(new ArrayList<>(unfinishedMessages.values()));
  }

  private void waitForGivenMessagesToBeParsed(List<RbelElement> unfinishedMessagesList) {
    if (unfinishedMessagesList.isEmpty()) {
      return;
    }
    final List<Pair<CompletableFuture<RbelElement>, RbelElement>> callbacks;
    synchronized (messagesWaitingForCompletion) {
      callbacks =
          unfinishedMessagesList.stream()
              .filter(
                  msg -> msg.getConversionPhase() != null && !msg.getConversionPhase().isFinished())
              .map(
                  msg -> {
                    final CompletableFuture<RbelElement> future = new CompletableFuture<>();
                    messagesWaitingForCompletion.put(msg.getUuid(), future);
                    return Pair.of(future, msg);
                  })
              .toList();
    }
    for (Pair<CompletableFuture<RbelElement>, RbelElement> future : callbacks) {
      try {
        future.getKey().get(100, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      } catch (ExecutionException e) {
        throw new RuntimeException(e);
      } catch (TimeoutException e) {
        throw new RuntimeException(
            "Tripped the timeout of 100 seconds while waiting for message "
                + future.getValue().getUuid()
                + " to finish parsing",
            e);
      }
    }
  }

  public void signalMessageParsingIsComplete(RbelElement element) {
    if (element.getParentNode() != null) {
      return;
    }
    element.getSequenceNumber().ifPresent(unfinishedMessages::remove);
    removeFuturesWaitingForCompletionOf(element).forEach(future -> future.complete(element));
  }

  private List<CompletableFuture<RbelElement>> removeFuturesWaitingForCompletionOf(
      RbelElement element) {
    synchronized (messagesWaitingForCompletion) {
      return messagesWaitingForCompletion.removeAll(element.getUuid());
    }
  }

  public synchronized Stream<RbelElement> getPreviousMessages(
      RbelElement targetElement, Predicate<RbelElement> additionalFilter) {
    NavigableMap<Long, RbelElement> reverseHistory = messageHistory.descendingMap();
    return targetElement
        .getSequenceNumber()
        .map(seq -> reverseHistory.tailMap(seq, false))
        .or(() -> Optional.of(reverseHistory))
        .map(Map::values)
        .stream()
        .flatMap(Collection::stream)
        .filter(additionalFilter);
  }

  public synchronized Optional<RbelElement> findPreviousMessage(
      RbelElement targetElement, Predicate<RbelElement> additionalFilter) {
    return getPreviousMessages(targetElement, additionalFilter).findFirst();
  }

  public static List<RbelElement> getLongestFinishedMessagesPrefix(Stream<RbelElement> elements) {
    return elements.takeWhile(e -> e.getConversionPhase().isFinished()).toList();
  }

  public interface MessageHistory {

    RbelElement getFirst();

    RbelElement getLast();

    boolean contains(Object o);

    boolean containsAll(@NonNull Collection<?> c);

    int size();

    boolean isEmpty();

    @NonNull
    Iterator<RbelElement> iterator();

    @NonNull
    Iterator<RbelElement> descendingIterator();

    Object[] toArray();

    Object[] toArray(Object[] a);

    /**
     * Returns the next sequence number that will be handed out by the history (i.e. the same value
     * that backs {@link RbelElement#getSequenceNumber()} for newly registered messages). It is
     * incremented on every successful registration and is <em>not</em> decreased by removals.
     */
    long getMessageSequenceNumber();

    /**
     * Returns the messages located "after" the given anchor in the requested {@link
     * MessageSortOrder}.
     *
     * <p>{@link MessageSortOrder#SEQUENCE} returns the messages in registration order; {@link
     * MessageSortOrder#TIMESTAMP} returns them in ascending transmission-timestamp order, with the
     * sequence number as tie breaker.
     *
     * <p>The anchor does not need to be a (current) member of the history. Its sequence number /
     * (timestamp, sequence number) tuple is used purely as a cutoff via {@code tailMap} / {@code
     * tailSet}, so messages registered after a previously removed anchor are still returned.
     */
    Collection<RbelElement> getMessagesAfter(
        RbelElement element, boolean includeElement, MessageSortOrder sortOrder);

    Collection<RbelElement> getMessages();

    /**
     * Returns the parsed messages in ascending order of sequence numbers (i.e. in the order in
     * which they were registered in the history). The list stops before the first unparsed message
     * to guarantee a consistent prefix.
     */
    List<RbelElement> getMessagesByOrder();

    /**
     * Returns the parsed messages in ascending order of transmission timestamps. Ties are broken by
     * sequence number. The list stops before the first unparsed message, so it might not be stable.
     */
    List<RbelElement> getMessagesByTimestamp();

    /**
     * Returns the parsed messages sorted according to the given {@link MessageSortOrder}.
     *
     * @see #getMessagesByOrder()
     * @see #getMessagesByTimestamp()
     */
    default List<RbelElement> getMessages(MessageSortOrder sortOrder) {
      return sortOrder == MessageSortOrder.TIMESTAMP
          ? getMessagesByTimestamp()
          : getMessagesByOrder();
    }

    Optional<RbelElement> findLast(Predicate<RbelElement> additionalFilter);
  }

  @AllArgsConstructor
  public class MessageHistoryImpl implements MessageHistory {
    private final boolean allowUnparsedMessagesToAppearInFacade;

    @Override
    public RbelElement getFirst() {
      return messageHistory.firstEntry().getValue();
    }

    @Override
    public RbelElement getLast() {
      return Optional.ofNullable(messageHistory.lastEntry())
          .map(Map.Entry::getValue)
          .map(
              element -> {
                if (!allowUnparsedMessagesToAppearInFacade) {
                  waitForAllElementsBeforeGivenToBeParsed(element);
                  waitForGivenElementToBeParsed(element);
                }
                return element;
              })
          .orElseThrow(NoSuchElementException::new);
    }

    @Override
    public boolean contains(Object o) {
      if (o instanceof RbelElement element) {
        waitForGivenElementToBeParsed(element);
        synchronized (RbelMessageHistory.this) {
          return element.getSequenceNumber().map(messageHistory::containsKey).orElse(false);
        }
      }
      return false;
    }

    @Override
    public boolean containsAll(@NonNull Collection<?> c) {
      synchronized (RbelMessageHistory.this) {
        return c.stream().allMatch(this::contains);
      }
    }

    @Override
    public int size() {
      return messageHistory.size();
    }

    @Override
    public boolean isEmpty() {
      return messageHistory.isEmpty();
    }

    @Override
    public @NonNull Iterator<RbelElement> iterator() {
      return new ImmutableIteratorFacade(
          allowUnparsedMessagesToAppearInFacade, messageHistory.values().iterator());
    }

    @Override
    public @NonNull Iterator<RbelElement> descendingIterator() {
      return new ImmutableIteratorFacade(
          allowUnparsedMessagesToAppearInFacade,
          messageHistory.descendingMap().values().iterator());
    }

    @Override
    public Object[] toArray() {
      synchronized (RbelMessageHistory.this) {
        return messageHistory.values().toArray();
      }
    }

    @Override
    public Object[] toArray(Object[] a) {
      synchronized (RbelMessageHistory.this) {
        return messageHistory.values().toArray(a);
      }
    }

    @Override
    public long getMessageSequenceNumber() {
      return messageSequenceNumber;
    }

    @Override
    public Collection<RbelElement> getMessagesAfter(
        RbelElement element, boolean includeElement, MessageSortOrder sortOrder) {
      if (sortOrder != MessageSortOrder.TIMESTAMP) {
        synchronized (RbelMessageHistory.this) {
          var candidates =
              element
                  .getSequenceNumber()
                  .map(seqNr -> messageHistory.tailMap(seqNr, includeElement).values())
                  .orElseGet(messageHistory::values);
          if (!allowUnparsedMessagesToAppearInFacade) {
            candidates = getLongestFinishedMessagesPrefix(candidates.stream());
          }
          return candidates;
        }
      }
      synchronized (RbelMessageHistory.this) {
        Collection<RbelElement> candidates =
            timestampSortedMessages.tailSet(element, includeElement);
        if (!allowUnparsedMessagesToAppearInFacade) {
          candidates = getLongestFinishedMessagesPrefix(candidates.stream());
        }
        return candidates;
      }
    }

    @Override
    public Collection<RbelElement> getMessages() {
      if (!allowUnparsedMessagesToAppearInFacade) {
        waitForAllCurrentMessagesToBeParsed();
      }
      return Collections.unmodifiableCollection(messageHistory.values());
    }

    @Override
    public List<RbelElement> getMessagesByOrder() {
      synchronized (RbelMessageHistory.this) {
        return getLongestFinishedMessagesPrefix(
            messageHistory.values().stream()
                .filter(e -> !e.hasFacet(RbelNonTransmissionMarkerFacet.class)));
      }
    }

    @Override
    public List<RbelElement> getMessagesByTimestamp() {
      synchronized (RbelMessageHistory.this) {
        return getLongestFinishedMessagesPrefix(
            timestampSortedMessages.stream()
                .filter(e -> !e.hasFacet(RbelNonTransmissionMarkerFacet.class)));
      }
    }

    @Override
    public Optional<RbelElement> findLast(Predicate<RbelElement> filter) {
      synchronized (RbelMessageHistory.this) {
        return messageHistory.descendingMap().values().stream().filter(filter).findFirst();
      }
    }
  }

  @AllArgsConstructor
  private class ImmutableIteratorFacade implements Iterator<RbelElement> {

    private final boolean allowUnparsedMessagesToAppearInFacade;
    private final Iterator<RbelElement> remoteIterator;

    @Override
    public boolean hasNext() {
      return remoteIterator.hasNext();
    }

    @Override
    public RbelElement next() {
      final RbelElement result = remoteIterator.next();
      if (!allowUnparsedMessagesToAppearInFacade) {
        waitForGivenElementToBeParsed(result);
      }
      return result;
    }

    @Override
    @Deprecated
    public void remove() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void forEachRemaining(Consumer action) {
      remoteIterator.forEachRemaining(
          element -> {
            if (!allowUnparsedMessagesToAppearInFacade) {
              waitForGivenElementToBeParsed(element);
            }
            action.accept(element);
          });
    }
  }
}
