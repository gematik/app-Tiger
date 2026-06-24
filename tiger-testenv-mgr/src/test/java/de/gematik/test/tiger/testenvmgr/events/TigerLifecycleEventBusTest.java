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

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.servers.AbstractTigerServer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for the lifecycle event bus SPI. Verifies:
 *
 * <ul>
 *   <li>exact-class dispatch (no supertype fanout — sealed-interface contract),
 *   <li>multiple subscribers per event type in subscription order,
 *   <li>exception isolation between subscribers,
 *   <li>{@link BeforeContainerStartEvent} mutability semantics (prepend DNS, idempotent add,
 *       env-var override).
 * </ul>
 */
class TigerLifecycleEventBusTest {

  static class TestServer extends AbstractTigerServer {
    TestServer(String id) {
      super(id, new CfgServer().setType("test"), null);
    }

    @Override
    public void performStartup() {
      // do nothing
    }

    @Override
    public void shutdown() {
      // do nothing
    }
  }

  @Test
  void dispatchesToSubscribersOfExactEventType() {
    var bus = new TigerLifecycleEventBus();
    var seen = new ArrayList<String>();
    bus.subscribe(
        BeforeServerStartEvent.class, e -> seen.add("before:" + e.server().getServerId()));
    bus.subscribe(AfterServerStartEvent.class, e -> seen.add("after:" + e.server().getServerId()));

    var srv = new TestServer("s1");
    bus.publish(new BeforeServerStartEvent(srv));
    bus.publish(new AfterServerStartEvent(srv));

    assertThat(seen).containsExactly("before:s1", "after:s1");
  }

  @Test
  void multipleSubscribersInvokedInSubscriptionOrder() {
    var bus = new TigerLifecycleEventBus();
    var seq = new ArrayList<Integer>();
    bus.subscribe(BeforeServerStartEvent.class, e -> seq.add(1));
    bus.subscribe(BeforeServerStartEvent.class, e -> seq.add(2));
    bus.subscribe(BeforeServerStartEvent.class, e -> seq.add(3));

    bus.publish(new BeforeServerStartEvent(new TestServer("x")));

    assertThat(seq).containsExactly(1, 2, 3);
  }

  @Test
  void exceptionInOneSubscriberDoesNotPreventOthersFromRunning() {
    var bus = new TigerLifecycleEventBus();
    var calls = new AtomicInteger();
    bus.subscribe(BeforeServerStartEvent.class, e -> calls.incrementAndGet());
    bus.subscribe(
        BeforeServerStartEvent.class,
        e -> {
          throw new RuntimeException("oh no");
        });
    bus.subscribe(BeforeServerStartEvent.class, e -> calls.incrementAndGet());

    bus.publish(new BeforeServerStartEvent(new TestServer("x")));

    assertThat(calls.get()).isEqualTo(2);
  }

  @Test
  void publishWithNoSubscribersIsANoOp() {
    var bus = new TigerLifecycleEventBus();
    Assertions.assertDoesNotThrow(
        () -> bus.publish(new BeforeServerStartEvent(new TestServer("x"))));
  }

  @Test
  void beforeContainerStartEventCarrierIsMutableAndIdempotent() {
    var srv = new TestServer("x");
    var evt =
        new BeforeContainerStartEvent(
            srv,
            new ArrayList<>(List.of("1.1.1.1")),
            new ArrayList<>(),
            new java.util.LinkedHashMap<>(Map.of("FOO", "bar")));

    // prependDnsServer moves to front
    evt.prependDnsServer("9.9.9.9");
    assertThat(evt.getDnsServers()).containsExactly("9.9.9.9", "1.1.1.1");

    // prependDnsServer is idempotent when already first
    evt.prependDnsServer("9.9.9.9");
    assertThat(evt.getDnsServers()).containsExactly("9.9.9.9", "1.1.1.1");

    // re-prepending an existing-but-not-first entry rotates it to front
    evt.prependDnsServer("1.1.1.1");
    assertThat(evt.getDnsServers()).containsExactly("1.1.1.1", "9.9.9.9");

    // network add idempotent
    evt.addNetwork("net-a");
    evt.addNetwork("net-a");
    evt.addNetwork("net-b");
    assertThat(evt.getNetworks()).containsExactly("net-a", "net-b");

    // env-var override
    evt.addEnvVar("FOO", "baz");
    evt.addEnvVar("BAZ", "qux");
    assertThat(evt.getExtraEnv()).containsExactly(Map.entry("FOO", "baz"), Map.entry("BAZ", "qux"));
  }

  @Test
  void containerEventDispatchesIndependentlyFromPureLifecycleEvents() {
    var bus = new TigerLifecycleEventBus();
    var beforeServerSeen = new AtomicInteger();
    var beforeContainerSeen = new AtomicInteger();
    bus.subscribe(BeforeServerStartEvent.class, e -> beforeServerSeen.incrementAndGet());
    bus.subscribe(BeforeContainerStartEvent.class, e -> beforeContainerSeen.incrementAndGet());

    bus.publish(new BeforeServerStartEvent(new TestServer("x")));
    // BeforeServerStartEvent must NOT fan out to BeforeContainerStartEvent subscribers
    assertThat(beforeContainerSeen.get()).isZero();

    bus.publish(new BeforeContainerStartEvent(new TestServer("x"), List.of(), List.of(), Map.of()));
    assertThat(beforeServerSeen.get()).isEqualTo(1);
    assertThat(beforeContainerSeen.get()).isEqualTo(1);
  }
}
