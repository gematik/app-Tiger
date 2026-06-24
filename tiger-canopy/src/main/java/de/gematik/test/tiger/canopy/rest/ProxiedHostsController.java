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

import de.gematik.test.tiger.canopy.client.config.MatchType;
import de.gematik.test.tiger.canopy.client.dto.AddProxiedHostRequest;
import de.gematik.test.tiger.canopy.client.dto.BulkAddRequest;
import de.gematik.test.tiger.canopy.client.dto.BulkAddResponse;
import de.gematik.test.tiger.canopy.client.dto.ConfigDto;
import de.gematik.test.tiger.canopy.client.dto.ProxiedHostDto;
import de.gematik.test.tiger.canopy.client.dto.UpdateProxyUrlRequest;
import de.gematik.test.tiger.canopy.config.CanopyConfiguration;
import de.gematik.test.tiger.canopy.dns.ProxyAddressProvider;
import de.gematik.test.tiger.canopy.registry.ProxiedHostEntry;
import de.gematik.test.tiger.canopy.registry.ProxiedHostRegistry;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API to inspect and mutate the {@link ProxiedHostRegistry} at runtime, plus diagnostic access
 * to the active {@link CanopyConfiguration}.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/proxied-hosts")
@RequiredArgsConstructor
public class ProxiedHostsController {

  private final ProxiedHostRegistry registry;
  private final CanopyConfiguration configuration;
  private final @Nullable ProxyAddressProvider proxyAddressProvider;

  // ---- list / get -----------------------------------------------------

  @GetMapping
  public List<ProxiedHostDto> list() {
    return registry.getEntries().stream().map(ProxiedHostDtoMapper::toDto).toList();
  }

  // ---- add ------------------------------------------------------------

  @PostMapping
  public ResponseEntity<ProxiedHostDto> add(@Valid @RequestBody AddProxiedHostRequest request) {
    MatchType type = request.matchType() == null ? MatchType.EXACT : request.matchType();
    ProxiedHostEntry entry = registry.add(request.host(), type, request.tigerProxyUrl());
    return ResponseEntity.status(HttpStatus.CREATED).body(ProxiedHostDtoMapper.toDto(entry));
  }

  @PostMapping("/bulk")
  public BulkAddResponse bulkAdd(@Valid @RequestBody BulkAddRequest request) {
    List<ProxiedHostDto> added = new ArrayList<>();
    List<ProxiedHostDto> unchanged = new ArrayList<>();
    for (AddProxiedHostRequest item : request.hosts()) {
      MatchType type = item.matchType() == null ? MatchType.EXACT : item.matchType();
      String normalized = ProxiedHostRegistry.normalize(item.host());
      boolean wasPresent = registry.lookup(normalized).isPresent();
      ProxiedHostEntry entry = registry.add(item.host(), type, item.tigerProxyUrl());
      ProxiedHostDto dto = ProxiedHostDtoMapper.toDto(entry);
      if (wasPresent) {
        unchanged.add(dto);
      } else {
        added.add(dto);
      }
    }
    return new BulkAddResponse(added, unchanged);
  }

  // ---- delete ---------------------------------------------------------

  @DeleteMapping("/{host}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable("host") String host) {
    registry.remove(host);
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void clear() {
    registry.clear();
  }

  // ---- config ---------------------------------------------------------

  @GetMapping("/config")
  public ConfigDto config() {
    return new ConfigDto(
        configuration.getTigerProxyUrl(),
        configuration.getControlMode(),
        configuration.getDnsPort());
  }

  @PutMapping("/config/proxy-url")
  public ConfigDto updateProxyUrl(@Valid @RequestBody UpdateProxyUrlRequest request) {
    // Eagerly validate the URL has a host so the caller gets immediate feedback.
    ProxyAddressProvider.extractHost(request.url());
    log.info(
        "Updating tigerProxyUrl from '{}' to '{}'",
        configuration.getTigerProxyUrl(),
        request.url());
    configuration.setTigerProxyUrl(request.url());
    if (proxyAddressProvider != null) {
      proxyAddressProvider.refresh();
    }
    return config();
  }
}
