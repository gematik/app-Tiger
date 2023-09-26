/*
 * Copyright (c) 2023 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.proxy.controller;

import de.gematik.rbellogger.data.RbelElementAssertion;
import de.gematik.rbellogger.data.facet.RbelMessageTimingFacet;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.data.TracingMessagePairFacet;
import io.restassured.RestAssured;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.RandomStringUtils;
import org.jsoup.Jsoup;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.StringContains.containsString;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "tigerProxy.skipDisplayWhenMessageLargerThanKb = 1")
@ResetTigerConfiguration
@ExtendWith(MockServerExtension.class)
public class TigerWebUiControllerTest {

    @Autowired
    private TigerProxy tigerProxy;
    @LocalServerPort
    private int adminPort;
    private static int fakeBackendServerPort;
    private static final int TOTAL_OF_EXCHANGED_MESSAGES = 4;

    @BeforeAll
    public static void setupBackendServer(MockServerClient fakeBackendServerClient) {
        fakeBackendServerPort = fakeBackendServerClient.getPort();
        log.info("Started Backend-Server on ports {}", fakeBackendServerPort);

        fakeBackendServerClient.when(request()
                .withMethod("GET")
                .withPath("/foobar.*"))
            .respond(response()
                .withStatusCode(666)
                .withBody("{\"foo\":\"bar\"}"));

        fakeBackendServerClient.when(request()
                .withMethod("POST")
                .withPath("/foobar.*"))
                .respond( response().withStatusCode(200)
                        .withBody(""));

        RestAssured.proxy = null;
    }

    @BeforeEach
    public void configureTigerProxy() {
        if (tigerProxy.getRbelMessages().isEmpty()) {
            val proxyRest = Unirest.spawnInstance();
            proxyRest.config().proxy("localhost", tigerProxy.getProxyPort());

            proxyRest.get("http://localhost:" + fakeBackendServerPort + "/foobar").asJson();

            proxyRest.post("http://localhost:" + fakeBackendServerPort + "/foobar").asJson();
        }
    }

    @AfterEach
    public void tearDown(){
        tigerProxy.clearAllMessages();
    }

    public String getWebUiUrl() {
        log.info("Connecting to {}", "http://localhost:" + adminPort + "/webui");
        log.info("Connected to server: {}", Unirest.get("http://localhost:" + adminPort + "/webui/getMsgAfter")
            .asString()
            .getStatus());
        return "http://localhost:" + adminPort + "/webui";
    }

    @Test
    void checkHtmlIsReturned() {
        RestAssured.given().get(getWebUiUrl())
            .then()
            .statusCode(200)
            .body(containsString("msgList"));
    }

    @Test
    void checkMsgIsReturned() {
        RestAssured.given().get(getWebUiUrl() + "/getMsgAfter")
            .then()
            .statusCode(200)
            .body("metaMsgList.size()", equalTo(TOTAL_OF_EXCHANGED_MESSAGES))
            .body("metaMsgList[0].uuid", equalTo(tigerProxy.getRbelMessagesList().get(0).getUuid()))
            .body("metaMsgList[1].uuid", equalTo(tigerProxy.getRbelMessagesList().get(1).getUuid()));
    }

    @Test
    void checkOnlyOneMsgIsReturnedWithLastMsgUuidSupplied() {
        RestAssured.given()
            .get(getWebUiUrl() + "/getMsgAfter?lastMsgUuid=" + tigerProxy.getRbelMessagesList().get(0).getUuid())
            .then()
            .statusCode(200)
            .body("metaMsgList.size()", equalTo(TOTAL_OF_EXCHANGED_MESSAGES-1))
            .body("metaMsgList[0].uuid", equalTo(tigerProxy.getRbelMessagesList().get(1).getUuid()));
    }

    @Test
    void checkAllTrafficSuppliedWhenDownloadWithoutFilteredUuids() {
        RestAssured.given().get(getWebUiUrl() + "/trafficLog.tgr")
                .then()
                .statusCode(200)
                .body(containsString("\"uuid\":\"%s\"".formatted(tigerProxy.getRbelMessagesList().get(0).getUuid())))
                .body(containsString("\"uuid\":\"%s\"".formatted(tigerProxy.getRbelMessagesList().get(1).getUuid())))
                .body(containsString("\"uuid\":\"%s\"".formatted(tigerProxy.getRbelMessagesList().get(2).getUuid())))
                .body(containsString("\"uuid\":\"%s\"".formatted(tigerProxy.getRbelMessagesList().get(3).getUuid())));
    }

    @Test
    void checkSuppliedUuidsAreFilteredOutWhenDownloadingTraffic() {
        RestAssured.given()
                .get(getWebUiUrl() + "/trafficLog.tgr?lastMsgUuid=" + tigerProxy.getRbelMessagesList().get(0).getUuid())
                .then()
                .statusCode(200)
                .body(not(containsString("\"uuid\":\"%s\"".formatted(tigerProxy.getRbelMessagesList().get(0).getUuid()))))
                .body(containsString("\"uuid\":\"%s\"".formatted(tigerProxy.getRbelMessagesList().get(1).getUuid())))
                .body(containsString("\"uuid\":\"%s\"".formatted(tigerProxy.getRbelMessagesList().get(2).getUuid())))
                .body(containsString("\"uuid\":\"%s\"".formatted(tigerProxy.getRbelMessagesList().get(3).getUuid())));
    }

    @Test
    void checkNoMsgIsReturnedIfNoneExistsAfterRequested() {
        RestAssured.given()
            .get(getWebUiUrl() + "/getMsgAfter?lastMsgUuid=" + tigerProxy.getRbelMessagesList().get(TOTAL_OF_EXCHANGED_MESSAGES-1).getUuid())
            .then()
            .statusCode(200)
            .body("metaMsgList.size()", equalTo(0));
    }


    @Test
    void checkNoMsgIsReturnedAfterReset() {
        RestAssured.given().get(getWebUiUrl() + "/resetMsgs")
            .then()
            .statusCode(200);

        RestAssured.given().get(getWebUiUrl() + "/getMsgAfter")
            .then()
            .statusCode(200)
            .body("metaMsgList.size()", equalTo(0));
    }

    @Test
    void checkCorrectMenuStringsAreSupplied() {
        RestAssured.given()
            .get(getWebUiUrl() + "/getMsgAfter")
            .then()
            .statusCode(200)
            .body("metaMsgList.size()", equalTo(TOTAL_OF_EXCHANGED_MESSAGES))
            .body("metaMsgList[0].menuInfoString", equalTo("GET /foobar"))
            .body("metaMsgList[1].menuInfoString", equalTo("666"));

        //Somewhere the zeros are omitted (see: https://stackoverflow.com/questions/72008690/jackson-and-localdatetime-trailing-zeros-are-removed)
        //Since they are not relevant for the UI, we just make a better assertion.
        String timestamp = RestAssured.given().get(getWebUiUrl() + "/getMsgAfter").then().extract()
            .path("metaMsgList[1].timestamp");

        assertThat(OffsetDateTime.parse(timestamp)).isEqualTo(tigerProxy.getRbelMessagesList().get(1)
            .getFacetOrFail(RbelMessageTimingFacet.class).getTransmissionTime().toOffsetDateTime());
    }

    @Test
    void simulateTrafficDownloadResetAndUpload() {
        final String downloadedTraffic = RestAssured.given()
                .get(getWebUiUrl() + "/trafficLog.tgr")
                .body().asString();

        RestAssured.given().get(getWebUiUrl() + "/resetMsgs")
                .then()
                .statusCode(200);

        assertThat(tigerProxy.getRbelMessages()).isEmpty();

        RestAssured
                .with().body(downloadedTraffic)
                .post(getWebUiUrl() + "/traffic")
                .then()
                .statusCode(200);

        assertThat(tigerProxy.getRbelMessages()).hasSize(TOTAL_OF_EXCHANGED_MESSAGES);

    }

    @Test
    void filterOutResponses_shouldStillAppearInPairs() {
        RestAssured.given()
            .get(getWebUiUrl() + "/getMsgAfter?filterCriterion=isRequest")
            .then()
            .statusCode(200)
            .body("metaMsgList.size()", equalTo(TOTAL_OF_EXCHANGED_MESSAGES));
    }

    @Test
    void filterOutRequests_shouldStillAppearInPairs() {
        RestAssured.given()
            .get(getWebUiUrl() + "/getMsgAfter?filterCriterion=isResponse")
            .then()
            .statusCode(200)
            .body("metaMsgList.size()", equalTo(TOTAL_OF_EXCHANGED_MESSAGES));
    }

    @Test
    void largeMessage_shouldNotBeRenderedCompletelyButStillAppear() {
        tigerProxy.clearAllMessages();

        val proxyRest = Unirest.spawnInstance();
        proxyRest.config().proxy("localhost", tigerProxy.getProxyPort());
        final String longString = RandomStringUtils.randomAlphanumeric(2_000);
        proxyRest.post("http://localhost:" + fakeBackendServerPort + "/foobar")
            .body("{'randomStringForLulz':'" + longString + "'}")
            .asString();
        await()
            .until(() -> tigerProxy.getRbelMessages().size() >= 2);

        final JsonNode body = Unirest.get(getWebUiUrl() + "/getMsgAfter").asJson().getBody();
        System.out.println(body.toString());
        assertThat(body.getObject().getJSONArray("htmlMsgList").getJSONObject(0).getString("html"))
            .contains("foobar")
            .doesNotContain(longString);
    }

    @Test
    void downloadTraffic_withoutFilterCriterion(){
        RestAssured.given()
                .get(getWebUiUrl()+"/trafficLog12334.tgr")
                .then()
                .statusCode(200)
                .header("available-messages", String.valueOf(TOTAL_OF_EXCHANGED_MESSAGES))
                .body(containsString("\"uuid\":\"%s\"".formatted(tigerProxy.getRbelMessagesList().get(0).getUuid())))
                .body(containsString("\"uuid\":\"%s\"".formatted(tigerProxy.getRbelMessagesList().get(1).getUuid())))
                .body(containsString("\"uuid\":\"%s\"".formatted(tigerProxy.getRbelMessagesList().get(2).getUuid())))
                .body(containsString("\"uuid\":\"%s\"".formatted(tigerProxy.getRbelMessagesList().get(3).getUuid())));

    }

    @Test
    void downloadTraffic_withFilterCriterion(){
        String filterCriterion = "$.method == 'POST'";
        RestAssured.given()
                .get(getWebUiUrl()+"/trafficLog12334.tgr?filterCriterion="+filterCriterion)
                .then()
                .statusCode(200)
                .header("available-messages", String.valueOf(2))
                .body(not(containsString("\"uuid\":\"%s\"".formatted(tigerProxy.getRbelMessagesList().get(0).getUuid()))))
                .body(not(containsString("\"uuid\":\"%s\"".formatted(tigerProxy.getRbelMessagesList().get(1).getUuid()))))
                .body(containsString("\"uuid\":\"%s\"".formatted(tigerProxy.getRbelMessagesList().get(2).getUuid())))
                .body(containsString("\"uuid\":\"%s\"".formatted(tigerProxy.getRbelMessagesList().get(3).getUuid())));

    }

    @Test
    void downloadHtml_withoutFilterCriterion(){
        var response = RestAssured.given()
                .get(getWebUiUrl() + "/tiger-report12345.html");

        response.then()
                .statusCode(200)
                .contentType(MediaType.TEXT_HTML_VALUE);

        Elements htmlElements = Jsoup.parse(response.body().asString())
                .body().select(".msg-card");

        assertThat(htmlElements).size().isEqualTo(TOTAL_OF_EXCHANGED_MESSAGES);
        assertThat(htmlElements.get(0).html()).contains(tigerProxy.getRbelMessagesList().get(0).getUuid());
        assertThat(htmlElements.get(1).html()).contains(tigerProxy.getRbelMessagesList().get(1).getUuid());
        assertThat(htmlElements.get(2).html()).contains(tigerProxy.getRbelMessagesList().get(2).getUuid());
        assertThat(htmlElements.get(3).html()).contains(tigerProxy.getRbelMessagesList().get(3).getUuid());
    }

    @Test
    void downloadHtml_withFilterCriterion(){
        String filterCriterion = "$.method == 'POST'";
        var response = RestAssured.given()
                .get(getWebUiUrl() + "/tiger-report12345.html?filterCriterion="+filterCriterion);

        response.then()
                .statusCode(200)
                .contentType(MediaType.TEXT_HTML_VALUE);

        Elements htmlElements = Jsoup.parse(response.body().asString())
                .body().select(".msg-card");

        assertThat(htmlElements).size().isEqualTo(2);
        assertThat(htmlElements.html()).doesNotContain(tigerProxy.getRbelMessagesList().get(0).getUuid());
        assertThat(htmlElements.html()).doesNotContain(tigerProxy.getRbelMessagesList().get(1).getUuid());
        assertThat(htmlElements.get(0).html()).contains(tigerProxy.getRbelMessagesList().get(2).getUuid());
        assertThat(htmlElements.get(1).html()).contains(tigerProxy.getRbelMessagesList().get(3).getUuid());
    }

    @Test
    void uploadingTrafficFile_processesPairedMessageUuid() {
        String filterCriterion = "$.method == 'POST'";

        var trafficFileContent = RestAssured.given()
                .get(getWebUiUrl() + "/trafficLog12334.tgr?filterCriterion=" + filterCriterion);
        trafficFileContent.then()
                .statusCode(200)
                .header("available-messages", String.valueOf(TOTAL_OF_EXCHANGED_MESSAGES - 2));

        RestAssured.given().get(getWebUiUrl() + "/resetMsgs");
        assertThat(tigerProxy.getRbelMessages()).isEmpty();

        RestAssured
                .with().body(trafficFileContent.asString())
                .post(getWebUiUrl() + "/traffic")
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
