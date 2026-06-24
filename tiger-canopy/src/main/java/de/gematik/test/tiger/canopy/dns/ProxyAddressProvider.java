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

import de.gematik.test.tiger.canopy.config.CanopyConfiguration;
import de.gematik.test.tiger.canopy.registry.ProxiedHostEntry;
import de.gematik.test.tiger.canopy.registry.ProxiedHostRegistry;
import jakarta.annotation.PostConstruct;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Resolves the host part of one or more Tiger-proxy URLs into {@link InetAddress InetAddresses}
 * that {@link ResolverChain} returns when a query matches the registry.
 *
 * <p>Two address sources are managed in parallel:
 *
 * <ul>
 *   <li>the global {@code canopy.tigerProxyUrl} from {@link CanopyConfiguration} — the default
 *       answer for entries without a per-host override;
 *   <li>per-host overrides ({@link ProxiedHostEntry#getTigerProxyUrl()}) — cached by URL string.
 * </ul>
 *
 * <p>Resolution is performed on startup and refreshed once a minute so long-running deployments
 * tolerate IP changes of any referenced proxy.
 */
@Slf4j
@Component
public class ProxyAddressProvider {

  private static final long REFRESH_INTERVAL_MS = 60_000L;

  private final CanopyConfiguration configuration;
  private final ProxiedHostRegistry registry;

  /** Cached resolution per normalized URL key — see {@link #normalizeUrl(String)}. */
  private final ConcurrentMap<String, List<InetAddress>> cache = new ConcurrentHashMap<>();

  public ProxyAddressProvider(CanopyConfiguration configuration, ProxiedHostRegistry registry) {
    this.configuration = configuration;
    this.registry = registry;
  }

  @PostConstruct
  void initialResolve() {
    refresh();
  }

  /**
   * Returns the cached address list for the global {@code canopy.tigerProxyUrl}. Kept for callers
   * that don't care about per-host overrides; new callers should prefer {@link
   * #addressesFor(ProxiedHostEntry)}.
   */
  public List<InetAddress> currentAddresses() {
    return addressesForUrl(configuration.getTigerProxyUrl());
  }

  /** Returns one IPv4 address from the global address list if available, otherwise the first. */
  public Optional<InetAddress> primary() {
    return primaryOf(currentAddresses());
  }

  /**
   * Returns the address list this {@link ProxiedHostEntry} should synthesise DNS answers from: the
   * entry's own override if set, otherwise the global default.
   */
  public List<InetAddress> addressesFor(ProxiedHostEntry entry) {
    val tigerProxyUrl = configuration.getTigerProxyUrl();
    String url = entry == null ? tigerProxyUrl : entry.getEffectiveTigerProxyUrl(tigerProxyUrl);
    return addressesForUrl(url);
  }

  /**
   * Returns one IPv4 (or first) address for the entry's effective URL. Convenience wrapper used by
   * the {@link de.gematik.test.tiger.canopy.control.RegistryToProxyBridge} when it needs the proxy
   * IP for a route registration.
   */
  public Optional<InetAddress> primaryFor(ProxiedHostEntry entry) {
    return primaryOf(addressesFor(entry));
  }

  private static Optional<InetAddress> primaryOf(List<InetAddress> snapshot) {
    return snapshot.stream()
        .filter(addr -> addr.getAddress().length == 4)
        .findFirst()
        .or(() -> snapshot.stream().findFirst());
  }

  private List<InetAddress> addressesForUrl(@Nullable String url) {
    if (url == null || url.isBlank()) {
      return List.of();
    }
    String key = normalizeUrl(url);
    List<InetAddress> cached = cache.get(key);
    if (cached != null) {
      return cached;
    }
    return resolveAndCache(url, key);
  }

  /**
   * Re-resolve the global URL and every per-entry override currently in the registry. Stale cache
   * entries (URLs no longer referenced anywhere) are evicted to bound memory. Invoked on startup
   * and once a minute.
   */
  @Scheduled(fixedDelay = REFRESH_INTERVAL_MS, initialDelay = REFRESH_INTERVAL_MS)
  public void refresh() {
    Set<String> stillReferenced = new HashSet<>();
    String global = configuration.getTigerProxyUrl();
    if (global != null && !global.isBlank()) {
      String key = normalizeUrl(global);
      stillReferenced.add(key);
      resolveAndCache(global, key);
    }
    for (ProxiedHostEntry entry : registry.getEntries()) {
      if (!entry.hasTigerProxyUrl()) {
        continue;
      }
      String key = entry.getNormalizedTigerProxyUrl();
      stillReferenced.add(key);
      resolveAndCache(entry.getTigerProxyUrl(), key);
    }
    cache.keySet().removeIf(k -> !stillReferenced.contains(k));
  }

  private List<InetAddress> resolveAndCache(String url, String key) {
    String host = extractHost(url);
    try {
      InetAddress[] resolved = InetAddress.getAllByName(host);
      List<InetAddress> list = List.of(resolved);
      cache.put(key, list);
      log.info("Resolved proxy host '{}' to {}", host, list);
      return list;
    } catch (UnknownHostException e) {
      log.atWarn()
          .addArgument(host)
          .addArgument(e::getMessage)
          .log("Failed to resolve proxy host '{}': {}");
      // Keep any previous value to bridge transient DNS outages.
      return cache.getOrDefault(key, List.of());
    }
  }

  /** Snapshot of all cached (URL → addresses) entries — visible-for-test / diagnostics. */
  public Map<String, List<InetAddress>> snapshot() {
    return new ConcurrentHashMap<>(cache);
  }

  /**
   * Normalises a proxy URL for use as a cache key: lowercases and strips a trailing slash. Keeps
   * the path so distinct admin endpoints on the same host don't collapse.
   */
  static String normalizeUrl(String url) {
    String trimmed = url.trim();
    if (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed.toLowerCase(Locale.ROOT);
  }

  public static String extractHost(String url) {
    try {
      URI uri = URI.create(url);
      String host = uri.getHost();
      if (host == null || host.isBlank()) {
        throw new IllegalArgumentException("URL has no host part: " + url);
      }
      return host;
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid proxy URL '" + url + "'", e);
    }
  }
}
