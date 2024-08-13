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

package de.gematik.rbellogger.util;

import static de.gematik.rbellogger.testutil.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelParsingNotCompleteFacet;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.collections4.IteratorUtils;
import org.junit.jupiter.api.Test;

class RbelMessagesDequeFacadeTest {

  @Test
  @SuppressWarnings("java:S5778")
  void assertChangingOperationsThrowException() {
    Deque<?> immutableFacade = new RbelMessagesDequeFacade(new ArrayDeque<>(), null);
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
    final RbelConverter converter = RbelConverter.builder().build();

    final RbelElement messageOne =
        converter.parseMessage("1".getBytes(), null, null, Optional.of(ZonedDateTime.now()));
    final RbelElement messageTwo =
        converter.parseMessage("2".getBytes(), null, null, Optional.of(ZonedDateTime.now()));
    final RbelElement messageThree =
        converter.parseMessage("3".getBytes(), null, null, Optional.of(ZonedDateTime.now()));

    final RbelElement messageNotInDeque = converter.convertElement("3".getBytes(), null);

    assertThat(converter.getMessageHistoryAsync().getFirst()).hasStringContentEqualTo("1");
    assertThat(converter.getMessageHistoryAsync().getLast()).hasStringContentEqualTo("3");
    assertThat(converter.getMessageHistoryAsync().peek()).hasStringContentEqualTo("1");
    assertThat(converter.getMessageHistoryAsync().peekFirst()).hasStringContentEqualTo("1");
    assertThat(converter.getMessageHistoryAsync().peekLast()).hasStringContentEqualTo("3");
    assertThat(converter.getMessageHistoryAsync().element()).hasStringContentEqualTo("1");
    // we do explicitely test the interface methods here and refactoring would remove those methods
    // invocations !
    assertThat(converter.getMessageHistoryAsync().contains(messageOne)).isTrue();
    assertThat(converter.getMessageHistoryAsync().contains(messageNotInDeque)).isFalse();
    assertThat(
            converter
                .getMessageHistoryAsync()
                .containsAll(Set.of(messageOne, messageTwo, messageThree)))
        .isTrue();
    assertThat(
            converter.getMessageHistoryAsync().containsAll(Set.of(messageThree, messageNotInDeque)))
        .isFalse();
    assertThat(IteratorUtils.toList(converter.getMessageHistoryAsync().iterator()))
        .containsExactly(messageOne, messageTwo, messageThree);
    assertThat(IteratorUtils.toList(converter.getMessageHistoryAsync().descendingIterator()))
        .containsExactly(messageThree, messageTwo, messageOne);
  }

  @Test
  void readingOperationsShouldWaitForUnparsedMessages() throws InterruptedException {
    // prepare a message that is not yet parsed (parsing is complete, but the facet is still
    // lingering)
    final ArrayDeque<RbelElement> arrayDeque = new ArrayDeque<>();
    final RbelConverter converter = RbelConverter.builder().build();
    final RbelElement element =
        converter.parseMessage("2".getBytes(), null, null, Optional.empty());
    element.addFacet(new RbelParsingNotCompleteFacet(converter));
    arrayDeque.add(element);
    Deque<RbelElement> immutableFacade = new RbelMessagesDequeFacade(arrayDeque, converter);
    // The get-operation should block until we manually remove the facet
    final Thread thread = new Thread(immutableFacade::getLast);
    thread.start();
    // Is it still alive?
    assertThat(thread.isAlive()).isTrue();
    element.removeFacetsOfType(RbelParsingNotCompleteFacet.class);
    Thread.sleep(10);
    // Now it should be finished
    await().until(() -> !thread.isAlive());
  }
}
