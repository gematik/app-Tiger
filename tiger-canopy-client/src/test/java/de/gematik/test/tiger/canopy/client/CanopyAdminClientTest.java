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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import de.gematik.test.tiger.canopy.client.config.ControlMode;
import de.gematik.test.tiger.canopy.client.config.MatchType;
import de.gematik.test.tiger.canopy.client.dto.AddProxiedHostRequest;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CanopyAdminClientTest {

  private WireMockServer wm;
  private CanopyAdminClient client;

  @BeforeEach
  void setup() {
    wm = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    wm.start();
    client = new CanopyAdminClient(URI.create("http://localhost:" + wm.port()));
  }

  @AfterEach
  void tearDown() {
    wm.stop();
  }

  // ---- list / config / read paths -----------------------------------

  @Test
  void list_returnsParsedHosts() {
    wm.stubFor(
        get(urlEqualTo("/api/v1/proxied-hosts"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        [{"host":"a.example","matchType":"EXACT","addedAt":"2026-05-07T12:00:00Z","routeId":null},
                         {"host":"b.example","matchType":"SUFFIX","addedAt":"2026-05-07T12:01:00Z","routeId":"r-1"}]""")));

    var hosts = client.list();

    assertThat(hosts).hasSize(2);
    assertThat(hosts.get(0).host()).isEqualTo("a.example");
    assertThat(hosts.get(0).matchType()).isEqualTo(MatchType.EXACT);
    assertThat(hosts.get(1).matchType()).isEqualTo(MatchType.SUFFIX);
    assertThat(hosts.get(1).routeId()).isEqualTo("r-1");
  }

  @Test
  void getConfig_parsesEnumAndPort() {
    wm.stubFor(
        get(urlEqualTo("/api/v1/proxied-hosts/config"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"tigerProxyUrl":"http://proxy:9090","controlMode":"ROUTE_PER_HOST","dnsPort":53}""")));

    var config = client.getConfig();

    assertThat(config.tigerProxyUrl()).isEqualTo("http://proxy:9090");
    assertThat(config.controlMode()).isEqualTo(ControlMode.ROUTE_PER_HOST);
    assertThat(config.dnsPort()).isEqualTo(53);
  }

  // ---- add / bulk / write paths -------------------------------------

  @Test
  void add_postsExactByDefault_andReturnsParsedDto() {
    wm.stubFor(
        post(urlEqualTo("/api/v1/proxied-hosts"))
            .withRequestBody(
                equalToJson(
                    """
                {"host":"a.example","matchType":"EXACT"}"""))
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"host":"a.example","matchType":"EXACT","addedAt":"2026-05-07T12:00:00Z","routeId":null}""")));

    var dto = client.add("a.example");

    assertThat(dto.host()).isEqualTo("a.example");
    assertThat(dto.matchType()).isEqualTo(MatchType.EXACT);
  }

  @Test
  void add_withSuffixMatchType_serializesEnum() {
    wm.stubFor(
        post(urlEqualTo("/api/v1/proxied-hosts"))
            .withRequestBody(
                equalToJson(
                    """
                {"host":"example.com","matchType":"SUFFIX"}"""))
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withBody(
                        """
                {"host":"example.com","matchType":"SUFFIX"}""")));

    client.add("example.com", MatchType.SUFFIX);

    wm.verify(1, postRequestedFor(urlEqualTo("/api/v1/proxied-hosts")));
  }

  @Test
  void bulkAdd_buildsBulkPayload_andParsesAddedUnchanged() {
    wm.stubFor(
        post(urlEqualTo("/api/v1/proxied-hosts/bulk"))
            .withRequestBody(
                equalToJson(
                    """
                    {
                      "hosts":[
                        {"host":"a.example","matchType":"EXACT"},
                        {"host":"example.com","matchType":"SUFFIX"}
                      ]
                    }"""))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "added":[{"host":"a.example","matchType":"EXACT"}],
                          "unchanged":[{"host":"example.com","matchType":"SUFFIX"}]
                        }""")));

    var resp =
        client.bulkAdd(
            List.of(
                new AddProxiedHostRequest("a.example", MatchType.EXACT),
                new AddProxiedHostRequest("example.com", MatchType.SUFFIX)));

    assertThat(resp.added()).hasSize(1);
    assertThat(resp.added().get(0).host()).isEqualTo("a.example");
    assertThat(resp.unchanged()).hasSize(1);
    assertThat(resp.unchanged().get(0).host()).isEqualTo("example.com");
  }

  @Test
  void bulkAdd_emptyListThrowsBeforeNetworkCall() {
    assertThatThrownBy(() -> client.bulkAdd(List.of()))
        .isInstanceOf(CanopyClientException.class)
        .hasMessageContaining("at least one");
    assertThat(wm.getAllServeEvents()).isEmpty();
  }

  // ---- delete / clear ------------------------------------------------

  @Test
  void remove_sendsDelete() {
    wm.stubFor(
        delete(urlPathEqualTo("/api/v1/proxied-hosts/a.example"))
            .willReturn(aResponse().withStatus(204)));

    client.remove("a.example");

    wm.verify(1, deleteRequestedFor(urlPathEqualTo("/api/v1/proxied-hosts/a.example")));
  }

  @Test
  void clearAll_sendsDeleteOnRoot() {
    wm.stubFor(delete(urlEqualTo("/api/v1/proxied-hosts")).willReturn(aResponse().withStatus(204)));

    client.clearAll();

    wm.verify(1, deleteRequestedFor(urlEqualTo("/api/v1/proxied-hosts")));
  }

  // ---- updateProxyUrl ------------------------------------------------

  @Test
  void updateProxyUrl_putsAndParsesConfig() {
    wm.stubFor(
        put(urlEqualTo("/api/v1/proxied-hosts/config/proxy-url"))
            .withRequestBody(
                equalToJson(
                    """
                {"url":"http://new-proxy:9090"}"""))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"tigerProxyUrl":"http://new-proxy:9090","controlMode":"NONE","dnsPort":53}""")));

    var cfg = client.updateProxyUrl("http://new-proxy:9090");

    assertThat(cfg.tigerProxyUrl()).isEqualTo("http://new-proxy:9090");
    wm.verify(1, putRequestedFor(urlEqualTo("/api/v1/proxied-hosts/config/proxy-url")));
  }

  // ---- error handling ------------------------------------------------

  @Test
  void serverErrorResponse_yieldsCanopyClientException() {
    wm.stubFor(post(urlEqualTo("/api/v1/proxied-hosts")).willReturn(aResponse().withStatus(500)));

    assertThatThrownBy(() -> client.add("a.example"))
        .isInstanceOf(CanopyClientException.class)
        .hasMessageContaining("500");
  }

  @Test
  void trailingSlashInBaseUrl_isHonored() {
    var c = new CanopyAdminClient(URI.create("http://localhost:" + wm.port() + "/"));
    wm.stubFor(
        get(urlEqualTo("/api/v1/proxied-hosts"))
            .willReturn(aResponse().withStatus(200).withBody("[]")));

    assertThat(c.list()).isEmpty();
  }

  @Test
  void getBaseUrl_stripsTrailingSlash() {
    var c = new CanopyAdminClient(URI.create("http://localhost:" + wm.port() + "/"));
    assertThat(c.getBaseUrl().toString()).isEqualTo("http://localhost:" + wm.port());
  }

  @Test
  void list_emptyBody_returnsNull() {
    // Exercises the empty-body branch of readBody(TypeReference).
    wm.stubFor(
        get(urlEqualTo("/api/v1/proxied-hosts"))
            .willReturn(aResponse().withStatus(200).withBody("")));

    assertThat(client.list()).isNull();
  }

  @Test
  void getConfig_emptyBody_returnsNull() {
    // Exercises the empty-body branch of readBody(Class).
    wm.stubFor(
        get(urlEqualTo("/api/v1/proxied-hosts/config"))
            .willReturn(aResponse().withStatus(200).withBody("")));

    assertThat(client.getConfig()).isNull();
  }

  @Test
  void list_malformedJson_yieldsCanopyClientException() {
    wm.stubFor(
        get(urlEqualTo("/api/v1/proxied-hosts"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{ not json")));

    assertThatThrownBy(() -> client.list())
        .isInstanceOf(CanopyClientException.class)
        .hasMessageContaining("parse CANOPY response");
  }

  @Test
  void getConfig_malformedJson_yieldsCanopyClientException() {
    wm.stubFor(
        get(urlEqualTo("/api/v1/proxied-hosts/config"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{ not json")));

    assertThatThrownBy(() -> client.getConfig())
        .isInstanceOf(CanopyClientException.class)
        .hasMessageContaining("parse CANOPY response");
  }

  @Test
  void connectionFailure_wrapsIoExceptionInCanopyClientException() {
    int deadPort = wm.port();
    wm.stop(); // refusal/IOException on next connect attempt
    var orphan = new CanopyAdminClient(URI.create("http://localhost:" + deadPort));

    assertThatThrownBy(orphan::list)
        .isInstanceOf(CanopyClientException.class)
        .hasMessageContaining("GET")
        .hasMessageContaining("failed:")
        .hasCauseInstanceOf(java.io.IOException.class);
  }
}
