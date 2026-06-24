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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import de.gematik.test.tiger.canopy.Constants;
import de.gematik.test.tiger.canopy.config.CanopyConfiguration;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class TigerProxyAdminClientTest {

  private static final String CONTENT_TYPE_HEADER = "Content-Type";
  private static final String APPLICATION_JSON = "application/json";

  private static final String ADD_ROUTE_REQUEST =
      """
      {"from":"https://foo.example","to":"https://foo.example"}""";

  private static final String ADD_ROUTE_RESPONSE =
      """
      {"id":"route-1","from":"https://foo.example","to":"https://foo.example"}""";

  private static final String ADD_ROUTE_RESPONSE_R2 =
      """
      {"id":"r-2"}""";

  private static final String ADD_ROUTE_RESPONSE_ABC =
      """
      {"id":"route-abc"}""";

  private static final String ADD_ROUTE_RESPONSE_ERR =
      """
      {"id":"route-err"}""";

  private WireMockServer wm;
  private TigerProxyAdminClient client;
  private CanopyConfiguration configuration;

  @BeforeEach
  void setup() {
    wm = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    wm.start();
    configuration = new CanopyConfiguration();
    configuration.setTigerProxyUrl("http://localhost:" + wm.port());
    client = new TigerProxyAdminClient(configuration, RestClient.builder());
  }

  @AfterEach
  void tearDown() {
    wm.stop();
  }

  @Test
  void addRoutePutsRouteAndTracksId() {
    wm.stubFor(
        put(urlEqualTo(Constants.ROUTE_ENDPOINT))
            .withHeader(CONTENT_TYPE_HEADER, equalTo(APPLICATION_JSON))
            .withRequestBody(equalToJson(ADD_ROUTE_REQUEST))
            .willReturn(
                aResponse()
                    .withStatus(TestConstants.HTTP_OK)
                    .withHeader(CONTENT_TYPE_HEADER, APPLICATION_JSON)
                    .withBody(ADD_ROUTE_RESPONSE)));

    client.addRoute("foo.example", Optional.empty());

    wm.verify(1, WireMock.putRequestedFor(urlEqualTo(Constants.ROUTE_ENDPOINT)));
  }

  @Test
  void addRouteHandlesServerError() {
    wm.stubFor(put(urlEqualTo(Constants.ROUTE_ENDPOINT)).willReturn(serverError()));

    Assertions.assertDoesNotThrow(() -> client.addRoute("foo.example", Optional.empty()));
  }

  @Test
  void addRouteHandlesTrailingSlashInProxyUrl() {
    configuration.setTigerProxyUrl("http://localhost:" + wm.port() + "/");
    wm.stubFor(
        put(urlEqualTo(Constants.ROUTE_ENDPOINT))
            .willReturn(
                aResponse()
                    .withStatus(TestConstants.HTTP_OK)
                    .withHeader(CONTENT_TYPE_HEADER, APPLICATION_JSON)
                    .withBody(ADD_ROUTE_RESPONSE_R2)));

    client.addRoute("bar.example", Optional.empty());

    wm.verify(1, WireMock.putRequestedFor(urlEqualTo(Constants.ROUTE_ENDPOINT)));
  }

  @Test
  void deleteRouteForHostSendsDeleteToCorrectId() {
    wm.stubFor(
        put(urlEqualTo(Constants.ROUTE_ENDPOINT))
            .willReturn(
                aResponse()
                    .withStatus(TestConstants.HTTP_OK)
                    .withHeader(CONTENT_TYPE_HEADER, APPLICATION_JSON)
                    .withBody(ADD_ROUTE_RESPONSE_ABC)));
    wm.stubFor(
        delete(urlPathEqualTo(Constants.ROUTE_ENDPOINT + "/route-abc"))
            .willReturn(aResponse().withStatus(TestConstants.HTTP_OK)));

    client.addRoute("test.example", Optional.empty());
    client.deleteRouteForHost("test.example", Optional.empty());

    wm.verify(1, deleteRequestedFor(urlPathEqualTo(Constants.ROUTE_ENDPOINT + "/route-abc")));
  }

  @Test
  void deleteRouteForHostSwallowsErrors() {
    wm.stubFor(
        put(urlEqualTo(Constants.ROUTE_ENDPOINT))
            .willReturn(
                aResponse()
                    .withStatus(TestConstants.HTTP_OK)
                    .withHeader(CONTENT_TYPE_HEADER, APPLICATION_JSON)
                    .withBody(ADD_ROUTE_RESPONSE_ERR)));
    wm.stubFor(
        delete(urlPathEqualTo(Constants.ROUTE_ENDPOINT + "/route-err")).willReturn(serverError()));

    client.addRoute("error.example", Optional.empty());
    Assertions.assertDoesNotThrow(
        () -> client.deleteRouteForHost("error.example", Optional.empty()));
  }

  @Test
  @SuppressWarnings("java:S5778")
  void addRouteThrowsWhenProxyUrlMissing() {
    configuration.setTigerProxyUrl(null);

    assertThatThrownBy(() -> client.addRoute("foo.example", Optional.empty()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("tigerProxyUrl");
  }
}
