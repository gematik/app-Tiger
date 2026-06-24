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
import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code POST /api/v1/proxied-hosts}. {@code matchType} defaults to {@code EXACT}; {@code
 * tigerProxyUrl} is optional and overrides {@code canopy.tigerProxyUrl} for this entry only — lets
 * a single canopy fan DNS answers (and, in {@code ROUTE_PER_HOST} mode, route registrations) out to
 * several reverse proxies.
 */
public record AddProxiedHostRequest(
    @NotBlank String host, MatchType matchType, String tigerProxyUrl) {

  /** Backward-compatible 2-arg form: no per-entry proxy override. */
  public AddProxiedHostRequest(String host, MatchType matchType) {
    this(host, matchType, null);
  }
}
