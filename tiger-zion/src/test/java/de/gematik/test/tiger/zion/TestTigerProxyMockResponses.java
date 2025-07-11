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
package de.gematik.test.tiger.zion;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static de.gematik.rbellogger.data.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.matching.UrlPattern;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.util.GlobalServerMap;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import de.gematik.test.tiger.testenvmgr.util.TigerEnvironmentStartupException;
import de.gematik.test.tiger.zion.config.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import kong.unirest.core.*;
import lombok.SneakyThrows;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ResetTigerConfiguration
@TestPropertySource(
    properties = {"zion.mockResponseFiles.firstFile=src/test/resources/someMockResponse.yaml"})
@WireMockTest
@Disabled
class TestTigerProxyMockResponses {

  final Path tempDirectory = Path.of("target", "zionResponses");

  @Autowired private ZionConfiguration configuration;
  @Autowired private ObjectMapper objectMapper;
  @LocalServerPort private int port;
  private Map<String, TigerMockResponse> mockResponsesBackup;

  @SneakyThrows
  @BeforeEach
  public void setupTempDirectory() {
    TigerGlobalConfiguration.reset();
    Files.createDirectories(tempDirectory);
    Files.list(tempDirectory).forEach(path -> path.toFile().delete());
    mockResponsesBackup = configuration.getMockResponses();
  }

  @AfterEach
  public void resetMockResponses() {
    TigerGlobalConfiguration.reset();
    GlobalServerMap.clear();
    configuration.setMockResponses(mockResponsesBackup);
    configuration.setSpy(null);
  }

  @Test
  void simpleMockedResponse() {
    configuration.setMockResponses(
        Map.of(
            "backend_foobar",
            TigerMockResponse.builder()
                .requestCriterions(
                    List.of("message.method == 'GET'", "message.url =~ '.*/userJsonPath.*'"))
                .response(
                    TigerMockResponseDescription.builder()
                        .statusCode("666")
                        .body(
                            """
                        {
                          "authorizedUser": "!{$.path.username.value}",
                          "someCertificate": "!{keyMgr.b64Certificate('puk_idp_enc')}"
                        }
                        """)
                        .build())
                .build()));

    final HttpResponse<JsonNode> response =
        Unirest.get("http://localhost:" + port + "/userJsonPath?username=someUsername").asJson();
    assertThat(response.getStatus()).isEqualTo(666);
    assertThat(response.getBody().getObject().getString("authorizedUser"))
        .isEqualTo("someUsername");
  }

  @Test
  void testMockResponseFromFile() {
    final HttpResponse<JsonNode> response =
        Unirest.post("http://localhost:" + port + "/specificEndpoint")
            .body(
                "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VybmFtZSI6Im15SGFwcHlMaXR0bGVGb29iYXIifQ.u0NTCYczr5qVmwgvU21GgecaibDwnn6voWmvmFPvPh8")
            .asJson();
    assertThat(response.getStatus()).isEqualTo(203);
    assertThat(response.getBody().getObject().getString("authorizedUser"))
        .isEqualTo("myHappyLittleFoobar");
  }

  @Test
  void testSpyFunctionality(WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
    stubFor(get(".*").willReturn(status(666).withBody("{\"foo\":\"bar\"}")));

    configuration.setSpy(
        ZionSpyConfiguration.builder()
            .url("http://localhost:" + wmRuntimeInfo.getHttpPort() + "/deepPath")
            .protocolToPath("target/zionResponses")
            .build());

    Unirest.get("http://localhost:" + port + "/shallowPath?foo=bar").asJson();

    final TigerMockResponse mockResponse =
        objectMapper.readValue(
            Files.list(Path.of("target", "zionResponses")).findAny().orElseThrow().toFile(),
            TigerMockResponse.class);

    assertThat(mockResponse.getRequestCriterions())
        .contains("message.method == 'GET'")
        .contains("message.url =$ '/shallowPath?foo=bar'");
  }

