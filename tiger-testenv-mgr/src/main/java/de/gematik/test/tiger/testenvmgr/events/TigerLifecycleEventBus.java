/*
 *
 * Copyright 2021-2026 gematik GmbH
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
 *
 */
package de.gematik.test.tiger.testenvmgr.events;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * In-process, in-house pub/sub for {@link TigerLifecycleEvent}s. Listeners run synchronously on the
 * publish thread in subscription order. Exceptions thrown by a listener are caught, logged and
 * swallowed so that one misbehaving subscriber cannot break unrelated server startups.
 *
 * <p>Intentionally <em>not</em> built on Spring's {@code ApplicationEventPublisher}: Tiger runs
 * outside a Spring context in CLI mode, and the SPI surface should not force out-of-tree extensions
 * to drag in Spring just to subscribe to a lifecycle hook.
 *
 * <p>Reachable through {@code TigerTestEnvMgr#getLifecycleEventBus()}.
 *
 * <p>See {@code doc/adr/canopy-extension-repo-extraction.md} for the SPI rationale.
 */
@Slf4j
public class TigerLifecycleEventBus {

  private final Map<
          Class<? extends TigerLifecycleEvent>, List<Consumer<? extends TigerLifecycleEvent>>>
      subscribers = new ConcurrentHashMap<>();

  /**
   * Register {@code handler} to be invoked synchronously for every published event of type {@code
   * eventType} (exact-class match — sealed-interface dispatch, no supertype fanout).
   */
  public <E extends TigerLifecycleEvent> void subscribe(Class<E> eventType, Consumer<E> handler) {
    subscribers.computeIfAbsent(eventType, k -> new ArrayList<>()).add(handler);
  }

  /**
   * Publish {@code event} to all subscribers registered for its exact class. Listener exceptions
   * are logged and discarded; subsequent listeners still receive the event.
   */
  @SuppressWarnings("unchecked")
  public <E extends TigerLifecycleEvent> void publish(E event) {
    List<Consumer<? extends TigerLifecycleEvent>> handlers = subscribers.get(event.getClass());
    if (handlers == null) {
      return;
    }
    // Snapshot to tolerate concurrent subscription during iteration.
    List<Consumer<? extends TigerLifecycleEvent>> snapshot;
    synchronized (handlers) {
      snapshot = new ArrayList<>(handlers);
    }
    for (Consumer<? extends TigerLifecycleEvent> handler : snapshot) {
      try {
        ((Consumer<E>) handler).accept(event);
      } catch (RuntimeException ex) {
        log.warn(
            "Lifecycle subscriber for {} threw {}: {}",
            event.getClass().getSimpleName(),
            ex.getClass().getSimpleName(),
            ex.getMessage(),
            ex);
      }
    }
  }
}
