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

import de.gematik.test.tiger.canopy.client.config.MatchType;
import de.gematik.test.tiger.canopy.config.CanopyConfiguration;
import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Thread-safe in-memory registry of hostnames whose DNS queries should be answered with the Tiger
 * proxy address.
 *
 * <p>Names are normalized to lower-case and stored without a trailing dot. Two storage shards are
 * kept, both backed by a {@link ConcurrentHashMap}:
 *
 * <ul>
 *   <li>{@code exactEntries} — keyed by FQDN; O(1) exact-match lookup.
 *   <li>{@code suffixEntries} — keyed by FQDN; lookup walks the at-most-{@code labels(query)}
 *       parent suffixes of the query name and probes the map. Still O(labels) but with O(1) hash
 *       lookups and lock-free mutation.
 * </ul>
 *
 * <p>Mutations publish a {@link RegistryEvent} via the Spring {@link ApplicationEventPublisher} so
 * downstream listeners (e.g. the stage-2 Tiger-proxy bridge) can react.
 */
@Slf4j
@Component
public class ProxiedHostRegistry {

  private final ConcurrentMap<String, ProxiedHostEntry> exactEntries = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, ProxiedHostEntry> suffixEntries = new ConcurrentHashMap<>();

  private final ApplicationEventPublisher events;
  private final CanopyConfiguration configuration;
  private final Clock clock;

  @Autowired
  public ProxiedHostRegistry(ApplicationEventPublisher events, CanopyConfiguration configuration) {
    this(events, configuration, Clock.systemUTC());
  }

  /** Test-only constructor allowing a deterministic clock. */
  ProxiedHostRegistry(
      ApplicationEventPublisher events, CanopyConfiguration configuration, Clock clock) {
    this.events = events;
    this.configuration = configuration;
    this.clock = clock;
  }

  @PostConstruct
  void seedFromConfiguration() {
    for (CanopyConfiguration.ProxiedHost init : configuration.getProxiedHosts()) {
      if (init == null || init.getHost() == null || init.getHost().isBlank()) {
        continue;
      }
      add(
          init.getHost(),
          init.getMatchType() == null ? MatchType.EXACT : init.getMatchType(),
          init.getTigerProxyUrl());
    }
  }

  // ---- mutation -----------------------------------------------------------

  /** Convenience overload routing the entry through the global {@code canopy.tigerProxyUrl}. */
  public ProxiedHostEntry add(String host, MatchType matchType) {
    return add(host, matchType, null);
  }

  /**
   * Add (or replace) an entry. Idempotent: re-adding an identical entry is a no-op and does not
   * publish an event. {@code tigerProxyUrl} (when non-blank) overrides the global default for this
   * entry on both DNS resolution and {@code ROUTE_PER_HOST} route registration.
   */
  public ProxiedHostEntry add(String host, MatchType matchType, @Nullable String tigerProxyUrl) {
    String normalized = normalize(host);
    MatchType type = matchType == null ? MatchType.EXACT : matchType;
    String override = (tigerProxyUrl == null || tigerProxyUrl.isBlank()) ? null : tigerProxyUrl;
    ProxiedHostEntry candidate =
        ProxiedHostEntry.builder()
            .host(normalized)
            .matchType(type)
            .addedAt(clock.instant())
            .tigerProxyUrl(override)
            .build();

    ProxiedHostEntry stored;
    boolean fresh;
    ConcurrentMap<String, ProxiedHostEntry> shard =
        (type == MatchType.EXACT) ? exactEntries : suffixEntries;
    ProxiedHostEntry previous = shard.putIfAbsent(normalized, candidate);
    stored = previous == null ? candidate : previous;
    fresh = previous == null;

    if (fresh) {
      log.info("Registered proxied host {} ({})", normalized, type);
      events.publishEvent(new RegistryEvent.HostAddedEvent(stored));
    }
    return stored;
  }

  /** Remove an entry; returns the removed value if present. */
  public Optional<ProxiedHostEntry> remove(String host) {
    String normalized = normalize(host);
    ProxiedHostEntry removed = exactEntries.remove(normalized);
    if (removed == null) {
      removed = suffixEntries.remove(normalized);
    }
    if (removed != null) {
      log.info("Removed proxied host {}", normalized);
      events.publishEvent(new RegistryEvent.HostRemovedEvent(removed));
    }
    return Optional.ofNullable(removed);
  }

  /** Remove all entries; publishes one removal event per entry. */
  public void clear() {
    List<ProxiedHostEntry> snapshot = getEntries();
    exactEntries.clear();
    suffixEntries.clear();
    for (ProxiedHostEntry e : snapshot) {
      events.publishEvent(new RegistryEvent.HostRemovedEvent(e));
    }
  }

  // ---- read ---------------------------------------------------------------

  /**
   * Returns the matching entry for {@code queryName}, if any. Exact matches win over suffix
   * matches; among suffix matches, the longest (most specific) one is returned.
   */
  public Optional<ProxiedHostEntry> lookup(String queryName) {
    String normalized = normalize(queryName);
    return lookupExactEntry(normalized).or(() -> lookupSuffixEntry(normalized));
  }

  private Optional<ProxiedHostEntry> lookupExactEntry(String normalized) {
    return Optional.ofNullable(exactEntries.get(normalized));
  }

  private Optional<ProxiedHostEntry> lookupSuffixEntry(String normalized) {
    if (suffixEntries.isEmpty()) {
      return Optional.empty();
    }
    // Walk parent suffixes of the query and probe the map; longest match wins automatically
    // because we start with the full name and chop off the leftmost label each iteration.
    String candidate = normalized;
    while (!candidate.isEmpty()) {
      ProxiedHostEntry match = suffixEntries.get(candidate);
      if (match != null) {
        return Optional.of(match);
      }
      int dot = candidate.indexOf('.');
      if (dot < 0) {
        break;
      }
      candidate = candidate.substring(dot + 1);
    }
    return Optional.empty();
  }

  /** Snapshot of all entries (exact first, then suffix). */
  public List<ProxiedHostEntry> getEntries() {
    return Stream.concat(exactEntries.values().stream(), suffixEntries.values().stream()).toList();
  }

  // ---- helpers ------------------------------------------------------------

  /** normalizes a hostname: trim, lower-case, strip trailing dot. */
  public static String normalize(@Nullable String host) {
    if (host == null) {
      throw new IllegalArgumentException("host must not be null");
    }
    String trimmed = host.trim().toLowerCase(Locale.ROOT);
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("host must not be blank");
    }
    while (trimmed.endsWith(".")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("host must not be only dots");
    }
    return trimmed;
  }
}