  @Test
  void testTemplatedBackendRequest(WireMockRuntimeInfo wmRuntimeInfo) {
    stubFor(
        post(UrlPattern.ANY)
            .willReturn(
                status(200).withBody("{{request.body}}").withTransformers("response-template")));

    configuration.setMockResponses(
        Map.of(
            "templatedBackendRequest",
            TigerMockResponse.builder()
                .backendRequests(
                    Map.of(
                        "theRequest",
                        ZionBackendRequestDescription.builder()
                            .url("http://localhost:" + wmRuntimeInfo.getHttpPort() + "/deepPath")
                            .body(
                                """
                                {
                                  "tgrEncodeAs":"JWT",
                                  "header":{
                                    "alg": "BP256R1",
                                    "typ": "JWT"
                                  },
                                  "body":{
                                    "sub": "1234567890",
                                    "name": "John Doe",
                                    "iat": 1516239022
                                  },
                                  "signature":{
                                    "verifiedUsing":"idp_enc"
                                  }
                                }
                                """)
                            .assignments(Map.of("signer", "$.body.signature.verifiedUsing"))
                            .build()))
                .response(TigerMockResponseDescription.builder().body("${signer}").build())
                .build()));

    assertThat(
            Unirest.get("http://localhost:" + port + "/shallowPath?foo=bar").asString().getBody())
        .startsWith("puk_idp_");
  }

  @Test
  void testRbelPathCriterions() {
    configuration.setMockResponses(
        Map.of(
            "backend_foobar",
            TigerMockResponse.builder()
                .requestCriterions(List.of("$.path.username.value=='someUsername'"))
                .response(TigerMockResponseDescription.builder().statusCode("666").build())
                .build()));

    final HttpResponse<Empty> response =
        Unirest.get("http://localhost:" + port + "/userJsonPath?username=someUsername").asEmpty();
    assertThat(response.getStatus()).isEqualTo(666);
  }

  @Test
  void testConfigurationAssignments() {
    configuration.setMockResponses(
        Map.of(
            "backend_foobar",
            TigerMockResponse.builder()
                .requestCriterions(List.of("message.method == 'GET'"))
                .assignments(Map.of("foo.bar.variable", "$.path.username.value"))
                .response(
                    TigerMockResponseDescription.builder()
                        .statusCode("666")
                        .body("{\"authorizedUser\": \"${foo.bar.variable}\"}\n")
                        .build())
                .build()));

    final HttpResponse<JsonNode> response =
        Unirest.get("http://localhost:" + port + "/userJsonPath?username=someUsername").asJson();
    assertThat(response.getBody().getObject().getString("authorizedUser"))
        .isEqualTo("someUsername");
  }

  @Test
  void testLoopOverRequestParts() {
    configuration.setMockResponses(
        Map.of(
            "myHappyResponse",
            TigerMockResponse.builder()
                .requestCriterions(List.of("message.method == 'GET'"))
                .response(
                    TigerMockResponseDescription.builder()
                        .statusCode("666")
                        .body(
                            """
                            {'headers':\s
                                [
                                    {'header': '${header}',
                                     'tgrFor': 'header : request.headers.entrySet()'}]
                            }
                            """)
                        .build())
                .build()));

    final HttpResponse<JsonNode> response =
        Unirest.get("http://localhost:" + port + "/userJsonPath?username=someUsername").asJson();
    assertThat(
            response
                .getBody()
                .getObject()
                .getJSONArray("headers")
                .getJSONObject(0)
                .getString("header"))
        .matches(".*=.*");
  }

  @Test
  void testLoopWithArithmetic() {
    configuration.setMockResponses(
        Map.of(
            "myHappyResponse",
            TigerMockResponse.builder()
                .requestCriterions(List.of("message.method == 'GET'"))
                .response(
                    TigerMockResponseDescription.builder()
                        .statusCode("666")
                        .body(
                            "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n"
                                + "<loops>\n"
                                + "    <counting>\n"
                                + "        <tgrFor>number : {1,2,3}</tgrFor>\n"
                                + "        ${number} and !{ ${number} + 1}\n"
                                + "    </counting>\n"
                                + "</loops>\n")
                        .build())
                .build()));

    final HttpResponse<String> response =
        Unirest.get("http://localhost:" + port + "/blub").asString();
    assertThat(response.getBody()).contains("<counting>1 and 2</counting>");
  }

