/*
 * Copyright 2024 gematik GmbH
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
 */

package de.gematik.test.tiger.proxy;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.fail;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerConfigurationRoute;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.mockserver.netty.MockServer;
import de.gematik.test.tiger.proxy.data.TigerProxyRoute;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import kong.unirest.core.*;
import kong.unirest.core.json.JSONObject;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Tests that the health endpoint of the Tiger Proxy returns correct details and that a
 * non-responsive tiger proxy yields a DOWN health status.
 *
 * <p>Test methods test
 *
 * <ul>
 *   <li>that initial health report has UP status and rbel data is 0, failed request timestamp is
 *       null and last success timestamp is current (> -20sec)
 *   <li>that calls via proxy populate the rbel message list
 *   <li>that if tiger proxy is non-responsive the status turns to DOWN and the failed request
 *       timestamp is set
 * </ul>
 *
 * <p><b>Attention</b> The negative test method stops the mockserver client of the Tiger Proxy which
 * can not be restarted. Thus, the @Order annotation must be used to ensure any new test method is
 * run before the NOK test method.
 */
@TestMethodOrder(OrderAnnotation.class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "management.endpoint.health.enabled=true",
      "management.endpoint.health.probes.enabled=true",
      "management.endpoint.health.show-details=always",
      "management.endpoint.info.enabled=true",
      "management.endpoints.enabled-by-default=true",
      "management.endpoints.web.base-path=/actuator/",
      "management.endpoints.web.exposure.include=health,info",
      "management.health.livenessstate.enabled=true",
      "management.health.readinessstate.enabled=true"
    })
@WireMockTest
@RequiredArgsConstructor
@ResetTigerConfiguration
@DirtiesContext
class TigerProxyHealthEndpointTest {

  private UnirestInstance unirestInstance;

  private UnirestInstance healthUnirestInstance;

  @Autowired private TigerProxy tigerProxy;

  @Autowired ServletWebServerApplicationContext context;

  @BeforeEach
  void beforeEachLifecyleMethod(WireMockRuntimeInfo runtimeInfo) {
    tigerProxy.getRoutes().stream()
        .filter(route -> !route.getFrom().contains("tiger"))
        .forEach(tigerRoute -> tigerProxy.removeRoute(tigerRoute.getId()));

    tigerProxy.addRoute(
        TigerConfigurationRoute.builder()
            .from("http://myserv.er")
            .to("http://localhost:" + runtimeInfo.getHttpPort())
            .build());

    runtimeInfo.getWireMock().register(get("/foo").willReturn(ok().withBody("bar")));

    unirestInstance =
        new UnirestInstance(new Config().proxy("localhost", tigerProxy.getProxyPort()));
    unirestInstance.get("");
    healthUnirestInstance = Unirest.spawnInstance();
  }

  @AfterEach
  void reset() {
    tigerProxy.clearAllRoutes();
    tigerProxy.clearAllMessages();
  }

  private String getHealthEndpointUrl() {
    return "http://localhost:" + tigerProxy.getAdminPort() + "/actuator/health";
  }

  @Test
  @Order(1)
  void healthEndpointData_OK() {
    JSONObject response =
        healthUnirestInstance.get(getHealthEndpointUrl()).asJson().getBody().getObject();
    validateHealthRecord(response, "UP", "UP", true, 0, 0, false, true);
    // check that the health endpoint get requests do NOT end up in the rbel messages
    response = healthUnirestInstance.get(getHealthEndpointUrl()).asJson().getBody().getObject();
    validateHealthRecord(response, "UP", "UP", true, 0, 0, false, true);
  }

  @Test
  @Order(1)
  void healthEndpointDataAfterSomeRequests_OK() {
    unirestInstance.get("http://myserv.er/foo").asString();
    unirestInstance.get("http://myserv.er/foo").asString();
    // parsing the messages might take some time so try until result matches!
    await()
        .pollInterval(200, TimeUnit.MILLISECONDS)
        .atMost(2, TimeUnit.SECONDS)
        .until(() -> tigerProxy.getRbelMessagesList().size() == 4);
    // check that the health endpoint get requests do NOT end up in the rbel messages
    JSONObject response =
        healthUnirestInstance.get(getHealthEndpointUrl()).asJson().getBody().getObject();
    validateHealthRecord(response, "UP", "UP", true, 4, 5, false, true);
  }

