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
package de.gematik.test.tiger.canopy.dns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.gematik.test.tiger.canopy.client.config.MatchType;
import de.gematik.test.tiger.canopy.config.CanopyConfiguration;
import de.gematik.test.tiger.canopy.registry.ProxiedHostEntry;
import de.gematik.test.tiger.canopy.registry.ProxiedHostRegistry;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class ProxyAddressProviderTest {

  private static final String LOOPBACK_PROXY = "http://127.0.0.1:9999";

  private CanopyConfiguration cfg;
  private ProxiedHostRegistry registry;
  private ProxyAddressProvider provider;

  @BeforeEach
  void setUp() {
    cfg = new CanopyConfiguration();
    cfg.setTigerProxyUrl(LOOPBACK_PROXY);
    ApplicationEventPublisher publisher = ev -> {};
    registry = new ProxiedHostRegistry(publisher, cfg);
    provider = new ProxyAddressProvider(cfg, registry);
    provider.initialResolve();
  }

  // ---- extractHost --------------------------------------------------------

  @Test
  void extractHostFromHttpUrl() {
    assertThat(ProxyAddressProvider.extractHost("http://proxy.local:9999/"))
        .isEqualTo("proxy.local");
    assertThat(ProxyAddressProvider.extractHost("https://10.0.0.5:8443")).isEqualTo("10.0.0.5");
  }

  @Test
  void rejectsUrlWithoutHost() {
    assertThatThrownBy(() -> ProxyAddressProvider.extractHost("not a url"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid proxy URL");
  }

  @Test
  void rejectsUrlWithNullHost() {
    // valid URI syntax but no authority/host component
    assertThatThrownBy(() -> ProxyAddressProvider.extractHost("http:///some/path"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid proxy URL")
        .hasRootCauseInstanceOf(IllegalArgumentException.class)
        .rootCause()
        .hasMessageContaining("URL has no host part");
  }

  // ---- normalizeUrl -------------------------------------------------------

  @Test
  void normalizeUrlLowercasesAndStripsTrailingSlash() {
    assertThat(ProxyAddressProvider.normalizeUrl("HTTP://Proxy.Local:9999/"))
        .isEqualTo("http://proxy.local:9999");
    assertThat(ProxyAddressProvider.normalizeUrl("  http://proxy.local:9999  "))
        .isEqualTo("http://proxy.local:9999");
  }

  // ---- currentAddresses / primary -----------------------------------------

  @Test
  void currentAddressesResolvesGlobalUrl() {
    List<InetAddress> addrs = provider.currentAddresses();
    assertThat(addrs)
        .isNotEmpty()
        .allMatch(Inet4Address.class::isInstance)
        .extracting(InetAddress::getHostAddress)
        .contains("127.0.0.1");
  }

  @Test
  void primaryPrefersIpv4() {
    assertThat(provider.primary()).isPresent();
    assertThat(provider.primary().orElseThrow().getHostAddress()).isEqualTo("127.0.0.1");
  }

  @Test
  void primaryEmptyWhenGlobalUrlIsBlank() {
    cfg.setTigerProxyUrl("");
    provider.refresh();
    assertThat(provider.currentAddresses()).isEmpty();
    assertThat(provider.primary()).isEmpty();
  }

  @Test
  void addressesForUrlReturnsEmptyOnNullOrBlank() {
    cfg.setTigerProxyUrl(null);
    provider.refresh();
    assertThat(provider.currentAddresses()).isEmpty();

    cfg.setTigerProxyUrl("   ");
    provider.refresh();
    assertThat(provider.currentAddresses()).isEmpty();
  }

  // ---- addressesFor / primaryFor ------------------------------------------

  @Test
  void addressesForNullEntryFallsBackToGlobal() {
    assertThat(provider.addressesFor(null))
        .extracting(InetAddress::getHostAddress)
        .contains("127.0.0.1");
  }

  @Test
  void addressesForEntryWithoutOverrideUsesGlobal() {
    ProxiedHostEntry e =
        ProxiedHostEntry.builder()
            .host("api.example.com")
            .matchType(MatchType.EXACT)
            .addedAt(Instant.now())
            .build();
    assertThat(provider.addressesFor(e))
        .extracting(InetAddress::getHostAddress)
        .contains("127.0.0.1");
  }

  @Test
  void addressesForEntryWithBlankOverrideUsesGlobal() {
    ProxiedHostEntry e =
        ProxiedHostEntry.builder()
            .host("api.example.com")
            .matchType(MatchType.EXACT)
            .addedAt(Instant.now())
            .tigerProxyUrl("   ")
            .build();
    assertThat(provider.addressesFor(e))
        .extracting(InetAddress::getHostAddress)
        .contains("127.0.0.1");
  }

  @Test
  void addressesForEntryWithOverrideUsesOverride() {
    ProxiedHostEntry e =
        ProxiedHostEntry.builder()
            .host("api.example.com")
            .matchType(MatchType.EXACT)
            .addedAt(Instant.now())
            .tigerProxyUrl("http://127.0.0.2:9999")
            .build();
    assertThat(provider.addressesFor(e))
        .extracting(InetAddress::getHostAddress)
        .containsExactly("127.0.0.2");
    assertThat(provider.primaryFor(e).orElseThrow().getHostAddress()).isEqualTo("127.0.0.2");
  }

  @Test
  void secondLookupHitsCache() {
    // First call populates the cache; second call returns the same cached List instance.
    List<InetAddress> a = provider.currentAddresses();
    List<InetAddress> b = provider.currentAddresses();
    assertThat(b).isSameAs(a);
  }

  // ---- refresh / snapshot -------------------------------------------------

  @Test
  void refreshResolvesPerEntryOverridesAndEvictsStaleKeys() {
    registry.add("a.example.com", MatchType.EXACT, "http://127.0.0.2:9999");
    registry.add("b.example.com", MatchType.EXACT, "http://127.0.0.3:9999");
    // Entry without override must NOT cause any resolution attempts (covers the `continue`).
    registry.add("c.example.com", MatchType.EXACT);
    // Entry with blank override is also skipped — exercises the second branch of the guard.
    registry.add("d.example.com", MatchType.EXACT, "   ");

    provider.refresh();

    assertThat(provider.snapshot().keySet())
        .containsExactlyInAnyOrder(
            ProxyAddressProvider.normalizeUrl(LOOPBACK_PROXY),
            ProxyAddressProvider.normalizeUrl("http://127.0.0.2:9999"),
            ProxyAddressProvider.normalizeUrl("http://127.0.0.3:9999"));

    // Drop one entry; the next refresh evicts its cached resolution.
    registry.remove("a.example.com");
    provider.refresh();
    assertThat(provider.snapshot().keySet())
        .containsExactlyInAnyOrder(
            ProxyAddressProvider.normalizeUrl(LOOPBACK_PROXY),
            ProxyAddressProvider.normalizeUrl("http://127.0.0.3:9999"));
  }

  @Test
  void snapshotReturnsIndependentCopy() {
    provider.currentAddresses();
    var snapshot = provider.snapshot();
    snapshot.clear(); // mutating the snapshot must not affect the live cache
    assertThat(provider.snapshot()).isNotEmpty();
  }

  @Test
  void unresolvableHostYieldsEmptyAddressListAndDoesNotThrow() {
    // RFC 2606 reserves the `.invalid` TLD; resolution is guaranteed to fail.
    cfg.setTigerProxyUrl("http://canopy-test-host.invalid");
    provider.refresh();
    assertThat(provider.currentAddresses()).isEmpty();
    // primary is also empty in this state — exercises the fallback in primaryOf.
    assertThat(provider.primary()).isEmpty();
  }
}
