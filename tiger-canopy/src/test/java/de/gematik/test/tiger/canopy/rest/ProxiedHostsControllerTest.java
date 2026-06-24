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

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.gematik.test.tiger.canopy.client.config.ControlMode;
import de.gematik.test.tiger.canopy.client.config.MatchType;
import de.gematik.test.tiger.canopy.config.CanopyConfiguration;
import de.gematik.test.tiger.canopy.dns.ProxyAddressProvider;
import de.gematik.test.tiger.canopy.registry.ProxiedHostEntry;
import de.gematik.test.tiger.canopy.registry.ProxiedHostRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ProxiedHostsController.class)
@Import(ApiExceptionHandler.class)
class ProxiedHostsControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private ProxiedHostRegistry registry;
  @MockitoBean private ProxyAddressProvider proxyAddressProvider;
  @MockitoBean private CanopyConfiguration configuration;

  @BeforeEach
  void setup() {
    when(configuration.getTigerProxyUrl()).thenReturn("http://proxy.local:9999");
    when(configuration.getControlMode()).thenReturn(ControlMode.NONE);
    when(configuration.getDnsPort()).thenReturn(53);
  }

  private static ProxiedHostEntry entry(String host, MatchType type) {
    return ProxiedHostEntry.builder()
        .host(host)
        .matchType(type)
        .addedAt(Instant.parse("2026-01-01T00:00:00Z"))
        .build();
  }

  // ---- list -----------------------------------------------------------

  @Test
  void list_returnsRegistryEntries() throws Exception {
    when(registry.getEntries())
        .thenReturn(
            List.of(entry("a.example", MatchType.EXACT), entry("b.example", MatchType.SUFFIX)));

    mvc.perform(get("/api/v1/proxied-hosts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].host").value("a.example"))
        .andExpect(jsonPath("$[0].matchType").value("EXACT"))
        .andExpect(jsonPath("$[1].matchType").value("SUFFIX"));
  }

  // ---- add ------------------------------------------------------------

  @Test
  void add_returns201AndDelegatesToRegistry() throws Exception {
    when(registry.add(eq("foo.example"), eq(MatchType.SUFFIX), any()))
        .thenReturn(entry("foo.example", MatchType.SUFFIX));

    mvc.perform(
            post("/api/v1/proxied-hosts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                         {"host":"foo.example","matchType":"SUFFIX"}"""))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.host").value("foo.example"))
        .andExpect(jsonPath("$.matchType").value("SUFFIX"));

    verify(registry).add("foo.example", MatchType.SUFFIX, null);
  }

  @Test
  void add_defaultsToExactWhenMatchTypeMissing() throws Exception {
    when(registry.add(eq("foo.example"), eq(MatchType.EXACT), any()))
        .thenReturn(entry("foo.example", MatchType.EXACT));

    mvc.perform(
            post("/api/v1/proxied-hosts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                         {"host":"foo.example"}"""))
        .andExpect(status().isCreated());

    verify(registry).add("foo.example", MatchType.EXACT, null);
  }

  @Test
  void add_returns400WhenHostBlank() throws Exception {
    mvc.perform(
            post("/api/v1/proxied-hosts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                         {"host":""}"""))
        .andExpect(status().isBadRequest());
  }

  // ---- bulk -----------------------------------------------------------

  @Test
  void bulk_classifiesAddedVsUnchanged() throws Exception {
    when(registry.lookup("a.example")).thenReturn(Optional.empty());
    when(registry.lookup("b.example")).thenReturn(Optional.of(entry("b.example", MatchType.EXACT)));
    when(registry.add(eq("a.example"), any(), any()))
        .thenReturn(entry("a.example", MatchType.EXACT));
    when(registry.add(eq("b.example"), any(), any()))
        .thenReturn(entry("b.example", MatchType.EXACT));

    mvc.perform(
            post("/api/v1/proxied-hosts/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "hosts": [
                        {"host":"a.example"},
                        {"host":"b.example"}
                      ]
                    }"""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.added.length()").value(1))
        .andExpect(jsonPath("$.added[0].host").value("a.example"))
        .andExpect(jsonPath("$.unchanged.length()").value(1))
        .andExpect(jsonPath("$.unchanged[0].host").value("b.example"));
  }

  @Test
  void bulk_returns400WhenListEmpty() throws Exception {
    mvc.perform(
            post("/api/v1/proxied-hosts/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                         {"hosts":[]}"""))
        .andExpect(status().isBadRequest());
  }

  // ---- delete ---------------------------------------------------------

  @Test
  void delete_returns204AndDelegates() throws Exception {
    mvc.perform(delete("/api/v1/proxied-hosts/foo.example")).andExpect(status().isNoContent());
    verify(registry).remove("foo.example");
  }

  @Test
  void clear_returns204AndDelegates() throws Exception {
    mvc.perform(delete("/api/v1/proxied-hosts")).andExpect(status().isNoContent());
    verify(registry, times(1)).clear();
  }

  // ---- config ---------------------------------------------------------

  @Test
  void config_returnsCurrentConfiguration() throws Exception {
    mvc.perform(get("/api/v1/proxied-hosts/config"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tigerProxyUrl").value("http://proxy.local:9999"))
        .andExpect(jsonPath("$.controlMode").value("NONE"))
        .andExpect(jsonPath("$.dnsPort").value(53));
  }

  @Test
  void updateProxyUrl_persistsAndRefreshesProvider() throws Exception {
    mvc.perform(
            put("/api/v1/proxied-hosts/config/proxy-url")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                         {"url":"http://new-proxy.local:9999"}"""))
        .andExpect(status().isOk());

    verify(configuration).setTigerProxyUrl("http://new-proxy.local:9999");
    verify(proxyAddressProvider).refresh();
  }

  @Test
  void updateProxyUrl_returns400WhenUrlHasNoHost() throws Exception {
    mvc.perform(
            put("/api/v1/proxied-hosts/config/proxy-url")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                         {"url":"not-a-url"}"""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("not-a-url")));
  }

  @Test
  void updateProxyUrl_returns400WhenUrlBlank() throws Exception {
    mvc.perform(
            put("/api/v1/proxied-hosts/config/proxy-url")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                         {"url":""}"""))
        .andExpect(status().isBadRequest());
  }
}
