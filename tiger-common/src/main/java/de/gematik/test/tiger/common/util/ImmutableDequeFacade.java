/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.util;

import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.function.Consumer;
import lombok.AllArgsConstructor;

@AllArgsConstructor
@SuppressWarnings({"java:S6355", "java:S1133"})
public class ImmutableDequeFacade<T> implements Deque<T> {

  private final Deque<T> remoteDeque;

  @Override
  @Deprecated
  public void addFirst(T o) {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  public void addLast(T o) {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  public boolean offerFirst(T o) {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  public boolean offerLast(T o) {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  public T removeFirst() {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  public T removeLast() {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  public T pollFirst() {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  public T pollLast() {
    throw new UnsupportedOperationException();
  }

  @Override
  public T getFirst() {
    return remoteDeque.getFirst();
  }

  @Override
  public T getLast() {
    return remoteDeque.getLast();
  }

  @Override
  public T peekFirst() {
    return remoteDeque.peekFirst();
  }

  @Override
  public T peekLast() {
    return remoteDeque.peekLast();
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
  public boolean add(T o) {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  public boolean offer(T o) {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  public T remove() {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  public T poll() {
    throw new UnsupportedOperationException();
  }

  @Override
  public T element() {
    return remoteDeque.element();
  }

  @Override
  public T peek() {
    return remoteDeque.peek();
  }

  @Override
  @Deprecated
  public boolean addAll(Collection<? extends T> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  public void clear() {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  public void push(Object o) {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  public T pop() {
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
  public Iterator<T> iterator() {
    return new ImmutableIteratorFacade<>(remoteDeque.iterator());
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
  public Iterator<T> descendingIterator() {
    return new ImmutableIteratorFacade<>(remoteDeque.descendingIterator());
  }

  @AllArgsConstructor
  private static class ImmutableIteratorFacade<T> implements Iterator<T> {

    private final Iterator<T> remoteIterator;

    @Override
    public boolean hasNext() {
      return remoteIterator.hasNext();
    }

    @Override
    public T next() {
      return remoteIterator.next();
    }

    @Override
    @Deprecated
    public void remove() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void forEachRemaining(Consumer action) {
      remoteIterator.forEachRemaining(action);
    }
  }
}
