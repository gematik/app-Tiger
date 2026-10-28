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
package de.gematik.test.tiger.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.rbellogger.util.RbelInternetAddress;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class NoProxyUtilsTest {

  @Test
  void nullOrEmptyListShouldUseProxy() throws Exception {
    final InetAddress local = InetAddress.getByName("127.0.0.1");
    assertThat(NoProxyUtils.shouldUseProxyForHost("localhost", local, null)).isTrue();
    assertThat(NoProxyUtils.shouldUseProxyForHost("localhost", local, List.of())).isTrue();
  }

  @Test
  void exactHostNameShouldBypassCaseInsensitive() {
    assertThat(NoProxyUtils.shouldUseProxyForHost("Host.Corp.DE", null, List.of("host.corp.de")))
        .isFalse();
    assertThat(NoProxyUtils.shouldUseProxyForHost("other.corp.de", null, List.of("host.corp.de")))
        .isTrue();
  }

  @Test
  void plainNameShouldAlsoMatchSubdomainsAsBefore() {
    assertThat(NoProxyUtils.shouldUseProxyForHost("x.corp.de", null, List.of("corp.de"))).isFalse();
    assertThat(NoProxyUtils.shouldUseProxyForHost("notcorp.de", null, List.of("corp.de"))).isTrue();
  }

  @Test
  void unresolvableEntryShouldNotStopOtherEntriesFromMatching() throws Exception {
    assertThat(
            NoProxyUtils.shouldUseProxyForHost(
                "localhost",
                InetAddress.getByName("localhost"),
                List.of("notresolvable.invalid", "localhost")))
        .isFalse();
  }

  @Test
  void dotDomainShouldMatchApexAndSubdomains() {
    final List<String> entries = List.of(".corp.de");
    assertThat(NoProxyUtils.shouldUseProxyForHost("corp.de", null, entries)).isFalse();
    assertThat(NoProxyUtils.shouldUseProxyForHost("a.b.corp.de", null, entries)).isFalse();
    assertThat(NoProxyUtils.shouldUseProxyForHost("notcorp.de", null, entries)).isTrue();
  }

  @Test
  void wildcardDomainShouldMatchApexAndSubdomains() {
    final List<String> entries = List.of("*.ti-dienste.de");
    assertThat(NoProxyUtils.shouldUseProxyForHost("ti-dienste.de", null, entries)).isFalse();
    assertThat(NoProxyUtils.shouldUseProxyForHost("konnektor.ti-dienste.de", null, entries))
        .isFalse();
    assertThat(NoProxyUtils.shouldUseProxyForHost("example.com", null, entries)).isTrue();
  }

  @Test
  void ipv4CidrShouldMatchResolvedAddress() throws Exception {
    final List<String> entries = List.of("10.0.0.0/8");
    assertThat(
            NoProxyUtils.shouldUseProxyForHost(
                "tim.example", InetAddress.getByName("10.11.240.47"), entries))
        .isFalse();
    assertThat(
            NoProxyUtils.shouldUseProxyForHost(
                "tim.example", InetAddress.getByName("11.0.0.1"), entries))
        .isTrue();
  }

  @Test
  void ipv6CidrShouldMatchResolvedAddress() throws Exception {
    final List<String> entries = List.of("fd00::/8");
    assertThat(
            NoProxyUtils.shouldUseProxyForHost(
                "target.example", InetAddress.getByName("fd12::1"), entries))
        .isFalse();
    assertThat(
            NoProxyUtils.shouldUseProxyForHost(
                "target.example", InetAddress.getByName("fe80::1"), entries))
        .isTrue();
  }

  @Test
  void ipLiteralEntryShouldMatchResolvedAddressExactly() throws Exception {
    final List<String> entries = List.of("10.11.240.47", "::1");
    assertThat(
            NoProxyUtils.shouldUseProxyForHost(
                "kob.example", InetAddress.getByName("10.11.240.47"), entries))
        .isFalse();
    assertThat(
            NoProxyUtils.shouldUseProxyForHost(
                "kob.example", InetAddress.getByName("::1"), entries))
        .isFalse();
    assertThat(
            NoProxyUtils.shouldUseProxyForHost(
                "kob.example", InetAddress.getByName("10.11.240.48"), entries))
        .isTrue();
  }

  @Test
  void formerGlobCasesFromTheInitializerTestHaveTheirCurrentExpectations() {
    assertThat(NoProxyUtils.shouldUseProxyForHost("EXAMPLE.COM", null, List.of("example.com")))
        .as("host names compare case-insensitively")
        .isFalse();
    assertThat(
            NoProxyUtils.shouldUseProxyForHost(
                "internal.example.com", null, List.of("*.internal.example.com")))
        .as("a wildcard domain also covers its apex")
        .isFalse();
    assertThat(NoProxyUtils.shouldUseProxyForHost("foo.bar", null, List.of("f*")))
        .as("a prefix glob matches")
        .isFalse();
    assertThat(
            NoProxyUtils.shouldUseProxyForHost(
                "service-a.internal.example.com", null, List.of("service-*.internal.example.com")))
        .as("a wildcard in the middle of a name matches, as before")
        .isFalse();
    assertThat(NoProxyUtils.shouldUseProxyForHost("foo.bar", null, List.of("*.baz"))).isTrue();
  }

  @Test
  void prefixAndSuffixGlobsShouldMatchAsInJavasNonProxyHosts() {
    assertThat(NoProxyUtils.shouldUseProxyForHost("10.1.2.3", null, List.of("10.*"))).isFalse();
    assertThat(NoProxyUtils.shouldUseProxyForHost("11.0.0.1", null, List.of("10.*"))).isTrue();
    assertThat(NoProxyUtils.shouldUseProxyForHost("a.corp.de", null, List.of("*corp.de")))
        .isFalse();
    assertThat(NoProxyUtils.shouldUseProxyForHost("a.b.example", null, List.of("a.b.*"))).isFalse();
    assertThat(NoProxyUtils.isReadableNoProxyEntry("10.*")).isTrue();
    assertThat(NoProxyUtils.isReadableNoProxyEntry("a.*.b")).isTrue();
    assertThat(NoProxyUtils.shouldUseProxyForHost("a.x.y.b", null, List.of("a.*.b"))).isFalse();
    assertThat(NoProxyUtils.shouldUseProxyForHost("a.x.y.c", null, List.of("a.*.b"))).isTrue();
  }

  @Test
  void numericGlobShouldAlsoMatchTheResolvedAddressInItsRange() throws Exception {
    assertThat(
            NoProxyUtils.shouldUseProxyForHost(
                "konnektor.ti", InetAddress.getByName("10.1.2.3"), List.of("10.*")))
        .isFalse();
    assertThat(
            NoProxyUtils.shouldUseProxyForHost(
                "konnektor.ti", InetAddress.getByName("11.1.2.3"), List.of("10.*")))
        .isTrue();
    assertThat(
            NoProxyUtils.shouldUseProxyForHost(
                "db.internal", InetAddress.getByName("192.168.5.1"), List.of("192.168.*")))
        .isFalse();
    assertThat(
            NoProxyUtils.shouldUseProxyForHost(
                "db.internal", InetAddress.getByName("192.169.5.1"), List.of("192.168.*")))
        .isTrue();
  }

  @Test
  void numericGlobShouldTriggerTheAddressLookup() throws Exception {
    final InetAddress internal = InetAddress.getByName("10.1.2.3");
    RbelInternetAddress.setHostnameResolver(host -> internal);
    try {
      assertThat(NoProxyUtils.resolveIfNeeded("konnektor.ti", List.of("10.*"))).isEqualTo(internal);
    } finally {
      RbelInternetAddress.resetHostnameResolver();
    }
  }

  @Test
  void resolveIfNeededShouldGoThroughTheSharedDnsCache() throws Exception {
    final AtomicInteger lookups = new AtomicInteger();
    final InetAddress internal = InetAddress.getByName("10.1.2.3");
    RbelInternetAddress.setHostnameResolver(
        host -> {
          lookups.incrementAndGet();
          return internal;
        });
    try {
      final List<String> entries = List.of("10.0.0.0/8");
      assertThat(NoProxyUtils.resolveIfNeeded("cached-host.example", entries)).isEqualTo(internal);
      assertThat(NoProxyUtils.resolveIfNeeded("cached-host.example", entries)).isEqualTo(internal);
      assertThat(lookups).as("the second call is answered by the cache").hasValue(1);
    } finally {
      RbelInternetAddress.resetHostnameResolver();
    }
  }

  @Test
  void resolveIfNeededShouldNotLookUpAHostWhenOnlyNamesAreListed() {
    assertThat(NoProxyUtils.resolveIfNeeded("unresolvable-host.invalid", List.of(".corp.de")))
        .isNull();
  }

  @Test
  void resolveIfNeededShouldLookUpAHostWhenACidrIsListed() throws Exception {
    assertThat(NoProxyUtils.resolveIfNeeded("localhost", List.of("127.0.0.0/8")))
        .isEqualTo(InetAddress.getByName("localhost"));
  }

  @Test
  void starShouldBypassEverything() {
    assertThat(NoProxyUtils.shouldUseProxyForHost("anything.example", null, List.of("*")))
        .isFalse();
  }

  @Test
  void findBypassingNoProxyEntryShouldReturnTheMatchingEntry() {
    assertThat(
            NoProxyUtils.findBypassingNoProxyEntry(
                "konnektor.ti-dienste.de", null, List.of("10.0.0.0/8", "*.ti-dienste.de")))
        .contains("*.ti-dienste.de");
  }

  @Test
  void noReverseLookupShouldBeDoneOnTheResolvedAddress() {
    final InetAddress remoteAddress = mock(InetAddress.class);
    when(remoteAddress.getHostName()).thenReturn("ptr.example.com");

    assertThat(
            NoProxyUtils.shouldUseProxyForHost(
                "target.example.org", remoteAddress, List.of("ptr.example.com")))
        .as("the PTR name must not count as a match")
        .isTrue();

    verify(remoteAddress, never()).getHostName();
  }

  @Test
  void readableEntriesAreAcceptedInEveryFormat() {
    assertThat(NoProxyUtils.isReadableNoProxyEntry("host.corp.de")).isTrue();
    assertThat(NoProxyUtils.isReadableNoProxyEntry(".corp.de")).isTrue();
    assertThat(NoProxyUtils.isReadableNoProxyEntry("*.corp.de")).isTrue();
    assertThat(NoProxyUtils.isReadableNoProxyEntry("10.0.0.0/8")).isTrue();
    assertThat(NoProxyUtils.isReadableNoProxyEntry("fd00::/8")).isTrue();
    assertThat(NoProxyUtils.isReadableNoProxyEntry("10.11.240.47")).isTrue();
    assertThat(NoProxyUtils.isReadableNoProxyEntry("::1")).isTrue();
    assertThat(NoProxyUtils.isReadableNoProxyEntry("*")).isTrue();
  }

  @Test
  void unreadableEntriesAreRejected() {
    assertThat(NoProxyUtils.isReadableNoProxyEntry("bad entry!")).isFalse();
    assertThat(NoProxyUtils.isReadableNoProxyEntry("10.0.0.0/33")).isFalse();
    assertThat(NoProxyUtils.isReadableNoProxyEntry("10.0.0.0/")).isFalse();
    assertThat(NoProxyUtils.isReadableNoProxyEntry("")).isFalse();
    assertThat(NoProxyUtils.isReadableNoProxyEntry(null)).isFalse();
    assertThat(NoProxyUtils.isReadableNoProxyEntry("a".repeat(64) + ".example")).isFalse();
    assertThat(NoProxyUtils.isReadableNoProxyEntry("a.".repeat(127) + "b")).isFalse();
    assertThat(NoProxyUtils.isReadableNoProxyEntry("0:".repeat(23) + "0")).isFalse();
  }
}
