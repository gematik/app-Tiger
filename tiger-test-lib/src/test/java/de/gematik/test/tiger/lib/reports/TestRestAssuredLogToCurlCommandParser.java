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

package de.gematik.test.tiger.lib.reports;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static org.assertj.core.api.Assertions.assertThat;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import de.gematik.test.tiger.lib.TigerDirector;
import io.restassured.RestAssured;
import io.restassured.filter.log.LogDetail;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.specification.RequestSpecification;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

class TestRestAssuredLogToCurlCommandParser {

    private static String httpBaseUrl;
    private static WireMockServer wireMockServer;

    @BeforeAll
    public static void setup() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();

        httpBaseUrl = wireMockServer.baseUrl();

        wireMockServer.stubFor(get("/foo")
            .willReturn(aResponse()
                .withBody("bor")));
        wireMockServer.stubFor(get("/faa")
            .willReturn(aResponse()
                .withBody("bar")));
        wireMockServer.stubFor(post("/fuu")
            .willReturn(aResponse()
                .withBody("buu")));
        wireMockServer.stubFor(post("/fyy")
            .willReturn(aResponse()
                .withBody("byy")));

        TigerDirector.testUninitialize();
        TigerDirector.start();
        TigerDirector.getLibConfig().setAddCurlCommandsForRaCallsToReport(true);
        TigerDirector.registerRestAssuredFilter();
    }

    @BeforeEach
    void resetLog() {
        Object tigerHooksCurlLoggingFilter = ReflectionTestUtils.getField(TigerDirector.class, "curlLoggingFilter");
        final ByteArrayOutputStream newOutputStream = new ByteArrayOutputStream();
        ReflectionTestUtils.setField(tigerHooksCurlLoggingFilter, "outputStream", newOutputStream);
        ReflectionTestUtils.setField(tigerHooksCurlLoggingFilter, "requestLoggingFilter",
            new RequestLoggingFilter(
                LogDetail.ALL,
                true,
                new PrintStream(newOutputStream),
                true));
    }

    @Test
    void testMultipleRequestsSplitCorrectly() {
        RestAssured.with().get(httpBaseUrl + "/foo").andReturn();
        RestAssured.with().post(httpBaseUrl + "/fuu").andReturn();
        RestAssured.with().post(httpBaseUrl + "/fyy").andReturn();
        RestAssured.with().get(httpBaseUrl + "/faa").andReturn();

        assertThat(RestAssuredLogToCurlCommandParser.convertRestAssuredLogToCurlCalls(getCurlLog()))
            .hasSize(4);
    }

    @Test
    void testSingleRequestsSplitCorrectly() {
        RestAssured.with().get(httpBaseUrl + "/foo").andReturn();

        assertThat(RestAssuredLogToCurlCommandParser.convertRestAssuredLogToCurlCalls(getCurlLog()))
            .hasSize(1);
    }

    @Test
    void testPostToCurl() {
        RestAssured.with().post(httpBaseUrl + "/fuu").
            andReturn();

        String raLog = getCurlLog();
        String log = RestAssuredLogToCurlCommandParser.convertRestAssuredLogToCurlCalls(raLog).get(0);
        String curlCmd = RestAssuredLogToCurlCommandParser.parseCurlCommandFromRestAssuredLog(log);
        assertThat(curlCmd).isEqualTo(
            "curl -v -H \"Accept: */*\" "
                + "-H \"Content-Type: application/x-www-form-urlencoded; charset=ISO-8859-1\" "
                + "-X POST \"" + httpBaseUrl + "/fuu\" ");
    }

    @Test
    void testGetToCurl() {
        RestAssured.with().get(httpBaseUrl + "/foo").
            andReturn();

        String raLog = getCurlLog();
        String log = RestAssuredLogToCurlCommandParser.convertRestAssuredLogToCurlCalls(raLog).get(0);
        String curlCmd = RestAssuredLogToCurlCommandParser.parseCurlCommandFromRestAssuredLog(log);
        assertThat(curlCmd).isEqualTo(
            "curl -v -H \"Accept: */*\" "
                + "-X GET \"" + httpBaseUrl + "/foo\" ");
    }

    @ParameterizedTest
    @CsvSource({
        "'foo\n\rbar',            \"Custom-Header: foobar\"",
        "'foo\n\rbar\n\rschmar',  \"Custom-Header: foobarschmar\"",
        "'foo\nbar\nschmar',      \"Custom-Header: foobarschmar\""
    })
    void testHeaderWithCrlf(String header, String stringContainedInCurl) {
        RequestSpecification requestSpec = RestAssured.given();

        requestSpec.header("Custom-Header", header);
        requestSpec.post(httpBaseUrl + "/fuu");

        String log = RestAssuredLogToCurlCommandParser.convertRestAssuredLogToCurlCalls(getCurlLog()).get(0);
        String curlCmd = RestAssuredLogToCurlCommandParser.parseCurlCommandFromRestAssuredLog(log);
        assertThat(curlCmd).contains(stringContainedInCurl);
    }

    private String getCurlLog() {
        Object tigerHooksCurlLoggingFilter = ReflectionTestUtils.getField(TigerDirector.class, "curlLoggingFilter");
        return String.valueOf(ReflectionTestUtils.getField(
            tigerHooksCurlLoggingFilter, TigerRestAssuredCurlLoggingFilter.class, "outputStream").toString());
    }

    // TODO TGR-398 zusätzliche Tests implementieren
}