  @Test
  void testSpyFunctionalityWithJwt(WireMockRuntimeInfo wireMockRuntimeInfo) throws IOException {
    stubFor(
        get(UrlPattern.ANY)
            .willReturn(
                status(666)
                    .withBody(
                        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c")));

    configuration.setSpy(
        ZionSpyConfiguration.builder()
            .url("http://localhost:" + wireMockRuntimeInfo.getHttpPort() + "/deepPath")
            .protocolToPath("target/zionResponses")
            .build());

    Unirest.get("http://localhost:" + port + "/shallowPath?foo=bar").asJson();

    final TigerMockResponse mockResponse =
        objectMapper.readValue(
            Files.list(tempDirectory).findAny().get().toFile(), TigerMockResponse.class);

    assertThat(mockResponse.getResponse().getBody())
        .containsIgnoringWhitespaces(
            """
                      {
                        "sub": "1234567890",
                        "name": "John Doe",
                        "iat": 1516239022
                      }""");
  }

  @Test
  @TigerTest(
      tigerYaml =
          """
               tigerProxy:
                 proxyRoutes:
                   - from: /checkPassword
                     to: http://127.0.0.1:${free.port.20}/checkPassword
               servers:
                 mainServer:
                   type: externalJar
                   healthcheckUrl:
                     http://127.0.0.1:${free.port.30}
                   externalJarOptions:
                     arguments:
                       - --server.port=${free.port.30}
                       - --backendServer.port=${tiger.tigerProxy.proxyPort}
                       - --spring.profiles.active=mainserver
                     workingDir: src/test/resources
                   source:
                     - local:../../../target/tiger-zion-*-executable.jar
                 backendServer:
                   type: externalJar
                   healthcheckUrl:
                     http://127.0.0.1:${free.port.20}
                   externalJarOptions:
                     arguments:
                       - --server.port=${free.port.20}
                       - --spring.profiles.active=backendserver
                     workingDir: src/test/resources
                   source:
                     - local:../../../target/tiger-zion-*-executable.jar
               lib:
                 trafficVisualization: true
               """)
  void testMultipleZionServerAsExternalJars(
      TigerTestEnvMgr testEnvMgr, UnirestInstance unirestInstance) {
    testEnvMgr.getLocalTigerProxyOrFail().clearAllMessages();

    unirestInstance
        .get(
            TigerGlobalConfiguration.resolvePlaceholders(
                "http://localhost:${free.port.30}/helloWorld"))
        .header("password", "secret")
        .asJson();

    assertThat(testEnvMgr.getLocalTigerProxyOrFail().getRbelMessagesList().get(0))
        .extractChildWithPath("$.receiver.bundledServerName")
        .hasStringContentEqualTo("mainServer");

    assertThat(testEnvMgr.getLocalTigerProxyOrFail().getRbelMessagesList().get(1))
        .extractChildWithPath("$.sender.bundledServerName")
        .hasStringContentEqualTo("mainServer");

    assertThat(testEnvMgr.getLocalTigerProxyOrFail().getRbelMessagesList().get(1))
        .extractChildWithPath("$.receiver.bundledServerName")
        .hasStringContentEqualTo("backendServer");

    assertThat(testEnvMgr.getLocalTigerProxyOrFail().getRbelMessagesList().get(2))
        .extractChildWithPath("$.sender.bundledServerName")
        .hasStringContentEqualTo("backendServer");

    assertThat(testEnvMgr.getLocalTigerProxyOrFail().getRbelMessagesList().get(2))
        .extractChildWithPath("$.receiver.bundledServerName")
        .hasStringContentEqualTo("mainServer");

    assertThat(testEnvMgr.getLocalTigerProxyOrFail().getRbelMessagesList().get(3))
        .extractChildWithPath("$.sender.bundledServerName")
        .hasStringContentEqualTo("mainServer");
  }

  @Test
  @TigerTest(
      tigerYaml =
          """
              servers:
                zionExternal:
                  type: externalJar
                  healthcheckUrl:
                    http://127.0.0.1:${free.port.10}
                  externalJarOptions:
                    arguments:
                      - --server.port=${free.port.10}
                      - --spring.profiles.active=echoserver
                    workingDir: src/test/resources
                  source:
                    - local:../../../target/tiger-zion-*-executable.jar
              lib:
                trafficVisualization: true
                      """)
  void testOneZionServerAsExternalJar(TigerTestEnvMgr testEnvMgr, UnirestInstance unirestInstance) {
    unirestInstance
        .get(
            TigerGlobalConfiguration.resolvePlaceholders(
                "http://localhost:${free.port.10}/helloWorld"))
        .header("password", "secret")
        .asJson();

    assertThat(
            testEnvMgr
                .getLocalTigerProxyOrFail()
                .getRbelMessagesList()
                .get(0)
                .findElement("$.receiver.bundledServerName")
                .orElseThrow()
                .getRawStringContent())
        .isEqualTo("zionExternal");
  }

  @Test
  @TigerTest(
      tigerYaml =
          """
          tigerProxy:
          servers:
            mainServerTypeZion:
              type: zion
              healthcheckUrl:
                http://mainServerTypeZion
              healthcheckReturnCode: 200
              zionConfiguration:
                serverPort: ${free.port.50}
                mockResponses:
                  helloZionBackendServer:
                    requestCriterions:
                      - message.method == 'GET'
                      - message.path == '/helloZionBackendServer'
                    backendRequests:
                      sayHello:
                        method: POST
                        url: "http://backendServerTypeZion/helloBackend"
                        assignments:
                          backendResponseBody: "$.body"
                        executeAfterSelection: true
                    response:
                      statusCode: 200
                      body: ${backendResponseBody}
                  healthCheckResponse:
                    importance: 20 # more important so others don't get evaluated.
                    requestCriterions:
                      - message.method == 'GET'
                      - message.path == '/'
                    response:
                      statusCode: 200
            backendServerTypeZion:
              type: zion
              healthcheckUrl:
                http://backendServerTypeZion
              healthcheckReturnCode: 200
              zionConfiguration:
                serverPort: ${free.port.55}
                mockResponses:
                  helloFromBackend:
                    request:
                      method: POST
                      path: "/helloBackend"
                    response:
                      statusCode: 200
                      body: '{"Hello": "from backend"}'
                  healthCheck:
                    requestCriterions:
                      - message.method == 'GET'
                      - message.path == '/'
                    response:
                      statusCode: 200
                      body: '{"status":"UP"}'
                    importance: 10
          lib:
            trafficVisualization: true
          """)
  void testMultipleZionServerAsZionServerType(
      TigerTestEnvMgr testEnvMgr, UnirestInstance unirestInstance) {
    testEnvMgr.getLocalTigerProxyOrFail().clearAllMessages();

    unirestInstance
        .get(
            TigerGlobalConfiguration.resolvePlaceholders(
                "http://mainServerTypeZion/helloZionBackendServer"))
        .asJson();

    testEnvMgr.getLocalTigerProxyOrFail().getRbelMessagesList().stream()
        .map(RbelElement::printHttpDescription)
        .forEach(System.out::println);

    assertThat(testEnvMgr.getLocalTigerProxyOrFail().getRbelMessagesList().get(0))
        .extractChildWithPath("$.sender.bundledServerName")
        .hasStringContentEqualTo("local client");
    assertThat(testEnvMgr.getLocalTigerProxyOrFail().getRbelMessagesList().get(0))
        .extractChildWithPath("$.receiver.bundledServerName")
        .hasStringContentEqualTo("mainServerTypeZion");

    assertThat(testEnvMgr.getLocalTigerProxyOrFail().getRbelMessagesList().get(1))
        .extractChildWithPath("$.sender.bundledServerName")
        .hasStringContentEqualTo("mainServerTypeZion");
    assertThat(testEnvMgr.getLocalTigerProxyOrFail().getRbelMessagesList().get(1))
        .extractChildWithPath("$.receiver.bundledServerName")
        .hasStringContentEqualTo("backendServerTypeZion");

    assertThat(testEnvMgr.getLocalTigerProxyOrFail().getRbelMessagesList().get(2))
        .extractChildWithPath("$.sender.bundledServerName")
        .hasStringContentEqualTo("backendServerTypeZion");
    assertThat(testEnvMgr.getLocalTigerProxyOrFail().getRbelMessagesList().get(2))
        .extractChildWithPath("$.receiver.bundledServerName")
        .hasStringContentEqualTo("mainServerTypeZion");

    assertThat(testEnvMgr.getLocalTigerProxyOrFail().getRbelMessagesList().get(3))
        .extractChildWithPath("$.sender.bundledServerName")
        .hasStringContentEqualTo("mainServerTypeZion");
    assertThat(testEnvMgr.getLocalTigerProxyOrFail().getRbelMessagesList().get(3))
        .extractChildWithPath("$.receiver.bundledServerName")
        .hasStringContentEqualTo("local client");
  }

  @Test
  @TigerTest(
      tigerYaml =
          """
          servers:
            zionHello:
                type: zion
                zionConfiguration:
                  serverPort: ${free.port.60}
                  mockResponses:
                      hello:
                        requestCriterions:
                          - message.method == 'GET'
                          - message.url =~ '.*/helloWorld'
                        response:
                          statusCode: 222
                          body: '{"Hello":"World"}'
          lib:
            trafficVisualization: true
         """)
  void testOneZionServerAsZionServerType(
      TigerTestEnvMgr testEnvMgr, UnirestInstance unirestInstance) {

    unirestInstance.get("http://zionHello/helloWorld").asJson();

    assertThat(
            testEnvMgr
                .getLocalTigerProxyOrFail()
                .getRbelMessagesList()
                .get(0)
                .findElement("$.receiver.bundledServerName")
                .orElseThrow()
                .getRawStringContent())
        .isEqualTo("zionHello");
  }

  @Test
  @TigerTest(
      tigerYaml =
          """
          servers:
            zionHello:
                type: zion
                zionConfiguration:
                  serverPort: ${free.port.60}
                  mockResponses:
                      hello:
                        response:
                          bodyFile: 'this/is/not/a/real/file.yaml'
         """,
      skipEnvironmentSetup = true)
  void bodyFileWithNonExistentFile_shouldThrowException(TigerTestEnvMgr testEnvMgr) {
    assertThatThrownBy(testEnvMgr::setUpEnvironment)
        .isInstanceOf(TigerEnvironmentStartupException.class)
        .cause()
        .isInstanceOf(ConfigurationPropertiesBindException.class)
        .hasRootCauseInstanceOf(NoSuchFileException.class)
        .hasRootCauseMessage(Paths.get("this", "is", "not", "a", "real", "file.yaml").toString());
  }

  @Test
  void testDelay() {
    configuration.setMockResponses(
        Map.of(
            "delay",
            TigerMockResponse.builder()
                .requestCriterions(List.of("message.method == 'GET'"))
                .response(
                    TigerMockResponseDescription.builder()
                        .statusCode("666")
                        .body("{\"foo\": \"bar\"}")
                        .responseDelay("800")
                        .build())
                .build()));

    final GetRequest getRequest = Unirest.get("http://localhost:" + port + "/delayIt");
    // once before to reduce warmup
    getRequest.asJson();
    final LocalDateTime start = LocalDateTime.now();
    getRequest.asJson();
    final LocalDateTime end = LocalDateTime.now();
    assertThat(Duration.between(start, end)).isGreaterThan(Duration.ofMillis(800));
  }

  @Test
  void emptyHttpHeadersShouldNotBeTransmitted() {
    configuration.setMockResponses(
        Map.of(
            "response",
            TigerMockResponse.builder()
                .response(
                    TigerMockResponseDescription.builder()
                        .headers(Map.of("foo", "", "bar", "baz"))
                        .body("{\"foo\": \"bar\"}")
                        .build())
                .build()));

    final HttpResponse<JsonNode> response =
        Unirest.get("http://localhost:" + port + "/myRequest").asJson();
    assertThat(response.getHeaders().containsKey("foo")).isFalse();
    assertThat(response.getHeaders().containsKey("bar")).isTrue();
  }
}
