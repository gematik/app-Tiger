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
 */
package de.gematik.test.tiger.canopy.extension.glue;

import de.gematik.test.tiger.canopy.client.CanopyAdminClient;
import de.gematik.test.tiger.canopy.client.CanopyClientException;
import de.gematik.test.tiger.canopy.client.config.MatchType;
import de.gematik.test.tiger.canopy.client.dto.AddProxiedHostRequest;
import de.gematik.test.tiger.canopy.client.dto.ProxiedHostDto;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.glue.TigerGluePackage;
import de.gematik.test.tiger.common.util.TigerSerializationUtil;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.de.Dann;
import io.cucumber.java.de.Wenn;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * Cucumber glue code for runtime configuration of a CANOPY DNS server (the {@code tiger-canopy}
 * module) from a Tiger test suite. Delegates the actual REST plumbing to {@link CanopyAdminClient}
 * from {@code tiger-canopy-client}.
 *
 * <p>The CANOPY base URL must be configured via the Tiger configuration key {@code
 * tiger.canopy.baseUrl} (e.g. {@code http://canopy:8080}) — typically in the test environment YAML
 * — or set at runtime through {@link #setCanopyBaseUrl(URI)}. All steps support placeholder
 * resolution via {@code tigerResolvedString} / {@code tigerResolvedUrl}.
 *
 * <p>The supported match types are {@code EXACT} (default) and {@code SUFFIX}.
 *
 * <p><strong>Cucumber glue scan:</strong> the {@link TigerGluePackage @TigerGluePackage} marker
 * below makes Tiger's {@code TigerCucumberRunner} auto-discover this package on the classpath and
 * add it to {@code cucumber.glue} without any explicit configuration on the user's side. Hard-coded
 * {@code @CucumberOptions(glue=...)} or {@code @ConfigurationParameter(cucumber.glue, …)} setups
 * that bypass {@code TigerCucumberRunner} still need to list {@code
 * de.gematik.test.tiger.canopy.extension.glue} explicitly.
 */
@TigerGluePackage
@SuppressWarnings("unused") // glue code is used via reflection
@Slf4j
public class CanopyGlue {

  /** Tiger configuration key holding the CANOPY base URL (e.g. {@code http://canopy:8080}). */
  public static final String CONFIG_KEY_BASE_URL = "tiger.canopy.baseUrl";

  private static final ObjectMapper JSON_MAPPER = TigerSerializationUtil.createSimpleJsonMapper();

  private final HttpClient httpClient;

  public CanopyGlue() {
    this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
  }

  /** Test-only constructor allowing a pre-configured client. */
  CanopyGlue(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  // -------------------------------------------------------------------
  // configuration steps
  // -------------------------------------------------------------------

  /**
   * Sets the CANOPY base URL used by all subsequent CANOPY glue steps. Equivalent to setting the
   * Tiger configuration key {@code tiger.canopy.baseUrl}.
   */
  @When("TGR set CANOPY base URL to {tigerResolvedUrl}")
  @Wenn("TGR setze die CANOPY-Basis-URL auf {tigerResolvedUrl}")
  public void setCanopyBaseUrl(URI baseUrl) {
    log.info("Configuring CANOPY base URL: {}", baseUrl);
    TigerGlobalConfiguration.putValue(CONFIG_KEY_BASE_URL, baseUrl.toString());
  }

  /**
   * Updates the {@code tigerProxyUrl} on the running CANOPY instance via {@code PUT
   * /api/v1/proxied-hosts/config/proxy-url}.
   */
  @When("TGR set CANOPY tiger proxy URL to {tigerResolvedUrl}")
  @Wenn("TGR setze die CANOPY tiger proxy URL auf {tigerResolvedUrl}")
  public void setCanopyTigerProxyUrl(URI proxyUrl) {
    run(c -> c.updateProxyUrl(proxyUrl.toString()));
  }

  // -------------------------------------------------------------------
  // host registry mutation
  // -------------------------------------------------------------------

  /** Adds a single proxied host with match type {@code EXACT}. */
  @When("TGR add proxied host {tigerResolvedString} to CANOPY")
  @Wenn("TGR füge den Proxy-Host {tigerResolvedString} zu CANOPY hinzu")
  public void addProxiedHost(String host) {
    run(c -> c.add(host));
  }

  /**
   * Adds a single proxied host with the given match type ({@code EXACT} or {@code SUFFIX}).
   *
   * @param host the hostname to intercept
   * @param matchType {@code EXACT} or {@code SUFFIX} (case insensitive)
   */
  @When(
      "TGR add proxied host {tigerResolvedString} with match type {tigerResolvedString} to CANOPY")
  @Wenn(
      "TGR füge den Proxy-Host {tigerResolvedString} mit Matchtyp {tigerResolvedString} zu CANOPY hinzu")
  public void addProxiedHostWithMatchType(String host, String matchType) {
    MatchType type = parseMatchType(matchType);
    run(c -> c.add(host, type));
  }

  /**
   * Adds multiple proxied hosts in one bulk call. The data table must have a header row with at
   * least a {@code host} column and optionally a {@code matchType} column.
   */
  @When("TGR add the following proxied hosts to CANOPY:")
  @Wenn("TGR füge folgende Proxy-Hosts zu CANOPY hinzu:")
  public void addProxiedHostsBulk(DataTable hosts) {
    List<AddProxiedHostRequest> requests = parseBulkDataTable(hosts);
    run(c -> c.bulkAdd(requests));
  }

  /** Removes a single proxied host. Idempotent — silently succeeds if the host was not present. */
  @When("TGR remove proxied host {tigerResolvedString} from CANOPY")
  @Wenn("TGR entferne den Proxy-Host {tigerResolvedString} von CANOPY")
  public void removeProxiedHost(String host) {
    run(c -> c.remove(host));
  }

  /** Clears the entire CANOPY registry. */
  @When("TGR clear all proxied hosts in CANOPY")
  @Wenn("TGR lösche alle Proxy-Hosts in CANOPY")
  public void clearProxiedHosts() {
    run(CanopyAdminClient::clearAll);
  }

  // -------------------------------------------------------------------
  // assertions
  // -------------------------------------------------------------------

  /** Asserts that the CANOPY registry currently contains an entry with the given host. */
  @Then("TGR CANOPY contains proxied host {tigerResolvedString}")
  @Dann("TGR CANOPY enthält den Proxy-Host {tigerResolvedString}")
  public void canopyContainsProxiedHost(String host) {
    assertHostPresence(host, true);
  }

  /** Asserts that the CANOPY registry currently does NOT contain an entry for the given host. */
  @Then("TGR CANOPY does not contain proxied host {tigerResolvedString}")
  @Dann("TGR CANOPY enthält den Proxy-Host {tigerResolvedString} nicht")
  public void canopyDoesNotContainProxiedHost(String host) {
    assertHostPresence(host, false);
  }

  private void assertHostPresence(String host, boolean expected) {
    List<ProxiedHostDto> entries = call(CanopyAdminClient::list);
    boolean present = entries.stream().anyMatch(e -> host.equalsIgnoreCase(e.host()));
    if (present != expected) {
      throw new AssertionError(
          "CANOPY registry "
              + (expected ? "did not contain" : "unexpectedly contained")
              + " host '"
              + host
              + "'. Current entries: "
              + entries);
    }
  }

  // -------------------------------------------------------------------
  // bulk data-table parsing (kept here because it's Cucumber-specific)
  // -------------------------------------------------------------------

  static List<AddProxiedHostRequest> parseBulkDataTable(DataTable hosts) {
    List<List<String>> rows = hosts.cells();
    if (rows.size() < 2) {
      throw new CanopyGlueException(
          "Expected at least a header row and one data row, got " + rows.size());
    }
    List<String> header = rows.get(0);
    int hostIdx = header.indexOf("host");
    if (hostIdx < 0) {
      throw new CanopyGlueException(
          "Required data table column 'host' is missing in header " + header);
    }
    int matchTypeIdx = header.indexOf("matchType");

    List<AddProxiedHostRequest> requests = new ArrayList<>();
    for (int r = 1; r < rows.size(); r++) {
      List<String> row = rows.get(r);
      String host = hostIdx < row.size() ? row.get(hostIdx) : null;
      if (host == null || host.isBlank()) {
        throw new CanopyGlueException(
            "Required data table column 'host' is missing or blank in row " + row);
      }
      MatchType matchType = null;
      if (matchTypeIdx >= 0 && matchTypeIdx < row.size()) {
        String raw = row.get(matchTypeIdx);
        if (raw != null && !raw.isBlank()) {
          matchType = parseMatchType(raw);
        }
      }
      requests.add(new AddProxiedHostRequest(host, matchType));
    }
    return requests;
  }

  // -------------------------------------------------------------------
  // helpers
  // -------------------------------------------------------------------

  private CanopyAdminClient client() {
    URI baseUrl = URI.create(requireBaseUrl());
    return new CanopyAdminClient(baseUrl, httpClient, JSON_MAPPER, Duration.ofSeconds(10));
  }

  /** Run a void operation; convert {@link CanopyClientException} to {@link CanopyGlueException}. */
  private void run(Consumer<CanopyAdminClient> op) {
    try {
      op.accept(client());
    } catch (CanopyClientException e) {
      throw new CanopyGlueException(e.getMessage(), e);
    }
  }

  /** Run a result-returning operation; convert exceptions as in {@link #run(Consumer)}. */
  private <T> T call(Function<CanopyAdminClient, T> op) {
    try {
      return op.apply(client());
    } catch (CanopyClientException e) {
      throw new CanopyGlueException(e.getMessage(), e);
    }
  }

  private static String requireBaseUrl() {
    return TigerGlobalConfiguration.readStringOptional(CONFIG_KEY_BASE_URL)
        .filter(s -> !s.isBlank())
        .orElseThrow(
            () ->
                new CanopyGlueException(
                    "CANOPY base URL is not configured. Set Tiger configuration key '"
                        + CONFIG_KEY_BASE_URL
                        + "' or use the step \"TGR set CANOPY base URL to <url>\"."));
  }

  private static MatchType parseMatchType(String matchType) {
    String upper = matchType.trim().toUpperCase(Locale.ROOT);
    try {
      return MatchType.valueOf(upper);
    } catch (IllegalArgumentException e) {
      throw new CanopyGlueException(
          "Unknown CANOPY match type '" + matchType + "'. Expected EXACT or SUFFIX.");
    }
  }

  /** Runtime exception raised by CANOPY glue steps for transport / parsing / state issues. */
  public static class CanopyGlueException extends RuntimeException {
    public CanopyGlueException(String message) {
      super(message);
    }

    public CanopyGlueException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
