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
 */
package de.gematik.rbellogger.util;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.util.ReverseHostnameResolver.ForwardLookup;
import de.gematik.rbellogger.util.ReverseHostnameResolver.ReverseLookup;
import de.gematik.test.tiger.common.config.TigerConfigurationKeys;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ReverseHostnameResolverTest {

  private static final InetAddress CLIENT = ip(172, 18, 0, 12);

  private static final ForwardLookup NOTHING_RESOLVES =
      name -> {
        throw new UnknownHostException(name);
      };

  @AfterEach
  void resetConfiguration() {
    TigerGlobalConfiguration.deleteFromAllSources(
        TigerConfigurationKeys.RBEL_REVERSE_DNS_LOOKUP.getKey());
  }

  private static ReverseHostnameResolver resolver(
      ReverseLookup reverseLookup, ForwardLookup forwardLookup) {
    return new ReverseHostnameResolver(
        reverseLookup, forwardLookup, Duration.ofMinutes(1), Duration.ofSeconds(1));
  }

  @Test
  void dockerNamesShouldBeCutToTheContainerName() {
    final ReverseHostnameResolver resolver =
        resolver(
            address -> Optional.of("popp-client.testhub_default."),
            name -> name.equals("popp-client") ? List.of(CLIENT) : List.of());

    assertThat(resolver.lookup(CLIENT)).contains("popp-client");
  }

  @Test
  void aFullyQualifiedNameShouldBeKeptIfItsFirstLabelDoesNotResolveBack() {
    final ReverseHostnameResolver resolver =
        resolver(address -> Optional.of("dev.mydomain.com."), NOTHING_RESOLVES);

    assertThat(resolver.lookup(CLIENT)).contains("dev.mydomain.com");
  }

  @Test
  void aFullyQualifiedNameShouldBeKeptIfItsFirstLabelResolvesElsewhere() {
    final ReverseHostnameResolver resolver =
        resolver(address -> Optional.of("dev.mydomain.com"), name -> List.of(ip(10, 0, 0, 1)));

    assertThat(resolver.lookup(CLIENT)).contains("dev.mydomain.com");
  }

  @Test
  void resultsShouldBeCachedIncludingFailures() {
    final AtomicInteger lookups = new AtomicInteger();
    final ReverseHostnameResolver resolver =
        resolver(
            address -> {
              lookups.incrementAndGet();
              throw new UnknownHostException("no PTR record");
            },
            NOTHING_RESOLVES);

    assertThat(resolver.lookup(CLIENT)).isEmpty();
    assertThat(resolver.lookup(CLIENT)).isEmpty();
    assertThat(lookups).hasValue(1);
  }

  @Test
  void aSlowLookupShouldNotBeWaitedForButStillFillTheCache() {
    final CountDownLatch release = new CountDownLatch(1);
    final ReverseHostnameResolver resolver =
        new ReverseHostnameResolver(
            address -> {
              awaitQuietly(release);
              return Optional.of("popp-client");
            },
            NOTHING_RESOLVES,
            Duration.ofMinutes(1),
            Duration.ofMillis(50));

    assertThat(resolver.lookup(CLIENT)).isEmpty();
    release.countDown();
    Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> resolver.lookup(CLIENT).isPresent());
  }

  @Test
  void shouldBeActiveByDefaultAndSwitchable() {
    assertThat(ReverseHostnameResolver.isActive()).isTrue();

    TigerGlobalConfiguration.putValue(
        TigerConfigurationKeys.RBEL_REVERSE_DNS_LOOKUP.getKey(), false);

    assertThat(ReverseHostnameResolver.isActive()).isFalse();
  }

  private static void awaitQuietly(CountDownLatch latch) {
    try {
      latch.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static InetAddress ip(int... bytes) {
    try {
      final byte[] address = new byte[bytes.length];
      for (int i = 0; i < bytes.length; i++) {
        address[i] = (byte) bytes[i];
      }
      return InetAddress.getByAddress(address);
    } catch (UnknownHostException e) {
      throw new IllegalStateException(e);
    }
  }
}
