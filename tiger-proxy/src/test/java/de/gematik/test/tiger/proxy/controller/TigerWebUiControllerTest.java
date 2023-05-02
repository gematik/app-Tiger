/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.core.StringContains.containsString;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import de.gematik.rbellogger.data.facet.RbelMessageTimingFacet;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.proxy.TigerProxy;
import io.restassured.RestAssured;
import java.time.OffsetDateTime;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.model.HttpResponse;
import org.mockserver.netty.MockServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

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

        RestAssured.proxy = null;
    }

    @BeforeEach
    public void configureTigerProxy() {
        if (tigerProxy.getRbelMessages().isEmpty()) {
            val proxyRest = Unirest.spawnInstance();
            proxyRest.config().proxy("localhost", tigerProxy.getProxyPort());

            proxyRest.get("http://localhost:" + fakeBackendServerPort + "/foobar").asJson();
        }
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
            .body("metaMsgList.size()", equalTo(2))
            .body("metaMsgList[0].uuid", equalTo(tigerProxy.getRbelMessagesList().get(0).getUuid()))
            .body("metaMsgList[1].uuid", equalTo(tigerProxy.getRbelMessagesList().get(1).getUuid()));
    }

    @Test
    void checkOnlyOneMsgIsReturnedWithLastMsgUuidSupplied() {
        RestAssured.given()
            .get(getWebUiUrl() + "/getMsgAfter?lastMsgUuid=" + tigerProxy.getRbelMessagesList().get(0).getUuid())
            .then()
            .statusCode(200)
            .body("metaMsgList.size()", equalTo(1))
            .body("metaMsgList[0].uuid", equalTo(tigerProxy.getRbelMessagesList().get(1).getUuid()));
    }

    @Test
    void checkAllTrafficSuppliedWhenDownloadWithoutFilteredUuids() {
        RestAssured.given().get(getWebUiUrl() + "/trafficLog.tgr")
            .then()
            .statusCode(200)
            .body(containsString(tigerProxy.getRbelMessagesList().get(0).getUuid()))
            .body(containsString(tigerProxy.getRbelMessagesList().get(1).getUuid()));
    }

    @Test
    void checkSuppliedUuidsAreFilteredOutWhenDownloadingTraffic() {
        RestAssured.given()
            .get(getWebUiUrl() + "/trafficLog.tgr?lastMsgUuid=" + tigerProxy.getRbelMessagesList().get(0).getUuid())
            .then()
            .statusCode(200)
            .body(containsString(tigerProxy.getRbelMessagesList().get(1).getUuid()));
    }

    @Test
    void checkNoMsgIsReturnedIfNoneExistsAfterRequested() {
        RestAssured.given()
            .get(getWebUiUrl() + "/getMsgAfter?lastMsgUuid=" + tigerProxy.getRbelMessagesList().get(1).getUuid())
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
            .body("metaMsgList.size()", equalTo(2))
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
        try {
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

            assertThat(tigerProxy.getRbelMessages()).hasSize(2);
        } finally {
            tigerProxy.clearAllMessages();
        }
    }

    @Test
    void filterOutResponses_shouldStillAppearInPairs() {
        RestAssured.given()
            .get(getWebUiUrl() + "/getMsgAfter?filterCriterion=isRequest")
            .then()
            .statusCode(200)
            .body("metaMsgList.size()", equalTo(2));
    }

    @Test
    void filterOutRequests_shouldStillAppearInPairs() {
        RestAssured.given()
            .get(getWebUiUrl() + "/getMsgAfter?filterCriterion=isResponse")
            .then()
            .statusCode(200)
            .body("metaMsgList.size()", equalTo(2));
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
}
