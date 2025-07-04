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
package de.gematik.rbellogger.util;

import de.gematik.rbellogger.RbelConverter;
import de.gematik.rbellogger.data.RbelElement;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.function.Consumer;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@RequiredArgsConstructor
@SuppressWarnings({"java:S6355", "java:S1133", "java:S1123"})
public class RbelMessagesDequeFacade implements Deque<RbelElement> {

  private final Deque<RbelElement> remoteDeque;
  private final RbelConverter rbelConverter;
  @Setter private boolean allowUnparsedMessagesToAppearInFacade = false;

  @Override
  @Deprecated
  public void addFirst(RbelElement o) {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  public void addLast(RbelElement o) {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  public boolean offerFirst(RbelElement o) {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  public boolean offerLast(RbelElement o) {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  public RbelElement removeFirst() {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  public RbelElement removeLast() {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  public RbelElement pollFirst() {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  public RbelElement pollLast() {
    throw new UnsupportedOperationException();
  }

  @Override
  public RbelElement getFirst() {
    final RbelElement result = remoteDeque.getFirst();
    rbelConverter.waitForAllElementsBeforeGivenToBeParsed(result);
    return result;
  }

  @Override
  public RbelElement getLast() {
    final RbelElement result = remoteDeque.getLast();
    rbelConverter.waitForGivenElementToBeParsed(result);
    return result;
  }

  @Override
  public RbelElement peekFirst() {
    final RbelElement result = remoteDeque.peekFirst();
    rbelConverter.waitForAllElementsBeforeGivenToBeParsed(result);
    return result;
  }

  @Override
  public RbelElement peekLast() {
    final RbelElement result = remoteDeque.peekLast();
    rbelConverter.waitForAllElementsBeforeGivenToBeParsed(result);
    return result;
  }

  @Override
  @Deprecated
  public boolean retainAll(Collection c) {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  public boolean removeAll(Collection c) {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  public boolean removeFirstOccurrence(Object o) {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  public boolean removeLastOccurrence(Object o) {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  public boolean add(RbelElement o) {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  public boolean offer(RbelElement o) {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  public RbelElement remove() {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  public RbelElement poll() {
    throw new UnsupportedOperationException();
  }

  @Override
  public RbelElement element() {
    final RbelElement result = remoteDeque.element();
    rbelConverter.waitForAllElementsBeforeGivenToBeParsed(result);
    return result;
  }

  @Override
  public RbelElement peek() {
    final RbelElement result = remoteDeque.peek();
    rbelConverter.waitForAllElementsBeforeGivenToBeParsed(result);
    return result;
  }

  @Override
  @Deprecated
  public boolean addAll(Collection<? extends RbelElement> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  public void clear() {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  public void push(RbelElement o) {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  public RbelElement pop() {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  public boolean remove(Object o) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean containsAll(Collection c) {
    return remoteDeque.containsAll(c);
  }

  @Override
  public boolean contains(Object o) {
    return remoteDeque.contains(o);
  }

  @Override
  public int size() {
    return remoteDeque.size();
  }

  @Override
  public boolean isEmpty() {
    return remoteDeque.isEmpty();
  }

  @Override
  public ImmutableIteratorFacade iterator() {
    return new ImmutableIteratorFacade(remoteDeque.iterator());
  }

  @Override
  public Object[] toArray() {
    return remoteDeque.toArray();
  }

  @Override
  public Object[] toArray(Object[] a) {
    return remoteDeque.toArray(a);
  }

  @Override
  public ImmutableIteratorFacade descendingIterator() {
    return new ImmutableIteratorFacade(remoteDeque.descendingIterator());
  }

  @AllArgsConstructor
  private class ImmutableIteratorFacade implements Iterator<RbelElement> {

    private final Iterator<RbelElement> remoteIterator;

    @Override
    public boolean hasNext() {
      return remoteIterator.hasNext();
    }

    @Override
    public RbelElement next() {
      final RbelElement result = remoteIterator.next();
      if (!allowUnparsedMessagesToAppearInFacade) {
        rbelConverter.waitForGivenElementToBeParsed(result);
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
              rbelConverter.waitForGivenElementToBeParsed(element);
            }
            action.accept(element);
          });
    }
  }
}