  @Test
  @Order(2)
  void catchAllReverseProxyRouteExists_HealthcheckShouldStillWork() {
    tigerProxy.addRoute(
        TigerProxyRoute.builder().from("/").to("http://some.bogus.address").build());
    assertThat(healthUnirestInstance.get(getHealthEndpointUrl()).asEmpty().isSuccess()).isTrue();
  }

  @Test
  @Order(2)
  void catchAllUniversalRouteExists_HealthcheckShouldStillWork() {
    tigerProxy.addRoute(
        TigerProxyRoute.builder()
            .from("/")
            .to("http://some.bogus.address")
            .matchForProxyType(false)
            .build());
    assertThat(healthUnirestInstance.get(getHealthEndpointUrl()).asEmpty().isSuccess()).isTrue();
  }

  // ATTENTION this method must be run as last method as it destroys the mock server client inside
  // the tiger proxy
  @Test
  @Order(9999)
  void healthEndpointDataOfDownTigerProxy_NOK() {
    await()
        .pollInterval(200, TimeUnit.MILLISECONDS)
        .atMost(2, TimeUnit.SECONDS)
        .until(() -> tigerProxy.getRbelMessagesList().isEmpty());

    MockServer tigerProxyInternalMockServer =
        ((MockServer) ReflectionTestUtils.getField(tigerProxy, "mockServer"));
    if (tigerProxyInternalMockServer == null) {
      fail("No Mockserver found! Can't simulate unresponsive tiger proxy");
    }
    tigerProxyInternalMockServer.stop();

    JSONObject response =
        healthUnirestInstance.get(getHealthEndpointUrl()).asJson().getBody().getObject();
    validateHealthRecord(response, "DOWN", "DOWN", false, 0, 5, false, false);
  }

  private void validateHealthRecord(
      JSONObject response,
      String overallStatus,
      String componentStatus,
      boolean tigerProxyHealthy,
      int rbelMessageNum,
      int rbelMessageMaximalBufferSizeMb,
      boolean lastSuccessRequestIsNull,
      boolean firstFailedRequestIsNull) {
    tigerProxy.waitForAllCurrentMessagesToBeParsed();
    SoftAssertions softly = new SoftAssertions();
    softly.assertThat(response.getString("status")).as("Overall status").isEqualTo(overallStatus);
    JSONObject msgQueue = response.getJSONObject("components").getJSONObject("messageQueue");
    softly
        .assertThat(msgQueue.getString("status"))
        .as("Component status")
        .isEqualTo(componentStatus);
    JSONObject msgQueueDetails = msgQueue.getJSONObject("details");
    softly
        .assertThat(msgQueueDetails.getBoolean("tigerProxyHealthy"))
        .as("tigerProxyHealthy")
        .isEqualTo(tigerProxyHealthy);
    softly
        .assertThat(msgQueueDetails.getInt("rbelMessages"))
        .as("rbelMessages")
        .isEqualTo(rbelMessageNum);
    softly
        .assertThat(msgQueueDetails.getInt("rbelMessageBuffer"))
        .as("rbelMessageBuffer")
        .isLessThanOrEqualTo(rbelMessageMaximalBufferSizeMb * 1024);
    if (lastSuccessRequestIsNull) {
      softly
          .assertThat(msgQueueDetails.get("lastSuccessfulMockserverRequest"))
          .as("lastSuccessfulMockserverRequest")
          .isNull();
    } else {
      softly
          .assertThat(
              LocalDateTime.parse(msgQueueDetails.getString("lastSuccessfulMockserverRequest"))
                  .plus(20, ChronoUnit.SECONDS))
          .as("lastSuccessfulMockserverRequest")
          .isAfter(LocalDateTime.now());
    }
    if (firstFailedRequestIsNull) {
      softly
          .assertThat(msgQueueDetails.get("firstFailedMockserverRequest"))
          .as("firstFailedMockserverRequest")
          .isNull();
    } else {
      softly
          .assertThat(
              LocalDateTime.parse(msgQueueDetails.getString("firstFailedMockserverRequest"))
                  .plus(20, ChronoUnit.SECONDS))
          .as("firstFailedMockserverRequest")
          .isAfter(LocalDateTime.now());
    }
    softly.assertAll();
  }
}
