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
package de.gematik.test.tiger.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ObservableQueueTest {

  @Test
  void shouldNotifyOnMutatingOperations() {
    final AtomicInteger updates = new AtomicInteger(0);
    final ObservableQueue<Integer> queue = new ObservableQueue<>(updates::incrementAndGet);

    queue.add(1);
    queue.addAll(List.of(2, 3));
    queue.remove(2);
    queue.retainAll(List.of(1));
    queue.poll();

    assertThat(updates.get()).isEqualTo(5);
  }

  @Test
  void shouldNotNotifyWhenCollectionIsNotChanged() {
    final AtomicInteger updates = new AtomicInteger(0);
    final ObservableQueue<String> queue = new ObservableQueue<>(updates::incrementAndGet);

    queue.remove("missing");
    queue.removeAll(List.of("missing"));
    queue.retainAll(List.of("missing"));
    queue.clear();
    queue.poll();
    queue.addAll(List.of());

    assertThat(updates.get()).isZero();
  }
}
