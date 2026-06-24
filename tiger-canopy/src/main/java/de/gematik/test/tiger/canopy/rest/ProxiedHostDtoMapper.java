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
package de.gematik.test.tiger.canopy.rest;

import de.gematik.test.tiger.canopy.client.dto.ProxiedHostDto;
import de.gematik.test.tiger.canopy.registry.ProxiedHostEntry;

/**
 * Maps the runtime-side {@link ProxiedHostEntry} to its wire form {@link ProxiedHostDto}.
 *
 * <p>Lives on the runtime side so the wire-format module ({@code tiger-canopy-client}) stays free
 * of any reference to the in-memory domain model.
 */
final class ProxiedHostDtoMapper {

  private ProxiedHostDtoMapper() {}

  static ProxiedHostDto toDto(ProxiedHostEntry e) {
    // Route ID tracking is now internal to TigerProxyAdminClient; not exposed in the DTO
    return new ProxiedHostDto(
        e.getHost(), e.getMatchType(), e.getAddedAt(), null, e.getTigerProxyUrl());
  }
}
