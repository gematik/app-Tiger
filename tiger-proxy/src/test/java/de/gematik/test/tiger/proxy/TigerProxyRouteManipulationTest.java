/*
 *
 * Copyright 2021-2025 gematik GmbH
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
package de.gematik.test.tiger.proxy;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.fail;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerConfigurationRoute;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.proxy.data.TigerRouteDto;
import java.util.List;
import kong.unirest.core.*;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@RequiredArgsConstructor
@ResetTigerConfiguration
@WireMockTest
class TigerProxyRouteManipulationTest {

  private UnirestInstance unirestInstance;
  @Autowired private TigerProxy tigerProxy;
  private int backendServerPort;

  @BeforeEach
  public void beforeEachLifecyleMethod(WireMockRuntimeInfo runtimeInfo) {
    tigerProxy.getRoutes().stream()
        .filter(route -> !route.getFrom().contains("tiger"))
        .forEach(tigerRoute -> tigerProxy.removeRoute(tigerRoute.getId()));

    tigerProxy.addRoute(
        TigerConfigurationRoute.builder()
            .from("http://myserv.er")
            .to("http://localhost:" + runtimeInfo.getHttpPort())
            .build());

    runtimeInfo.getWireMock().register(get("/foo").willReturn(ok().withBody("bar")));

    backendServerPort = runtimeInfo.getHttpPort();

    unirestInstance =
        new UnirestInstance(new Config().proxy("localhost", tigerProxy.getProxyPort()));
    unirestInstance.get("");
  }

  @AfterEach
  public void reset() {
    tigerProxy.clearAllRoutes();
  }

  @Test
  void shouldHonorConfiguredRoutes() {
    assertThat(unirestInstance.get("http://myserv.er/foo").asString().getBody()).isEqualTo("bar");
  }

  @Test
  void addRoute_shouldWork() {
    unirestInstance
        .put("http://tiger.proxy/route")
        .header("Content-Type", "application/json")
        .body(
            "{\"from\":\"http://anderer.server\","
                + "\"to\":\"http://localhost:"
                + backendServerPort
                + "\"}")
        .asEmpty()
        .ifFailure(response -> fail("Cant reach tiger proxy (" + response.toString() + ")"));

    assertThat(unirestInstance.get("http://anderer.server/foo").asString().getBody())
        .isEqualTo("bar");
  }

  @Test
  void deleteRoute_shouldWork() {
    assertThatThrownBy(() -> unirestInstance.get("http://temp.server/foo").asEmpty())
        .isInstanceOf(UnirestException.class);

    String routeId =
        unirestInstance
            .put("http://tiger.proxy/route")
            .header("Content-Type", "application/json")
            .body(
                "{\"from\":\"http://temp.server\","
                    + "\"to\":\"http://localhost:"
                    + backendServerPort
                    + "\"}")
            .asObject(TigerRouteDto.class)
            .getBody()
            .getId();

    assertThat(unirestInstance.get("http://temp.server/foo").asEmpty().getStatus()).isEqualTo(200);
    assertThat(routeId).isNotBlank().isNotNull();

    unirestInstance
        .delete("http://tiger.proxy/route/" + routeId)
        .asString()
        .ifFailure(
            response ->
                fail(
                    "Error while deleting Route: "
                        + response.getStatus()
                        + " "
                        + response.getBody()));

    assertThatThrownBy(() -> unirestInstance.get("http://temp.server/foo").asEmpty())
        .isInstanceOf(UnirestException.class);
  }

  @Test
  void getRoutes_shouldGiveAllRoutes() {
    final HttpResponse<List<TigerRouteDto>> tigerRoutesResponse =
        unirestInstance.get("http://tiger.proxy/route").asObject(new GenericType<>() {});

    assertThat(tigerRoutesResponse.getStatus()).isEqualTo(200);
    var tigerRoutes = tigerRoutesResponse.getBody();
    assertThat(tigerRoutes)
        .extracting(TigerRouteDto::getFrom)
        .contains("http://tiger.proxy", "http://myserv.er");
  }
}
