/*
 * Copyright 2025 gematik GmbH
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

package de.gematik.test.tiger.proxy.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.StringContains.containsString;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import de.gematik.rbellogger.data.RbelElementAssertion;
import de.gematik.rbellogger.data.facet.TracingMessagePairFacet;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.TigerProxyTestHelper;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.jcip.annotations.NotThreadSafe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
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
class TigerWebUiControllerTest {

  @Autowired private TigerProxy tigerProxy;
  @LocalServerPort private int adminPort;
  private static final int TOTAL_OF_EXCHANGED_MESSAGES = 4;

  @BeforeEach
  public void setupBackendServer(WireMockRuntimeInfo runtimeInfo) {
    int fakeBackendServerPort = runtimeInfo.getHttpPort();
    log.info("Started Backend-Server on port {}", fakeBackendServerPort);

    runtimeInfo.getWireMock().resetMappings();
    runtimeInfo
        .getWireMock()
        .register(
            get("/foobar").willReturn(aResponse().withStatus(666).withBody("{\"foo\":\"bar\"}")));

    runtimeInfo.getWireMock().register(post("/foobar").willReturn(ok().withBody("")));

    RestAssured.proxy = null;

    tigerProxy.clearAllMessages();

    try (val proxyRest = Unirest.spawnInstance()) {
      proxyRest.config().proxy("localhost", tigerProxy.getProxyPort());

      System.out.println(
          proxyRest
              .get("http://localhost:" + fakeBackendServerPort + "/foobar")
              .asString()
              .getStatus());
      System.out.println(
          proxyRest
              .post("http://localhost:" + fakeBackendServerPort + "/foobar")
              .asString()
              .getBody());
    }

    TigerProxyTestHelper.waitUntilMessageListInProxyContainsCountMessagesWithTimeout(
        tigerProxy, 4, 10);
  }

  @AfterEach
  public void tearDown() {
    tigerProxy.clearAllMessages();
  }

  public String getWebUiUrl() {
    log.info("Connecting to {}", "http://localhost:" + adminPort + "/webui");
    return "http://localhost:" + adminPort + "/webui";
  }

  @Test
  @ResourceLock(value = "TigerWebUiController")
  void checkMsgIsReturned() {
    RestAssured.given()
        .get(getWebUiUrl() + "/getMessagesWithHtml?fromOffset=0&toOffsetExcluding=100")
        .then()
        .statusCode(200)
        .body("messages.size()", equalTo(TOTAL_OF_EXCHANGED_MESSAGES))
        .body("messages[0].uuid", equalTo(tigerProxy.getRbelMessagesList().get(0).getUuid()))
        .body("messages[1].uuid", equalTo(tigerProxy.getRbelMessagesList().get(1).getUuid()));
  }

  @Test
  @ResourceLock(value = "TigerWebUiController")
  void checkRbelExpression_invalid() {
    final var uuid = tigerProxy.getRbelMessagesList().get(0).getUuid();
    RestAssured.given()
        .get(getWebUiUrl() + "/testRbelExpression?query='*&messageUuid=" + uuid)
        .then()
        .statusCode(200)
        .body("messageUuid", equalTo(uuid))
        .body("query", equalTo("'*"))
        .body("errorMessage", containsString("RbelPath expressions always start with"));
  }

  @Test
  @ResourceLock(value = "TigerWebUiController")
  void checkJexlQuery_invalid() {
    final var uuid = tigerProxy.getRbelMessagesList().get(0).getUuid();
    RestAssured.given()
        .get(getWebUiUrl() + "/testJexlQuery?query='*&messageUuid=" + uuid)
        .then()
        .statusCode(200)
        .body("messageUuid", equalTo(uuid))
        .body("query", equalTo("'*"))
        .body("errorMessage", containsString("Error while parsing expression"));
  }

  @Test
  @ResourceLock(value = "TigerWebUiController")
  void checkIfParametersAreReplayed_testJexlQuery() {
    final var uuid = tigerProxy.getRbelMessagesList().get(0).getUuid();
    RestAssured.given()
        .get(getWebUiUrl() + "/testJexlQuery?query=$.*&messageUuid=" + uuid)
        .then()
        .statusCode(200)
        .body("messageUuid", equalTo(uuid))
        .body("query", equalTo("$.*"));
  }

  @Test
  @ResourceLock(value = "TigerWebUiController")
  void checkIfParametersAreReplayed_testRbelExpression() {
    final var uuid = tigerProxy.getRbelMessagesList().get(0).getUuid();
    RestAssured.given()
        .get(getWebUiUrl() + "/testRbelExpression?query=$.*&messageUuid=" + uuid)
        .then()
        .statusCode(200)
        .body("messageUuid", equalTo(uuid))
        .body("query", equalTo("$.*"));
  }

  @Test
  @ResourceLock(value = "TigerWebUiController")
  void checkIfParametersAreReplayed_testFilterMessages() {
    RestAssured.given()
        .get(getWebUiUrl() + "/testFilterMessages?filterRbelPath=isRequest")
        .then()
        .statusCode(200)
        .body("filter.rbelPath", equalTo("isRequest"));
  }

  @Test
  @ResourceLock(value = "TigerWebUiController")
  void checkIfParametersAreReplayed_searchMessages() {
    RestAssured.given()
        .get(getWebUiUrl() + "/searchMessages?filterRbelPath=isRequest&searchRbelPath=isRequest")
        .then()
        .statusCode(200)
        .body("filter.rbelPath", equalTo("isRequest"))
        .body("searchFilter.rbelPath", equalTo("isRequest"));
  }

  @Test
  @ResourceLock(value = "TigerWebUiController")
  void checkIfParametersAreReplayed_getMessagesWithHtml() {
    RestAssured.given()
        .get(
            getWebUiUrl()
                + "/getMessagesWithHtml?fromOffset=100&toOffsetExcluding=200&filterRbelPath=isRequest")
        .then()
        .statusCode(200)
        .body("fromOffset", equalTo(100))
        .body("toOffsetExcluding", equalTo(200))
        .body("filter.rbelPath", equalTo("isRequest"));
  }

  @Test
  @ResourceLock(value = "TigerWebUiController")
  void checkIfParametersAreReplayed_getMessagesWithMeta() {
    RestAssured.given()
        .get(getWebUiUrl() + "/getMessagesWithMeta?filterRbelPath=isRequest")
        .then()
        .statusCode(200)
        .body("filter.rbelPath", equalTo("isRequest"));
  }

  @Test
  @ResourceLock(value = "TigerWebUiController")
  void checkIfFilterMessagesReturnsErrorOnInvalidRbelPath() {
    RestAssured.given()
        .get(getWebUiUrl() + "/testFilterMessages?filterRbelPath=blablub")
        .then()
        .statusCode(200)
        .body("searchFilter", equalTo(null))
        .body("errorMessage", containsString("blablub"));
  }

  @Test
  @ResourceLock(value = "TigerWebUiController")
  void checkIfSearchMessagesReturnsErrorOnInvalidRbelPath() {
    RestAssured.given()
        .get(getWebUiUrl() + "/searchMessages?filterRbelPath=isRequest&searchRbelPath=blablub")
        .then()
        .statusCode(200)
        .body("filter.rbelPath", equalTo("isRequest"))
        .body("searchFilter.rbelPath", equalTo("blablub"))
        .body("errorMessage", containsString("blablub"));
  }

  @Test
  @ResourceLock(value = "TigerWebUiController")
  void checkIfSearchMessagesReturnsFilteredAndSearchedMessages() {
    RestAssured.given()
        .get(
            getWebUiUrl()
                + "/searchMessages?filterRbelPath=isRequest&searchRbelPath=$.body.foo == \"bar\"")
        .then()
        .statusCode(200)
        .body("filter.rbelPath", equalTo("isRequest"))
        .body("searchFilter.rbelPath", equalTo("$.body.foo == \"bar\""))
        .body("errorMessage", equalTo(null));
  }

  @Test
  @ResourceLock(value = "TigerWebUiController")
  void checkNoMsgIsReturnedIfNoneExistsAfterRequested() {
    RestAssured.given()
        .get(getWebUiUrl() + "/getMessagesWithHtml?fromOffset=100&toOffsetExcluding=200")
        .then()
        .statusCode(200)
        .body("messages.size()", equalTo(0))
        .body("totalFiltered", equalTo(0))
        .body("total", equalTo(TOTAL_OF_EXCHANGED_MESSAGES));
  }

  @Test
  @ResourceLock(value = "TigerWebUiController")
  void checkNoMsgIsReturnedAfterReset() {
    RestAssured.given().get(getWebUiUrl() + "/resetMessages").then().statusCode(200);

    RestAssured.given()
        .get(getWebUiUrl() + "/getMessagesWithHtml?fromOffset=0&toOffsetExcluding=100")
        .then()
        .statusCode(200)
        .body("messages.size()", equalTo(0))
        .body("total", equalTo(0));
  }

  @Test
  @ResourceLock(value = "TigerWebUiController")
  void filterOutResponses_shouldStillAppearInPairs() {
    RestAssured.given()
        .get(
            getWebUiUrl()
                + "/getMessagesWithHtml?fromOffset=0&toOffsetExcluding=100&filterRbelPath=isRequest")
        .then()
        .statusCode(200)
        .body("messages.size()", equalTo(TOTAL_OF_EXCHANGED_MESSAGES))
        .body("total", equalTo(TOTAL_OF_EXCHANGED_MESSAGES));
  }

  @Test
  @ResourceLock(value = "TigerWebUiController")
  void filterOutRequests_shouldStillAppearInPairs() {
    RestAssured.given()
        .get(
            getWebUiUrl()
                + "/getMessagesWithHtml?fromOffset=0&toOffsetExcluding=100&filterRbelPath=isResponse")
        .then()
        .statusCode(200)
        .body("messages.size()", equalTo(TOTAL_OF_EXCHANGED_MESSAGES))
        .body("total", equalTo(TOTAL_OF_EXCHANGED_MESSAGES));
  }

  @Test
  @ResourceLock(value = "TigerWebUiController")
  void checkAllTrafficSuppliedWhenDownloadWithoutFilteredUuids() {
    RestAssured.given()
        .get(getWebUiUrl() + "/trafficLog.tgr")
        .then()
        .statusCode(200)
        .body(
            containsString(
                "\"uuid\":\"%s\"".formatted(tigerProxy.getRbelMessagesList().get(0).getUuid())))
        .body(
            containsString(
                "\"uuid\":\"%s\"".formatted(tigerProxy.getRbelMessagesList().get(1).getUuid())))
        .body(
            containsString(
                "\"uuid\":\"%s\"".formatted(tigerProxy.getRbelMessagesList().get(2).getUuid())))
        .body(
            containsString(
                "\"uuid\":\"%s\"".formatted(tigerProxy.getRbelMessagesList().get(3).getUuid())));
  }

  @Test
  @ResourceLock(value = "TigerWebUiController")
  void simulateTrafficDownloadResetAndUpload() {
    final String downloadedTraffic =
        RestAssured.given().get(getWebUiUrl() + "/trafficLog.tgr").body().asString();

    RestAssured.given().get(getWebUiUrl() + "/resetMessages").then().statusCode(200);

    assertThat(tigerProxy.getRbelMessages()).isEmpty();

    RestAssured.with()
        .body(downloadedTraffic)
        .post(getWebUiUrl() + "/importTraffic")
        .then()
        .statusCode(200);

    TigerProxyTestHelper.waitUntilMessageListInProxyContainsCountMessagesWithTimeout(
        tigerProxy, TOTAL_OF_EXCHANGED_MESSAGES, 20);
  }

  @Test
  @ResourceLock(value = "TigerWebUiController")
  void downloadTraffic_without_filterRbelPath() {
    final Response response = RestAssured.given().get(getWebUiUrl() + "/trafficLog12334.tgr");
    log.info("Response: {}", response.asString());
    response
        .then()
        .statusCode(200)
        .header("available-messages", String.valueOf(TOTAL_OF_EXCHANGED_MESSAGES))
        .body(
            containsString(
                "\"uuid\":\"%s\"".formatted(tigerProxy.getRbelMessagesList().get(0).getUuid())))
        .body(
            containsString(
                "\"uuid\":\"%s\"".formatted(tigerProxy.getRbelMessagesList().get(1).getUuid())))
        .body(
            containsString(
                "\"uuid\":\"%s\"".formatted(tigerProxy.getRbelMessagesList().get(2).getUuid())))
        .body(
            containsString(
                "\"uuid\":\"%s\"".formatted(tigerProxy.getRbelMessagesList().get(3).getUuid())));
  }

  @Test
  @ResourceLock(value = "TigerWebUiController")
  void downloadTraffic_with_filterRbelPath() {
    String filterRbelPath = "$.method == 'POST'";
    RestAssured.given()
        .get(getWebUiUrl() + "/trafficLog12334.tgr?filterRbelPath=" + filterRbelPath)
        .then()
        .statusCode(200)
        .header("available-messages", String.valueOf(2))
        .body(
            not(
                containsString(
                    "\"uuid\":\"%s\""
                        .formatted(tigerProxy.getRbelMessagesList().get(0).getUuid()))))
        .body(
            not(
                containsString(
                    "\"uuid\":\"%s\""
                        .formatted(tigerProxy.getRbelMessagesList().get(1).getUuid()))))
        .body(
            containsString(
                "\"uuid\":\"%s\"".formatted(tigerProxy.getRbelMessagesList().get(2).getUuid())))
        .body(
            containsString(
                "\"uuid\":\"%s\"".formatted(tigerProxy.getRbelMessagesList().get(3).getUuid())));
  }

  @Test
  @ResourceLock(value = "TigerWebUiController")
  void uploadingTrafficFile_processesPairedMessageUuid() {
    String filterRbelPath = "$.method == 'POST'";

    var trafficFileContent =
        RestAssured.given()
            .get(getWebUiUrl() + "/trafficLog12334.tgr?filterRbelPath=" + filterRbelPath);
    trafficFileContent
        .then()
        .statusCode(200)
        .header("available-messages", String.valueOf(TOTAL_OF_EXCHANGED_MESSAGES - 2));

    RestAssured.given().get(getWebUiUrl() + "/resetMessages");
    assertThat(tigerProxy.getRbelMessages()).isEmpty();

    RestAssured.with()
        .body(trafficFileContent.asString())
        .post(getWebUiUrl() + "/importTraffic")
        .then()
        .statusCode(200);

    var rbelMessages = tigerProxy.getRbelMessagesList();

    assertThat(rbelMessages).size().isEqualTo(TOTAL_OF_EXCHANGED_MESSAGES - 2);
    RbelElementAssertion.assertThat(rbelMessages.get(0)).hasFacet(TracingMessagePairFacet.class);
    RbelElementAssertion.assertThat(rbelMessages.get(1)).hasFacet(TracingMessagePairFacet.class);

    var requestFacet = rbelMessages.get(0).getFacetOrFail(TracingMessagePairFacet.class);
    var responseFacet = rbelMessages.get(1).getFacetOrFail(TracingMessagePairFacet.class);

    assertThat(requestFacet.getResponse().getUuid()).isEqualTo(rbelMessages.get(1).getUuid());
    assertThat(responseFacet.getRequest().getUuid()).isEqualTo(rbelMessages.get(0).getUuid());
  }
}
