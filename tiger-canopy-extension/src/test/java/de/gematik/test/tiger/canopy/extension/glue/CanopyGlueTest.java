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
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import io.cucumber.datatable.DataTable;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CanopyGlueTest {

  private WireMockServer wm;
  private CanopyGlue glue;

  @Test
  void glueClassCarriesTigerGluePackageMarker() {
    assertThat(
            CanopyGlue.class.isAnnotationPresent(
                de.gematik.test.tiger.common.glue.TigerGluePackage.class))
        .isTrue();
  }

  @BeforeEach
  void setup() {
    wm = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    wm.start();
    TigerGlobalConfiguration.reset();
    TigerGlobalConfiguration.initialize();
    TigerGlobalConfiguration.putValue(
        CanopyGlue.CONFIG_KEY_BASE_URL, "http://localhost:" + wm.port());
    glue = new CanopyGlue(HttpClient.newHttpClient());
  }

  @AfterEach
  void tearDown() {
    wm.stop();
    TigerGlobalConfiguration.reset();
  }

  // ---- configuration --------------------------------------------------

  @Test
  void setCanopyBaseUrl_writesToTigerConfiguration() {
    glue.setCanopyBaseUrl(URI.create("http://canopy.example:8080"));

    assertThat(TigerGlobalConfiguration.readString(CanopyGlue.CONFIG_KEY_BASE_URL))
        .isEqualTo("http://canopy.example:8080");
  }

  @Test
  void setCanopyTigerProxyUrl_putsToConfigEndpoint() {
    wm.stubFor(
        put(urlEqualTo("/api/v1/proxied-hosts/config/proxy-url"))
            .withRequestBody(
                equalToJson(
                    """
                {"url":"http://proxy:9999"}"""))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"tigerProxyUrl":"http://proxy:9999","controlMode":"NONE","dnsPort":53}""")));

    glue.setCanopyTigerProxyUrl(URI.create("http://proxy:9999"));

    wm.verify(1, putRequestedFor(urlEqualTo("/api/v1/proxied-hosts/config/proxy-url")));
  }

  // ---- add / bulk -----------------------------------------------------

  @Test
  void addProxiedHost_postsExactByDefault() {
    wm.stubFor(
        post(urlEqualTo("/api/v1/proxied-hosts"))
            .withRequestBody(
                equalToJson(
                    """
                {"host":"a.example","matchType":"EXACT"}"""))
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withBody(
                        """
                {"host":"a.example"}""")));

    glue.addProxiedHost("a.example");

    wm.verify(1, postRequestedFor(urlEqualTo("/api/v1/proxied-hosts")));
  }

  @Test
  void addProxiedHost_withSuffixMatchType() {
    wm.stubFor(
        post(urlEqualTo("/api/v1/proxied-hosts"))
            .withRequestBody(
                equalToJson(
                    """
                {"host":"example.com","matchType":"SUFFIX"}"""))
            .willReturn(aResponse().withStatus(201).withBody("{}")));

    glue.addProxiedHostWithMatchType("example.com", "suffix");

    wm.verify(1, postRequestedFor(urlEqualTo("/api/v1/proxied-hosts")));
  }

  @Test
  void addProxiedHost_invalidMatchTypeThrows() {
    assertThatThrownBy(() -> glue.addProxiedHostWithMatchType("a.example", "fuzzy"))
        .isInstanceOf(CanopyGlue.CanopyGlueException.class)
        .hasMessageContaining("fuzzy");
  }

  @Test
  void addProxiedHostsBulk_buildsBulkPayload() {
    wm.stubFor(
        post(urlEqualTo("/api/v1/proxied-hosts/bulk"))
            .withRequestBody(
                equalToJson(
                    """
                    {
                      "hosts": [
                        {"host":"a.example","matchType":"EXACT"},
                        {"host":"example.com","matchType":"SUFFIX"}
                      ]
                    }"""))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(
                        """
                        {"added":[],"unchanged":[]}""")));

    DataTable table =
        DataTable.create(
            List.of(
                List.of("host", "matchType"),
                List.of("a.example", "EXACT"),
                List.of("example.com", "SUFFIX")));

    glue.addProxiedHostsBulk(table);

    wm.verify(1, postRequestedFor(urlEqualTo("/api/v1/proxied-hosts/bulk")));
  }

  @Test
  void addProxiedHostsBulk_omitsMatchTypeWhenColumnAbsent() {
    wm.stubFor(
        post(urlEqualTo("/api/v1/proxied-hosts/bulk"))
            .withRequestBody(
                equalToJson(
                    """
                {"hosts":[{"host":"a.example"}]}"""))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(
                        """
                        {"added":[],"unchanged":[]}""")));

    DataTable table = DataTable.create(List.of(List.of("host"), List.of("a.example")));

    glue.addProxiedHostsBulk(table);

    wm.verify(1, postRequestedFor(urlEqualTo("/api/v1/proxied-hosts/bulk")));
  }

  // ---- remove / clear -------------------------------------------------

  @Test
  void removeProxiedHost_sendsDelete() {
    wm.stubFor(
        delete(urlPathEqualTo("/api/v1/proxied-hosts/a.example"))
            .willReturn(aResponse().withStatus(204)));

    glue.removeProxiedHost("a.example");

    wm.verify(1, deleteRequestedFor(urlPathEqualTo("/api/v1/proxied-hosts/a.example")));
  }

  @Test
  void clearProxiedHosts_sendsDeleteOnRoot() {
    wm.stubFor(delete(urlEqualTo("/api/v1/proxied-hosts")).willReturn(aResponse().withStatus(204)));

    glue.clearProxiedHosts();

    Assertions.assertDoesNotThrow(
        () -> wm.verify(1, deleteRequestedFor(urlEqualTo("/api/v1/proxied-hosts"))));
  }

  // ---- assertions -----------------------------------------------------

  @Test
  void canopyContainsProxiedHost_passesWhenPresent() {
    wm.stubFor(
        get(urlEqualTo("/api/v1/proxied-hosts"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(
                        """
                        [{"host":"a.example"}]""")));

    Assertions.assertDoesNotThrow(() -> glue.canopyContainsProxiedHost("a.example"));
  }

  @Test
  void canopyContainsProxiedHost_throwsWhenAbsent() {
    wm.stubFor(
        get(urlEqualTo("/api/v1/proxied-hosts"))
            .willReturn(aResponse().withStatus(200).withBody("[]")));

    assertThatThrownBy(() -> glue.canopyContainsProxiedHost("a.example"))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("did not contain");
  }

  @Test
  void canopyDoesNotContainProxiedHost_throwsWhenPresent() {
    wm.stubFor(
        get(urlEqualTo("/api/v1/proxied-hosts"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(
                        """
                        [{"host":"a.example"}]""")));

    assertThatThrownBy(() -> glue.canopyDoesNotContainProxiedHost("a.example"))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("unexpectedly contained");
  }

  // ---- error handling -------------------------------------------------

  @Test
  void missingBaseUrl_yieldsHelpfulError() {
    TigerGlobalConfiguration.reset();
    TigerGlobalConfiguration.initialize();

    assertThatThrownBy(() -> glue.addProxiedHost("a.example"))
        .isInstanceOf(CanopyGlue.CanopyGlueException.class)
        .hasMessageContaining(CanopyGlue.CONFIG_KEY_BASE_URL);
  }

  @Test
  void serverErrorResponse_yieldsCanopyGlueException() {
    wm.stubFor(post(urlEqualTo("/api/v1/proxied-hosts")).willReturn(aResponse().withStatus(500)));

    assertThatThrownBy(() -> glue.addProxiedHost("a.example"))
        .isInstanceOf(CanopyGlue.CanopyGlueException.class)
        .hasMessageContaining("500");
  }

  @Test
  void canopyContainsProxiedHost_wrapsTransportErrorsInGlueException() {
    wm.stubFor(get(urlEqualTo("/api/v1/proxied-hosts")).willReturn(aResponse().withStatus(500)));

    assertThatThrownBy(() -> glue.canopyContainsProxiedHost("a.example"))
        .isInstanceOf(CanopyGlue.CanopyGlueException.class)
        .hasMessageContaining("500");
  }

  @Test
  void canopyDoesNotContainProxiedHost_passesWhenAbsent() {
    wm.stubFor(
        get(urlEqualTo("/api/v1/proxied-hosts"))
            .willReturn(aResponse().withStatus(200).withBody("[]")));

    Assertions.assertDoesNotThrow(() -> glue.canopyDoesNotContainProxiedHost("a.example"));
  }

  @Test
  void blankBaseUrlAlsoYieldsHelpfulError() {
    TigerGlobalConfiguration.putValue(CanopyGlue.CONFIG_KEY_BASE_URL, "   ");

    assertThatThrownBy(() -> glue.addProxiedHost("a.example"))
        .isInstanceOf(CanopyGlue.CanopyGlueException.class)
        .hasMessageContaining(CanopyGlue.CONFIG_KEY_BASE_URL);
  }

  // ---- data-table parsing -------------------------------------------------

  @Test
  void parseBulkDataTable_rejectsHeaderOnlyTable() {
    DataTable headerOnly = DataTable.create(List.of(List.of("host", "matchType")));

    assertThatThrownBy(() -> CanopyGlue.parseBulkDataTable(headerOnly))
        .isInstanceOf(CanopyGlue.CanopyGlueException.class)
        .hasMessageContaining("header row");
  }

  @Test
  void parseBulkDataTable_rejectsTableWithoutHostColumn() {
    DataTable noHostColumn = DataTable.create(List.of(List.of("matchType"), List.of("EXACT")));

    assertThatThrownBy(() -> CanopyGlue.parseBulkDataTable(noHostColumn))
        .isInstanceOf(CanopyGlue.CanopyGlueException.class)
        .hasMessageContaining("'host'");
  }

  @Test
  void parseBulkDataTable_rejectsRowWithBlankHost() {
    DataTable blankHost =
        DataTable.create(List.of(List.of("host", "matchType"), List.of("", "EXACT")));

    assertThatThrownBy(() -> CanopyGlue.parseBulkDataTable(blankHost))
        .isInstanceOf(CanopyGlue.CanopyGlueException.class)
        .hasMessageContaining("'host'");
  }

  // ---- constructors -------------------------------------------------------

  @Test
  void defaultConstructor_buildsUsableGlueInstance() {
    CanopyGlue defaultGlue = new CanopyGlue();
    defaultGlue.setCanopyBaseUrl(URI.create("http://canopy.local:8080"));

    assertThat(TigerGlobalConfiguration.readString(CanopyGlue.CONFIG_KEY_BASE_URL))
        .isEqualTo("http://canopy.local:8080");
  }
}
