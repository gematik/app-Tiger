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
package de.gematik.test.tiger.proxy.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.TigerProxyTestHelper;
import io.restassured.RestAssured;
import java.util.UUID;
import kong.unirest.core.Unirest;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.jcip.annotations.NotThreadSafe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@Slf4j
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "tigerProxy.skipDisplayWhenMessageLargerThanKb = 1")
@ResetTigerConfiguration
@NotThreadSafe
@WireMockTest
class TigerWebUiControllerFullRenderTest {

  private static final String LARGE_JSON_TEMPLATE =
      """
      {"data": [%s]}\
      """;

  private static final String JSON_ITEM_TEMPLATE =
      """
      {"id": %d, "name": "Item %d", "description": "This is a detailed description for item %d \
      with lots of additional information to make the response larger than 1KB and trigger the full render \
      button functionality in the Tiger proxy web UI for proper testing of message rendering capabilities."}\
      """;

  private static final String LARGE_TEXT_RESPONSE =
      """
      Large response text with repeated content to exceed 1KB threshold. \
      This is additional content that will be repeated many times to ensure the response is larger than 1KB.\
      """
          .repeat(50);

  private static final String LARGE_JSON_RESPONSE = createLargeJsonResponse();

  private static String createLargeJsonResponse() {
    StringBuilder jsonItems = new StringBuilder();
    for (int i = 0; i < 100; i++) {
      if (i > 0) jsonItems.append(",");
      jsonItems.append(String.format(JSON_ITEM_TEMPLATE, i, i, i));
    }
    return String.format(LARGE_JSON_TEMPLATE, jsonItems);
  }

  @Autowired private TigerProxy tigerProxy;
  @LocalServerPort private int adminPort;
  private int fakeBackendServerPort;

  @BeforeEach
  void setup(WireMockRuntimeInfo runtimeInfo) {
    fakeBackendServerPort = runtimeInfo.getHttpPort();
    log.info("Started Backend-Server on port {}", fakeBackendServerPort);

    runtimeInfo.getWireMock().resetMappings();

    runtimeInfo
        .getWireMock()
        .register(
            get("/foobar").willReturn(aResponse().withStatus(200).withBody(LARGE_JSON_RESPONSE)));

    runtimeInfo
        .getWireMock()
        .register(post("/foobar").willReturn(ok().withBody(LARGE_TEXT_RESPONSE)));

    RestAssured.port = adminPort;
    RestAssured.baseURI = "http://localhost";
    RestAssured.proxy = null; // Clear any existing proxy settings for RestAssured
  }

  @Test
  void testSingleMessageRouteServesIndexHtml() {
    String testUuid = UUID.randomUUID().toString();

    given()
        .when()
        .get("/webui/message/" + testUuid)
        .then()
        .statusCode(200)
        .contentType("text/html")
        .body(containsString("<div id=\"app\"></div>"))
        .body(containsString("Tiger Proxy Log"));
  }

  @Test
  void testRootRouteServesIndexHtml() {
    given()
        .when()
        .get("/webui/")
        .then()
        .statusCode(200)
        .contentType("text/html")
        .body(containsString("<div id=\"app\"></div>"));
  }

  @Test
  void testFullRenderButtonIncludedInMessageHtml() {
    tigerProxy.clearAllMessages();

    try (val proxyRest = Unirest.spawnInstance()) {
      proxyRest.config().proxy("localhost", tigerProxy.getProxyPort());

      proxyRest.get("http://localhost:" + fakeBackendServerPort + "/foobar").asString().getStatus();
      proxyRest.post("http://localhost:" + fakeBackendServerPort + "/foobar").asString().getBody();
    }

    TigerProxyTestHelper.waitUntilMessageListInProxyContainsCountMessagesWithTimeout(
        tigerProxy, 4, 10);

    given()
        .param("fromOffset", "0")
        .param("toOffsetExcluding", "10")
        .when()
        .get("/webui/getMessagesWithHtml")
        .then()
        .statusCode(200)
        .contentType("application/json")
        .body(containsString("full-message-button"));
  }

  @Test
  void testSingleMessageApiEndpoint() {
    String testUuid = UUID.randomUUID().toString();

    given().when().get("/webui/fullyRenderedMessage/" + testUuid).then().statusCode(404);
  }

  @Test
  void testAssetRoutesStillWork() {
    given().when().get("/webui/assets/nonexistent.js").then().statusCode(404);
  }

  @Test
  void testInvalidMessageUuidHandling() {
    String invalidUuid = "invalid-uuid-format";

    given()
        .when()
        .get("/webui/message/" + invalidUuid)
        .then()
        .statusCode(200)
        .contentType("text/html");
  }
}
