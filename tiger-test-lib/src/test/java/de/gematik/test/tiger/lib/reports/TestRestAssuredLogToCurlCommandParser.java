/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.reports;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static org.assertj.core.api.Assertions.assertThat;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import de.gematik.test.tiger.hooks.TigerTestHooks;
import de.gematik.test.tiger.lib.TigerDirector;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@WireMockTest
public class TestRestAssuredLogToCurlCommandParser {

    @BeforeEach
    public void setup(WireMockRuntimeInfo remoteServer) {
        remoteServer.getWireMock().register(get("/foo")
            .willReturn(aResponse()
                .withBody("bor")));
        remoteServer.getWireMock().register(get("/faa")
            .willReturn(aResponse()
                .withBody("bar")));
        remoteServer.getWireMock().register(post("/fuu")
            .willReturn(aResponse()
                .withBody("buu")));
        remoteServer.getWireMock().register(post("/fyy")
            .willReturn(aResponse()
                .withBody("byy")));

        TigerDirector.testUninitialize();
        TigerTestHooks.unregisterRestAssuredFilter();
        TigerDirector.start();
        TigerDirector.getLibConfig().setAddCurlCommandsForRaCallsToReport(true);
        TigerTestHooks.registerRestAssuredFilter();
    }

    @Test
    public void testMultipleRequestsSplitCorrectly(WireMockRuntimeInfo remoteServer) {

        RestAssured.with().get(remoteServer.getHttpBaseUrl() + "/foo").andReturn();
        RestAssured.with().post(remoteServer.getHttpBaseUrl() + "/fuu").andReturn();
        RestAssured.with().post(remoteServer.getHttpBaseUrl() + "/fyy").andReturn();
        RestAssured.with().get(remoteServer.getHttpBaseUrl() + "/faa").andReturn();

        assertThat(RestAssuredLogToCurlCommandParser.convertRestAssuredLogToCurlCalls(getCurlLog()))
            .hasSize(4);
    }

    @Test
    public void testSingleRequestsSplitCorrectly(WireMockRuntimeInfo remoteServer) {
        RestAssured.with().get(remoteServer.getHttpBaseUrl() + "/foo").andReturn();

        assertThat(RestAssuredLogToCurlCommandParser.convertRestAssuredLogToCurlCalls(getCurlLog()))
            .hasSize(1);
    }

    @Test
    public void testPostToCurl(WireMockRuntimeInfo remoteServer) {

        RestAssured.with().post(remoteServer.getHttpBaseUrl() + "/fuu").
            andReturn();

        String raLog = getCurlLog();
        String log = RestAssuredLogToCurlCommandParser.convertRestAssuredLogToCurlCalls(raLog).get(0);
        String curlCmd = RestAssuredLogToCurlCommandParser.parseCurlCommandFromRestAssuredLog(log);
        assertThat(curlCmd).isEqualTo(
            "curl -v -H \"Accept: */*\" "
                + "-H \"Content-Type: application/x-www-form-urlencoded; charset=ISO-8859-1\" "
                + "-X POST \"" + remoteServer.getHttpBaseUrl() + "/fuu\" ");
    }

    @Test
    public void testGetToCurl(WireMockRuntimeInfo remoteServer) {
        RestAssured.with().get(remoteServer.getHttpBaseUrl() + "/foo").
            andReturn();

        String raLog = getCurlLog();
        String log = RestAssuredLogToCurlCommandParser.convertRestAssuredLogToCurlCalls(raLog).get(0);
        String curlCmd = RestAssuredLogToCurlCommandParser.parseCurlCommandFromRestAssuredLog(log);
        assertThat(curlCmd).isEqualTo(
            "curl -v -H \"Accept: */*\" "
                + "-X GET \"" + remoteServer.getHttpBaseUrl() + "/foo\" ");
    }

    private String getCurlLog() {
        Object tigerHooksCurlLoggingFilter = ReflectionTestUtils.getField(TigerTestHooks.class, "curlLoggingFilter");
        return String.valueOf(ReflectionTestUtils.getField(
            tigerHooksCurlLoggingFilter, TigerRestAssuredCurlLoggingFilter.class, "outputStream").toString());
    }

    // TODO TGR-398 zusätzliche Tests implementieren
}
