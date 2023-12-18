/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import org.apache.commons.collections.IteratorUtils;
import org.junit.jupiter.api.Test;

class ImmutableDequeFacadeTest {

  @Test
  @SuppressWarnings("java:S5778")
  void assertChangingOperationsThrowException() {
    Deque immutableFacade = new ImmutableDequeFacade(new ArrayDeque());
    assertThatThrownBy(immutableFacade::remove).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> immutableFacade.add(null))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> immutableFacade.addFirst(null))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> immutableFacade.addLast(null))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> immutableFacade.addAll(null))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> immutableFacade.offer(null))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> immutableFacade.offerFirst(null))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> immutableFacade.offerLast(null))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(immutableFacade::remove).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> immutableFacade.remove(null))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(immutableFacade::removeFirst)
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(immutableFacade::removeLast)
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(immutableFacade::poll).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(immutableFacade::pollFirst)
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(immutableFacade::pollLast).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> immutableFacade.retainAll(null))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> immutableFacade.removeAll(null))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> immutableFacade.removeFirstOccurrence(null))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> immutableFacade.removeLastOccurrence(null))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(immutableFacade::clear).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> immutableFacade.push(null))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(immutableFacade::pop).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> immutableFacade.iterator().remove())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @SuppressWarnings("java:S5838")
  void testReadingOperations() {
    final ArrayDeque<Integer> arrayDeque = new ArrayDeque<>();
    Deque<Integer> immutableFacade = new ImmutableDequeFacade<>(arrayDeque);
    arrayDeque.add(1);
    arrayDeque.add(2);
    arrayDeque.add(3);
    assertThat(immutableFacade.getFirst()).isEqualTo(1);
    assertThat(immutableFacade.getLast()).isEqualTo(3);
    assertThat(immutableFacade.peek()).isEqualTo(1);
    assertThat(immutableFacade.peekFirst()).isEqualTo(1);
    assertThat(immutableFacade.peekLast()).isEqualTo(3);
    assertThat(immutableFacade.element()).isEqualTo(1);
    // we do explicitely test the interface methods here and refactoring would remove those methods
    // invocations !
    assertThat(immutableFacade.contains(1)).isTrue();
    assertThat(immutableFacade.contains(4)).isFalse();
    assertThat(immutableFacade.containsAll(Set.of(1, 3, 2))).isTrue();
    assertThat(immutableFacade.containsAll(Set.of(3, 4))).isFalse();
    assertThat(IteratorUtils.toList(immutableFacade.iterator())).containsExactly(1, 2, 3);
    assertThat(IteratorUtils.toList(immutableFacade.descendingIterator())).containsExactly(3, 2, 1);
  }
}
