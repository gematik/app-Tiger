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

import de.gematik.rbellogger.RbelMessageHistory;
import de.gematik.rbellogger.data.RbelElement;
import java.util.Collection;
import java.util.Iterator;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.function.Predicate;
import lombok.AllArgsConstructor;
import lombok.NonNull;

@AllArgsConstructor
public class MockHistoryFacade implements RbelMessageHistory.Facade {
  private final NavigableMap<Long, RbelElement> messages;

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
  public Collection<RbelElement> getMessagesAfter(RbelElement element, boolean includeElement) {
    return element
        .getSequenceNumber()
        .map(seqNr -> messages.tailMap(seqNr, includeElement).values())
        .orElseGet(messages::values);
  }

  @Override
  public Collection<RbelElement> getMessages() {
    return messages.values();
  }

  @Override
  public Optional<RbelElement> findLast(Predicate<RbelElement> additionalFilter) {
    return messages.descendingMap().values().stream().filter(additionalFilter).findFirst();
  }
}
