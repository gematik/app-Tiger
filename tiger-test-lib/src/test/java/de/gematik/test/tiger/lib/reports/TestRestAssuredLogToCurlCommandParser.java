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
package de.gematik.test.tiger.lib.reports;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import de.gematik.test.tiger.lib.TigerDirector;
import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TestRestAssuredLogToCurlCommandParser {

  private static String httpBaseUrl;
  private static WireMockServer wireMockServer;

  @BeforeAll
  static void setup() {
    wireMockServer = new WireMockServer(0);
    wireMockServer.start();

    httpBaseUrl = wireMockServer.baseUrl();

    wireMockServer.stubFor(get("/foo").willReturn(aResponse().withBody("bor")));
    wireMockServer.stubFor(get("/faa").willReturn(aResponse().withBody("bar")));
    wireMockServer.stubFor(post("/fuu").willReturn(aResponse().withBody("buu")));
    wireMockServer.stubFor(post("/fyy").willReturn(aResponse().withBody("byy")));
    wireMockServer.stubFor(put("/put").willReturn(aResponse().withBody("put-ok")));
    wireMockServer.stubFor(delete("/del").willReturn(aResponse().withBody("del-ok")));
    wireMockServer.stubFor(patch("/patch").willReturn(aResponse().withBody("patch-ok")));
    wireMockServer.stubFor(post("/form").willReturn(aResponse().withBody("form-ok")));

    TigerDirector.testUninitialize();
    TigerDirector.start();
    TigerDirector.getLibConfig().setAddCurlCommandsForRaCallsToReport(true);
    TigerDirector.registerRestAssuredFilter();
  }

  @AfterAll
  static void tearDown() {
    if (wireMockServer != null) {
      wireMockServer.stop();
    }
    TigerDirector.testUninitialize();
  }

  @BeforeEach
  void resetLog() {
    TigerRestAssuredCurlLoggingFilter filter = TigerDirector.getCurlLoggingFilter();
    if (filter != null) {
      filter.getRecordedCurlCommands().clear();
    }
  }

  @Test
  void testMultipleRequestsSplitCorrectly() {
    RestAssured.with().get(httpBaseUrl + "/foo").andReturn();
    RestAssured.with().post(httpBaseUrl + "/fuu").andReturn();
    RestAssured.with().post(httpBaseUrl + "/fyy").andReturn();
    RestAssured.with().get(httpBaseUrl + "/faa").andReturn();

    assertThat(TigerDirector.getCurlLoggingFilter().getRecordedCurlCommands()).hasSize(4);
  }

  @Test
  void testSingleRequestsSplitCorrectly() {
    RestAssured.with().get(httpBaseUrl + "/foo").andReturn();

    assertThat(TigerDirector.getCurlLoggingFilter().getRecordedCurlCommands()).hasSize(1);
  }

  @Test
  void testPostToCurl() {
    RestAssured.with().post(httpBaseUrl + "/fuu").andReturn();

    String curlCmd = TigerDirector.getCurlLoggingFilter().getRecordedCurlCommands().get(0);
    assertThat(curlCmd)
        .isEqualTo(
            "curl -v -H \"Accept: */*\" "
                + "-H \"Content-Type: application/x-www-form-urlencoded; charset=ISO-8859-1\" "
                + "-X POST \""
                + httpBaseUrl
                + "/fuu\" ");
  }

  @Test
  void testGetToCurl() {
    RestAssured.with().get(httpBaseUrl + "/foo").andReturn();

    String curlCmd = TigerDirector.getCurlLoggingFilter().getRecordedCurlCommands().get(0);
    assertThat(curlCmd).isEqualTo("curl -v -H \"Accept: */*\" -X GET \"" + httpBaseUrl + "/foo\" ");
  }

  @Test
  void testPutWithBody() {
    RestAssured.given()
        .contentType("application/json")
        .body("{\"key\":\"value\"}")
        .put(httpBaseUrl + "/put")
        .andReturn();

    String curlCmd = TigerDirector.getCurlLoggingFilter().getRecordedCurlCommands().get(0);
    assertThat(curlCmd)
        .isEqualTo(
            "curl -v -H \"Accept: */*\" -H \"Content-Type: application/json\" -d"
                + " '{\"key\":\"value\"}' -X PUT \""
                + httpBaseUrl
                + "/put\" ");
  }

  @Test
  void testDeleteToCurl() {
    RestAssured.given().delete(httpBaseUrl + "/del").andReturn();

    String curlCmd = TigerDirector.getCurlLoggingFilter().getRecordedCurlCommands().get(0);
    assertThat(curlCmd)
        .isEqualTo("curl -v -H \"Accept: */*\" -X DELETE \"" + httpBaseUrl + "/del\" ");
  }

  @Test
  void testPatchWithBody() {
    RestAssured.given().body("patch-data").patch(httpBaseUrl + "/patch").andReturn();

    String curlCmd = TigerDirector.getCurlLoggingFilter().getRecordedCurlCommands().get(0);
    assertThat(curlCmd)
        .isEqualTo(
            "curl -v -H \"Accept: */*\" -H \"Content-Type: text/plain; charset=ISO-8859-1\" -d"
                + " 'patch-data' -X PATCH \""
                + httpBaseUrl
                + "/patch\" ");
  }

  @Test
  void testPostWithFormParams() {
    RestAssured.given()
        .formParam("name", "john")
        .formParam("age", "30")
        .post(httpBaseUrl + "/form")
        .andReturn();

    String curlCmd = TigerDirector.getCurlLoggingFilter().getRecordedCurlCommands().get(0);
    assertThat(curlCmd)
        .isEqualTo(
            "curl -v -H \"Accept: */*\" -H \"Content-Type: application/x-www-form-urlencoded;"
                + " charset=ISO-8859-1\" --data \"name=john&age=30\" -X POST \""
                + httpBaseUrl
                + "/form\" ");
  }

  @Test
  void testPostWithMultipartParam() {
    RestAssured.given()
        .multiPart("description", "multipart-value")
        .post(httpBaseUrl + "/fuu")
        .andReturn();

    String curlCmd = TigerDirector.getCurlLoggingFilter().getRecordedCurlCommands().get(0);
    assertThat(curlCmd).contains("-F \"description=multipart-value\"");
  }

  @Test
  void testCookies() {
    RestAssured.given().cookie("session_id", "abc123xyz").get(httpBaseUrl + "/foo").andReturn();

    String curlCmd = TigerDirector.getCurlLoggingFilter().getRecordedCurlCommands().get(0);
    assertThat(curlCmd)
        .isEqualTo(
            "curl -v -H \"Accept: */*\" -b \"session_id=abc123xyz\" -X GET \""
                + httpBaseUrl
                + "/foo\" ");
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

    String curlCmd = TigerDirector.getCurlLoggingFilter().getRecordedCurlCommands().get(0);
    assertThat(curlCmd).contains(stringContainedInCurl);
  }
}
