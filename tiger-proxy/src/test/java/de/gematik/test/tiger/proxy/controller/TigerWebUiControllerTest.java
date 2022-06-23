/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.StringContains.containsString;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import de.gematik.rbellogger.RbelOptions;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelMessageTimingFacet;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.common.util.TigerSerializationUtil;
import de.gematik.test.tiger.proxy.AbstractTigerProxyTest;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.TigerProxyApplication;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.ProxySpecification;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import kong.unirest.Unirest;
import kong.unirest.UnirestInstance;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TigerWebUiControllerTest {

    public static WireMockServer fakeBackendServer;
    @Autowired
    private TigerProxy tigerProxy;
    @LocalServerPort
    private int adminPort;

    @BeforeAll
    public static void setupBackendServer() {
        fakeBackendServer = new WireMockServer(
            new WireMockConfiguration()
                .dynamicPort());
        fakeBackendServer.start();

        log.info("Started Backend-Server on ports {}", fakeBackendServer.port());

        fakeBackendServer.stubFor(get(urlPathEqualTo("/foobar"))
            .willReturn(aResponse()
                .withStatus(666)
                .withBody("{\"foo\":\"bar\"}")));

        RestAssured.proxy = null;
    }

    @AfterAll
    public static void closeDownTigerProxy() {
        fakeBackendServer.stop();
    }

    @BeforeEach
    public void configureTigerProxy() {
        if (tigerProxy.getRbelMessages().isEmpty()) {
            val proxyRest = Unirest.spawnInstance();
            proxyRest.config().proxy("localhost", tigerProxy.getProxyPort());

            proxyRest.get("http://localhost:" + fakeBackendServer.port() + "/foobar").asJson();
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
    public void checkHtmlIsReturned() {
        RestAssured.given().get(getWebUiUrl())
            .then()
            .statusCode(200)
            .body(containsString("msgList"));
    }

    @Test
    public void checkMsgIsReturned() {
        RestAssured.given().get(getWebUiUrl() + "/getMsgAfter")
            .then()
            .statusCode(200)
            .body("metaMsgList.size()", equalTo(2))
            .body("metaMsgList[0].uuid", equalTo(tigerProxy.getRbelMessages().get(0).getUuid()))
            .body("metaMsgList[1].uuid", equalTo(tigerProxy.getRbelMessages().get(1).getUuid()));
    }

    @Test
    public void checkOnlyOneMsgIsReturnedWithLastMsgUuidSupplied() {
        RestAssured.given()
            .get(getWebUiUrl() + "/getMsgAfter?lastMsgUuid=" + tigerProxy.getRbelMessages().get(0).getUuid())
            .then()
            .statusCode(200)
            .body("metaMsgList.size()", equalTo(1))
            .body("metaMsgList[0].uuid", equalTo(tigerProxy.getRbelMessages().get(1).getUuid()));
    }

    @Test
    public void checkAllTrafficSuppliedWhenDownloadWithoutFilteredUuids() {
        RestAssured.given().get(getWebUiUrl() + "/trafficLog.tgr")
            .then()
            .statusCode(200)
            .body(containsString(tigerProxy.getRbelMessages().get(0).getUuid()))
            .body(containsString(tigerProxy.getRbelMessages().get(1).getUuid()));
    }

    @Test
    public void checkSuppliedUuidsAreFilteredOutWhenDownloadingTraffic() {
        RestAssured.given()
            .get(getWebUiUrl() + "/trafficLog.tgr?lastMsgUuid=" + tigerProxy.getRbelMessages().get(0).getUuid())
            .then()
            .statusCode(200)
            .body(containsString(tigerProxy.getRbelMessages().get(1).getUuid()));
    }

    @Test
    public void checkNoMsgIsReturnedIfNoneExistsAfterRequested() {
        RestAssured.given()
            .get(getWebUiUrl() + "/getMsgAfter?lastMsgUuid=" + tigerProxy.getRbelMessages().get(1).getUuid())
            .then()
            .statusCode(200)
            .body("metaMsgList.size()", equalTo(0));
    }


    @Test
    public void checkNoMsgIsReturnedAfterReset() {
        RestAssured.given().get(getWebUiUrl() + "/resetMsgs")
            .then()
            .statusCode(200);

        RestAssured.given().get(getWebUiUrl() + "/getMsgAfter")
            .then()
            .statusCode(200)
            .body("metaMsgList.size()", equalTo(0));
    }

    @Test
    public void checkCorrectMenuStringsAreSupplied() {
        RestAssured.given()
            .get(getWebUiUrl() + "/getMsgAfter")
            .then()
            .statusCode(200)
            .body("metaMsgList.size()", equalTo(2))
            .body("metaMsgList[0].menuInfoString", equalTo("GET /foobar"))
            .body("metaMsgList[1].menuInfoString", equalTo("666"))
            .body("metaMsgList[1].timestamp", equalTo(tigerProxy.getRbelMessages().get(1)
                .getFacetOrFail(RbelMessageTimingFacet.class).getTransmissionTime().toOffsetDateTime().toString()));
    }

    @Test
    public void filterOutResponses_shouldStillAppearInPairs() {
        RestAssured.given()
            .get(getWebUiUrl() + "/getMsgAfter?filterCriterion=isRequest")
            .then()
            .statusCode(200)
            .body("metaMsgList.size()", equalTo(2));
    }

    @Test
    public void filterOutRequests_shouldStillAppearInPairs() {
        RestAssured.given()
            .get(getWebUiUrl() + "/getMsgAfter?filterCriterion=isResponse")
            .then()
            .statusCode(200)
            .body("metaMsgList.size()", equalTo(2));
    }
}
