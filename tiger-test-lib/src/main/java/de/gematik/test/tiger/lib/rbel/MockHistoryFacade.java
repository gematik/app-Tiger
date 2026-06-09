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
package de.gematik.test.tiger.lib.rbel;

import de.gematik.rbellogger.MessageSortOrder;
import de.gematik.rbellogger.RbelMessageHistory;
import de.gematik.rbellogger.data.RbelElement;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Predicate;
import lombok.NonNull;

public class MockHistoryFacade implements RbelMessageHistory.MessageHistory {
  private final NavigableMap<Long, RbelElement> messages;
  private final NavigableSet<RbelElement> timestampSortedMessages;

  /**
   * Creates a facade that provides both a sequence-based and a timestamp-based view of the given
   * messages. The timestamp-based view is derived from the sequence-keyed map using the standard
   * {@link RbelMessageHistory#TIMESTAMP_SEQ_COMPARATOR}.
   */
  public MockHistoryFacade(NavigableMap<Long, RbelElement> messages) {
    this(messages, deriveTimestampSortedMessages(messages));
  }

  /**
   * Creates a facade that exposes the given sequence-keyed map and the given timestamp-sorted set
   * verbatim. Both collections are expected to contain the same elements; the caller is responsible
   * for keeping them in sync.
   */
  public MockHistoryFacade(
      NavigableMap<Long, RbelElement> messages, NavigableSet<RbelElement> timestampSortedMessages) {
    this.messages = messages;
    this.timestampSortedMessages = timestampSortedMessages;
  }

  private static NavigableSet<RbelElement> deriveTimestampSortedMessages(
      NavigableMap<Long, RbelElement> messages) {
    NavigableSet<RbelElement> set = new TreeSet<>(RbelMessageHistory.TIMESTAMP_SEQ_COMPARATOR);
    set.addAll(messages.values());
    return set;
  }

  @Override
  public RbelElement getFirst() {
    return messages.firstEntry().getValue();
  }

  @Override
  public RbelElement getLast() {
    return messages.lastEntry().getValue();
  }

  @Override
  public boolean contains(Object o) {
    return messages.values().contains(o);
  }

  @Override
  public boolean containsAll(@NonNull Collection<?> c) {
    return messages.values().containsAll(c);
  }

  @Override
  public int size() {
    return messages.size();
  }

  @Override
  public boolean isEmpty() {
    return messages.isEmpty();
  }

  @Override
  public @NonNull Iterator<RbelElement> iterator() {
    return messages.values().iterator();
  }

  @Override
  public @NonNull Iterator<RbelElement> descendingIterator() {
    return messages.descendingMap().values().iterator();
  }

  @Override
  public Object[] toArray() {
    return messages.values().toArray();
  }

  @Override
  public Object[] toArray(Object[] a) {
    return messages.values().toArray(a);
  }

  @Override
  public long getMessageSequenceNumber() {
    // The mock has no real sequence-number generator; the largest known sequence number + 1 is
    // the closest analogue (the value the next "real" registration would receive).
    return messages.isEmpty() ? 0L : messages.lastKey() + 1L;
  }

  @Override
  public Collection<RbelElement> getMessagesAfter(
      RbelElement element, boolean includeElement, MessageSortOrder sortOrder) {
    if (sortOrder == MessageSortOrder.TIMESTAMP) {
      return RbelMessageHistory.getLongestFinishedMessagesPrefix(
          timestampSortedMessages.tailSet(element, includeElement).stream());
    }
    var candidates =
        element
            .getSequenceNumber()
            .map(seqNr -> messages.tailMap(seqNr, includeElement).values())
            .orElseGet(messages::values);
    return RbelMessageHistory.getLongestFinishedMessagesPrefix(candidates.stream());
  }

  @Override
  public Collection<RbelElement> getMessages() {
    return getMessagesByOrder();
  }

  @Override
  public List<RbelElement> getMessagesByOrder() {
    return RbelMessageHistory.getLongestFinishedMessagesPrefix(messages.values().stream());
  }

  @Override
  public List<RbelElement> getMessagesByTimestamp() {
    return RbelMessageHistory.getLongestFinishedMessagesPrefix(timestampSortedMessages.stream());
  }

  @Override
  public Optional<RbelElement> findLast(Predicate<RbelElement> additionalFilter) {
    return messages.descendingMap().values().stream().filter(additionalFilter).findFirst();
  }
}
