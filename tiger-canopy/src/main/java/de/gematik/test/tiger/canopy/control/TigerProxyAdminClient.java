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
package de.gematik.test.tiger.canopy.control;

import de.gematik.test.tiger.canopy.Constants;
import de.gematik.test.tiger.canopy.config.CanopyConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Stage-2 client that talks to the Tiger proxy admin API to add/remove routes when the CANOPY
 * registry is mutated.
 *
 * <p>Bean is only created when {@code canopy.controlMode} is not {@code NONE}.
 *
 * <p>The base URL is read from {@link CanopyConfiguration#getTigerProxyUrl()} on every call so that
 * runtime updates via {@code PUT /api/v1/proxied-hosts/config/proxy-url} are honored.
 */
@Slf4j
@Component
@ConditionalOnExpression("'${canopy.controlMode:NONE}' != 'NONE'")
public class TigerProxyAdminClient {

  private final CanopyConfiguration configuration;
  private final RestClient restClient;
  // Track route IDs by (host, proxyUrl) key for automatic cleanup
  private final Map<RouteKey, String> routeIdMap = new HashMap<>();

  public TigerProxyAdminClient(CanopyConfiguration configuration, RestClient.Builder builder) {
    this.configuration = configuration;
    HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    this.restClient = builder.requestFactory(new JdkClientHttpRequestFactory(http)).build();
  }

  /** Key for tracking routes by host and proxy URL. */
  private record RouteKey(String host, String proxyUrl) {}

  /**
   * Adds a route on the Tiger proxy that intercepts traffic for {@code host}. When {@code
   * overrideBaseUrl} is non-blank the request targets that URL instead of the global {@code
   * canopy.tigerProxyUrl} — used when a {@code proxiedHosts} entry carries a per-host proxy
   * override. Currently uses {@code https://<host>} as both {@code from} and {@code to};
   * refinements (HTTP, ports, scheme options) are tracked under the {@code honorHostHeaderRouting}
   * follow-up.
   *
   * <p>The assigned route ID is tracked internally for automatic cleanup on {@link
   * #deleteRouteForHost(String, Optional)}.
   */
  public void addRoute(String host, Optional<String> overrideBaseUrl) {
    String base = effectiveBaseUrl(overrideBaseUrl);
    URI uri = URI.create(stripTrailingSlash(base) + Constants.ROUTE_ENDPOINT);
    TigerRouteDto request = TigerRouteDto.fromTo("https://" + host, "https://" + host);
    try {
      TigerRouteDto response =
          restClient
              .put()
              .uri(uri)
              .contentType(MediaType.APPLICATION_JSON)
              .body(request)
              .retrieve()
              .body(TigerRouteDto.class);
      String id = response == null ? null : response.id();
      log.info("Added route on Tiger proxy for host '{}' at {} (id={})", host, base, id);
      if (id != null) {
        routeIdMap.put(new RouteKey(host, base), id);
      }
    } catch (RestClientException e) {
      log.atWarn()
          .addArgument(host)
          .addArgument(uri)
          .addArgument(e::getMessage)
          .log("Failed to add route for host '{}' on Tiger proxy at {}: {}");
    }
  }

  /**
   * Deletes the route for a given host using the internally tracked route ID. Best-effort; failures
   * are logged and swallowed.
   */
  public void deleteRouteForHost(String host, Optional<String> overrideBaseUrl) {
    String base = effectiveBaseUrl(overrideBaseUrl);
    String routeId = routeIdMap.remove(new RouteKey(host, base));
    if (routeId == null) {
      log.atDebug()
          .addArgument(host)
          .addArgument(base)
          .log("No tracked route for host '{}' on proxy at {}; nothing to delete");
      return;
    }
    deleteRoute(routeId, base);
  }

  private void deleteRoute(String routeId, String baseUrl) {
    URI uri = URI.create(stripTrailingSlash(baseUrl) + Constants.ROUTE_ENDPOINT + "/" + routeId);
    try {
      restClient.delete().uri(uri).retrieve().toBodilessEntity();
      log.info("Deleted route '{}' on Tiger proxy at {}", routeId, baseUrl);
    } catch (RestClientException e) {
      log.atWarn()
          .addArgument(routeId)
          .addArgument(uri)
          .addArgument(e::getMessage)
          .log("Failed to delete route '{}' on Tiger proxy at {}: {}");
    }
  }

  /** Picks the per-entry override when set, falling back to the global URL. */
  private String effectiveBaseUrl(Optional<String> overrideBaseUrl) {
    return overrideBaseUrl.filter(url -> !url.isBlank()).orElseGet(this::requireProxyUrl);
  }

  private String requireProxyUrl() {
    String url = configuration.getTigerProxyUrl();
    if (url == null || url.isBlank()) {
      throw new IllegalStateException(
          "canopy.tigerProxyUrl is not configured but controlMode requires it");
    }
    return url;
  }

  private static String stripTrailingSlash(String url) {
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
