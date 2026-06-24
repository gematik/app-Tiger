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
import java.time.Instant;
import java.util.Locale;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * One entry of the {@link ProxiedHostRegistry}. The {@link #host} is always stored normalized
 * (lower-case, trailing dot removed).
 */
@Getter
@ToString
@EqualsAndHashCode(of = "host")
@Builder
public class ProxiedHostEntry {

  /** normalized hostname (lower-case, no trailing dot). Identifies the entry. */
  private final String host;

  /** How {@link #host} is matched against incoming queries. */
  private final MatchType matchType;

  /** Wall-clock instant the entry was added to the registry. */
  private final Instant addedAt;

  /**
   * Optional per-entry override of {@code canopy.tigerProxyUrl}. {@code null} means "use the global
   * default". When non-null, DNS answers for this entry resolve <em>this</em> URL's host and, in
   * {@code ROUTE_PER_HOST} mode, the route is registered on this proxy instead.
   */
  private final String tigerProxyUrl;

  /**
   * Checks whether this entry has a per-host Tiger proxy URL override.
   *
   * @return {@code true} if {@link #tigerProxyUrl} is set and non-blank
   */
  public boolean hasTigerProxyUrl() {
    return tigerProxyUrl != null && !tigerProxyUrl.isBlank();
  }

  /**
   * Returns the normalized form of the {@link #tigerProxyUrl} for use as a cache key (lowercase,
   * trailing slash removed). Normalisation keeps the path so distinct admin endpoints on the same
   * host don't collapse.
   *
   * @return normalized URL string
   * @throws IllegalStateException if {@link #hasTigerProxyUrl()} is {@code false}
   */
  public String getNormalizedTigerProxyUrl() {
    if (!hasTigerProxyUrl()) {
      throw new IllegalStateException("Entry does not have a Tiger proxy URL override");
    }
    return normalizeUrl(tigerProxyUrl);
  }

  /**
   * Returns the effective Tiger proxy URL for this entry: the per-host override if present,
   * otherwise the provided default.
   *
   * @param globalDefault the URL to use if this entry has no override
   * @return the effective URL, or null if both the override and default are null/blank
   */
  public String getEffectiveTigerProxyUrl(String globalDefault) {
    if (hasTigerProxyUrl()) {
      return tigerProxyUrl;
    }
    return (globalDefault == null || globalDefault.isBlank()) ? null : globalDefault;
  }

  /**
   * Normalizes a proxy URL for use as a cache key: lowercases and strips a trailing slash. Keeps
   * the path so distinct admin endpoints on the same host don't collapse.
   */
  private static String normalizeUrl(String url) {
    String trimmed = url.trim();
    if (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed.toLowerCase(Locale.ROOT);
  }
}
