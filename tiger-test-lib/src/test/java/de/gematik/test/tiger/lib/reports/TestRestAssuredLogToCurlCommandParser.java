/*
 * Copyright (c) 2022 gematik GmbH
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
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import de.gematik.test.tiger.LocalProxyRbelMessageListener;
import de.gematik.test.tiger.lib.TigerDirector;
import io.restassured.RestAssured;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
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
        TigerDirector.start();
        TigerDirector.getLibConfig().setAddCurlCommandsForRaCallsToReport(true);
        TigerDirector.registerRestAssuredFilter();
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
        Object tigerHooksCurlLoggingFilter = ReflectionTestUtils.getField(TigerDirector.class, "curlLoggingFilter");
        return String.valueOf(ReflectionTestUtils.getField(
            tigerHooksCurlLoggingFilter, TigerRestAssuredCurlLoggingFilter.class, "outputStream").toString());
    }

    // TODO TGR-398 zusätzliche Tests implementieren
}
