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
package de.gematik.test.tiger.canopy.client.dto;

import de.gematik.test.tiger.canopy.client.config.MatchType;
import java.time.Instant;

/**
 * REST representation of a proxied host entry. {@code addedAt} reflects when the entry was first
 * registered; {@code routeId} is non-null only when the server is running in stage-2 {@code
 * ROUTE_PER_HOST} mode and the proxy accepted a route registration; {@code tigerProxyUrl} is
 * non-null only when the entry carries a per-host override of {@code canopy.tigerProxyUrl}.
 */
public record ProxiedHostDto(
    String host, MatchType matchType, Instant addedAt, String routeId, String tigerProxyUrl) {

  /** Backward-compatible 4-arg form: no per-entry proxy override. */
  public ProxiedHostDto(String host, MatchType matchType, Instant addedAt, String routeId) {
    this(host, matchType, addedAt, routeId, null);
  }
}
