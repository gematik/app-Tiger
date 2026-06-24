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
package de.gematik.test.tiger.canopy.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.gematik.test.tiger.canopy.client.config.MatchType;
import de.gematik.test.tiger.canopy.config.CanopyConfiguration;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class ProxiedHostRegistryTest {

  private List<RegistryEvent> events;
  private ProxiedHostRegistry registry;

  @BeforeEach
  void setUp() {
    events = new ArrayList<>();
    ApplicationEventPublisher publisher =
        event -> {
          if (event instanceof RegistryEvent re) {
            events.add(re);
          }
        };
    registry = new ProxiedHostRegistry(publisher, new CanopyConfiguration(), Clock.systemUTC());
  }

  @Test
  void normalizesHost() {
    ProxiedHostEntry entry = registry.add("  Example.COM.  ", MatchType.EXACT);
    assertThat(entry.getHost()).isEqualTo("example.com");
    assertThat(registry.lookup("example.com")).isPresent();
    assertThat(registry.lookup("EXAMPLE.com.")).isPresent();
  }

  @Test
  void rejectsBlankHost() {
    assertThatThrownBy(() -> registry.add("  ", MatchType.EXACT))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> registry.add(".", MatchType.EXACT))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void exactMatchDoesNotMatchSubdomain() {
    registry.add("example.com", MatchType.EXACT);
    assertThat(registry.lookup("api.example.com")).isEmpty();
  }

  @Test
  void suffixMatchHitsRootAndSubdomains() {
    registry.add("example.com", MatchType.SUFFIX);
    assertThat(registry.lookup("example.com")).isPresent();
    assertThat(registry.lookup("api.example.com")).isPresent();
    assertThat(registry.lookup("a.b.example.com")).isPresent();
    assertThat(registry.lookup("notexample.com")).isEmpty();
  }

  @Test
  void exactBeatsSuffixWhenBothPresent() {
    ProxiedHostEntry suffix = registry.add("example.com", MatchType.SUFFIX);
    ProxiedHostEntry exact = registry.add("example.com", MatchType.EXACT);
    // Same host name -> exact map keeps the first registration; suffix added separately.
    assertThat(registry.lookup("example.com")).isPresent();
    // The exact-shard hit is returned first (O(1) lookup before suffix scan).
    assertThat(registry.lookup("example.com").get().getMatchType()).isEqualTo(MatchType.EXACT);
    assertThat(suffix.getHost()).isEqualTo(exact.getHost());
  }

  @Test
  void addIsIdempotentAndDoesNotEmitDuplicateEvents() {
    registry.add("example.com", MatchType.EXACT);
    registry.add("example.com", MatchType.EXACT);
    assertThat(events).hasSize(1).first().isInstanceOf(RegistryEvent.HostAddedEvent.class);
  }

  @Test
  void removeEmitsEventAndIsIdempotent() {
    registry.add("example.com", MatchType.EXACT);
    registry.add("foo.bar", MatchType.SUFFIX);
    events.clear();

    assertThat(registry.remove("example.com")).isPresent();
    assertThat(registry.remove("example.com")).isEmpty();
    assertThat(registry.remove("foo.bar")).isPresent();

    assertThat(events).hasSize(2).allMatch(e -> e instanceof RegistryEvent.HostRemovedEvent);
  }

  @Test
  void clearEmitsOneEventPerEntry() {
    registry.add("a.com", MatchType.EXACT);
    registry.add("b.com", MatchType.EXACT);
    registry.add("c.com", MatchType.SUFFIX);
    events.clear();

    registry.clear();

    assertThat(registry.getEntries()).isEmpty();
    assertThat(events).hasSize(3).allMatch(e -> e instanceof RegistryEvent.HostRemovedEvent);
  }

  @Test
  void seedFromConfigurationPopulatesRegistry() {
    CanopyConfiguration config = new CanopyConfiguration();
    CanopyConfiguration.ProxiedHost one = new CanopyConfiguration.ProxiedHost();
    one.setHost("seed.example.com");
    one.setMatchType(MatchType.EXACT);
    CanopyConfiguration.ProxiedHost two = new CanopyConfiguration.ProxiedHost();
    two.setHost("internal");
    two.setMatchType(MatchType.SUFFIX);
    config.setProxiedHosts(List.of(one, two));

    ProxiedHostRegistry seeded = new ProxiedHostRegistry(event -> {}, config, Clock.systemUTC());
    seeded.seedFromConfiguration();
    assertThat(seeded.getEntries()).hasSize(2);
    assertThat(seeded.lookup("seed.example.com")).isPresent();
    assertThat(seeded.lookup("anything.internal")).isPresent();
  }

  @Test
  void addStoresPerEntryTigerProxyUrlOverride() {
    ProxiedHostEntry entry =
        registry.add("pop3.example.com", MatchType.EXACT, "http://pop3-tp:9100");
    assertThat(entry.getTigerProxyUrl()).isEqualTo("http://pop3-tp:9100");
    assertThat(registry.lookup("pop3.example.com"))
        .hasValueSatisfying(e -> assertThat(e.getTigerProxyUrl()).isEqualTo("http://pop3-tp:9100"));
  }

  @Test
  void addNormalisesBlankOverrideToNull() {
    // Blank/empty overrides must collapse to null so the bridge treats them as "use the global".
    assertThat(registry.add("a.example.com", MatchType.EXACT, "   ").getTigerProxyUrl()).isNull();
    assertThat(registry.add("b.example.com", MatchType.EXACT, "").getTigerProxyUrl()).isNull();
    assertThat(registry.add("c.example.com", MatchType.EXACT, null).getTigerProxyUrl()).isNull();
  }

  @Test
  void seedFromConfigurationPropagatesPerEntryOverride() {
    CanopyConfiguration config = new CanopyConfiguration();
    CanopyConfiguration.ProxiedHost pop3 = new CanopyConfiguration.ProxiedHost();
    pop3.setHost("pop3.example.com");
    pop3.setTigerProxyUrl("http://pop3-tp:9100");
    CanopyConfiguration.ProxiedHost smtp = new CanopyConfiguration.ProxiedHost();
    smtp.setHost("smtp.example.com");
    smtp.setTigerProxyUrl("http://smtp-tp:9101");
    CanopyConfiguration.ProxiedHost plain = new CanopyConfiguration.ProxiedHost();
    plain.setHost("api.example.com");
    config.setProxiedHosts(List.of(pop3, smtp, plain));

    ProxiedHostRegistry seeded = new ProxiedHostRegistry(event -> {}, config, Clock.systemUTC());
    seeded.seedFromConfiguration();

    assertThat(seeded.lookup("pop3.example.com"))
        .hasValueSatisfying(e -> assertThat(e.getTigerProxyUrl()).isEqualTo("http://pop3-tp:9100"));
    assertThat(seeded.lookup("smtp.example.com"))
        .hasValueSatisfying(e -> assertThat(e.getTigerProxyUrl()).isEqualTo("http://smtp-tp:9101"));
    assertThat(seeded.lookup("api.example.com"))
        .hasValueSatisfying(e -> assertThat(e.getTigerProxyUrl()).isNull());
  }

  @Test
  void concurrentAddsAreSafe() throws Exception {
    int threads = 16;
    int perThread = 200;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger errors = new AtomicInteger();

    for (int t = 0; t < threads; t++) {
      final int tid = t;
      pool.submit(
          () -> {
            ready.countDown();
            try {
              start.await();
              for (int i = 0; i < perThread; i++) {
                registry.add("h" + ((tid * perThread + i) % 500) + ".example.com", MatchType.EXACT);
              }
            } catch (Exception e) {
              errors.incrementAndGet();
            }
          });
    }
    ready.await();
    start.countDown();
    pool.shutdown();
    assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    assertThat(errors.get()).isZero();
    assertThat(registry.getEntries()).hasSize(500);
  }
}
