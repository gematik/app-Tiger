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
package de.gematik.test.tiger.canopy.integrationtest;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import de.gematik.test.tiger.canopy.Constants;
import de.gematik.test.tiger.canopy.client.config.MatchType;
import de.gematik.test.tiger.canopy.registry.ProxiedHostRegistry;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * End-to-end stage-2 control bridge test: brings up the full CANOPY Spring context with a WireMock
 * standing in for the Tiger proxy admin API and verifies that registry mutations propagate.
 */
@SpringBootTest(
    properties = {
      "canopy.dnsPort=0",
      "canopy.controlMode=ROUTE_PER_HOST",
      "server.port=0",
      "spring.main.web-application-type=servlet"
    })
class CanopyProxyBridgeIT {

  private static WireMockServer wm;

  @Autowired private ProxiedHostRegistry registry;

  @BeforeAll
  static void start() {
    wm = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    wm.start();
    wm.stubFor(
        put(urlEqualTo(Constants.ROUTE_ENDPOINT))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"id":"r-it-1"}""")));
    wm.stubFor(
        delete(urlPathEqualTo(Constants.ROUTE_ENDPOINT + "/r-it-1"))
            .willReturn(aResponse().withStatus(200)));
  }

  @AfterAll
  static void stop() {
    if (wm != null) {
      wm.stop();
    }
  }

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("canopy.tigerProxyUrl", () -> "http://localhost:" + wm.port());
  }

  @Test
  void registryMutationsPropagateToProxy() {
    registry.add("svc.example", MatchType.EXACT);

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> wm.verify(WireMock.putRequestedFor(urlEqualTo(Constants.ROUTE_ENDPOINT))));

    registry.remove("svc.example");

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                wm.verify(
                    WireMock.deleteRequestedFor(
                        urlPathEqualTo(Constants.ROUTE_ENDPOINT + "/r-it-1"))));
  }
}
