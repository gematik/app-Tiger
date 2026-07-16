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
package de.gematik.test.tiger.canopy.client;

import de.gematik.test.tiger.canopy.client.config.MatchType;
import de.gematik.test.tiger.canopy.client.dto.AddProxiedHostRequest;
import de.gematik.test.tiger.canopy.client.dto.BulkAddRequest;
import de.gematik.test.tiger.canopy.client.dto.BulkAddResponse;
import de.gematik.test.tiger.canopy.client.dto.ConfigDto;
import de.gematik.test.tiger.canopy.client.dto.ProxiedHostDto;
import de.gematik.test.tiger.canopy.client.dto.UpdateProxyUrlRequest;
import de.gematik.test.tiger.common.util.TigerSerializationUtil;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Slim, framework-free client for the CANOPY REST API ({@code /api/v1/proxied-hosts}).
 *
 * <p>This class uses only JDK {@link HttpClient} and Jackson; it deliberately has <strong>no
 * dependency on Spring</strong>, so it can be embedded in glue code, in TestEnv-Mgr server adapters
 * and in CLI tooling without dragging the runtime in.
 *
 * <p>All methods are synchronous and idempotent where the server is. Failures are signalled via
 * {@link CanopyClientException}.
 *
 * <p>The client honors trailing slashes in the base URL and is thread-safe.
 */
public class CanopyAdminClient {

  private static final TypeReference<List<ProxiedHostDto>> LIST_OF_HOSTS = new TypeReference<>() {};

  private static final String API = "/api/v1/proxied-hosts";

  private static final ObjectMapper DEFAULT_MAPPER =
      TigerSerializationUtil.createSimpleJsonMapper();
  ;

  private final URI baseUrl;
  private final HttpClient http;
  private final ObjectMapper mapper;
  private final Duration requestTimeout;

  /**
   * Creates a client with sensible defaults: 5 s connect timeout, 10 s per-request timeout, and an
   * {@link ObjectMapper} configured with and lenient unknown-property handling.
   */
  public CanopyAdminClient(URI baseUrl) {
    this(
        baseUrl,
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
        DEFAULT_MAPPER,
        Duration.ofSeconds(10));
  }

  /** Full-control constructor for callers that already manage their own {@link HttpClient}. */
  public CanopyAdminClient(
      URI baseUrl, HttpClient http, ObjectMapper mapper, Duration requestTimeout) {
    this.baseUrl = stripTrailingSlash(Objects.requireNonNull(baseUrl, "baseUrl"));
    this.http = Objects.requireNonNull(http, "http");
    this.mapper = Objects.requireNonNull(mapper, "mapper");
    this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
  }

  /** Returns the base URL of the CANOPY instance, e.g. {@code http://canopy:8080}. */
  public URI getBaseUrl() {
    return baseUrl;
  }

  // -------------------------------------------------------------------
  // host registry
  // -------------------------------------------------------------------

  /** {@code GET /api/v1/proxied-hosts}. */
  public List<ProxiedHostDto> list() {
    HttpResponse<String> response = send("GET", API, null);
    expectSuccess(response, "list proxied hosts");
    return readBody(response, LIST_OF_HOSTS);
  }

  /**
   * {@code POST /api/v1/proxied-hosts}. Idempotent — adding an existing host returns the existing
   * entry.
   */
  public ProxiedHostDto add(String host, MatchType matchType) {
    HttpResponse<String> response =
        send(
            "POST", API, mapper.valueToTree(new AddProxiedHostRequest(host, matchType)).toString());
    expectSuccess(response, "add proxied host '" + host + "'");
    return readBody(response, ProxiedHostDto.class);
  }

  /** Convenience overload for {@link MatchType#EXACT}. */
  public ProxiedHostDto add(String host) {
    return add(host, MatchType.EXACT);
  }

  /** {@code POST /api/v1/proxied-hosts/bulk}. */
  public BulkAddResponse bulkAdd(List<AddProxiedHostRequest> hosts) {
    if (hosts == null || hosts.isEmpty()) {
      throw new CanopyClientException("bulkAdd requires at least one host entry");
    }
    HttpResponse<String> response =
        send("POST", API + "/bulk", mapper.valueToTree(new BulkAddRequest(hosts)).toString());
    expectSuccess(response, "bulk add proxied hosts");
    return readBody(response, BulkAddResponse.class);
  }

  /** {@code DELETE /api/v1/proxied-hosts/{host}}. Best-effort idempotent. */
  public void remove(String host) {
    HttpResponse<String> response = send("DELETE", API + "/" + encode(host), null);
    expectSuccess(response, "remove proxied host '" + host + "'");
  }

  /** {@code DELETE /api/v1/proxied-hosts}. Clears the entire registry. */
  public void clearAll() {
    HttpResponse<String> response = send("DELETE", API, null);
    expectSuccess(response, "clear proxied hosts");
  }

  // -------------------------------------------------------------------
  // configuration
  // -------------------------------------------------------------------

  /** {@code GET /api/v1/proxied-hosts/config}. */
  public ConfigDto getConfig() {
    HttpResponse<String> response = send("GET", API + "/config", null);
    expectSuccess(response, "get config");
    return readBody(response, ConfigDto.class);
  }

  /** {@code PUT /api/v1/proxied-hosts/config/proxy-url}. Updates the live tigerProxy target. */
  public ConfigDto updateProxyUrl(String url) {
    HttpResponse<String> response =
        send(
            "PUT",
            API + "/config/proxy-url",
            mapper.valueToTree(new UpdateProxyUrlRequest(url)).toString());
    expectSuccess(response, "update tigerProxyUrl");
    return readBody(response, ConfigDto.class);
  }

  // -------------------------------------------------------------------
  // helpers
  // -------------------------------------------------------------------

  private HttpResponse<String> send(String method, String path, String jsonBody) {
    URI uri = URI.create(baseUrl + path);
    HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(requestTimeout);
    HttpRequest.BodyPublisher publisher =
        jsonBody == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(jsonBody);
    if (jsonBody != null) {
      builder.header("Content-Type", "application/json");
    }
    builder.header("Accept", "application/json");
    builder.method(method, publisher);
    try {
      return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new CanopyClientException(
          "CANOPY HTTP " + method + " " + uri + " failed: " + e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new CanopyClientException("CANOPY HTTP " + method + " " + uri + " interrupted", e);
    }
  }

  private static void expectSuccess(HttpResponse<String> response, String operation) {
    int status = response.statusCode();
    if (status < 200 || status >= 300) {
      throw new CanopyClientException(
          "CANOPY operation '"
              + operation
              + "' failed with status "
              + status
              + ": "
              + response.body());
    }
  }

  private <T> T readBody(HttpResponse<String> response, Class<T> type) {
    if (response.body() == null || response.body().isEmpty()) {
      return null;
    }
    try {
      return mapper.readValue(response.body(), type);
    } catch (RuntimeException e) {
      throw new CanopyClientException(
          "Failed to parse CANOPY response body: " + response.body(), e);
    }
  }

  private <T> T readBody(HttpResponse<String> response, TypeReference<T> type) {
    if (response.body() == null || response.body().isEmpty()) {
      return null;
    }
    try {
      return mapper.readValue(response.body(), type);
    } catch (RuntimeException e) {
      throw new CanopyClientException(
          "Failed to parse CANOPY response body: " + response.body(), e);
    }
  }

  private static URI stripTrailingSlash(URI uri) {
    String s = uri.toString();
    return s.endsWith("/") ? URI.create(s.substring(0, s.length() - 1)) : uri;
  }

  private static String encode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }
}
