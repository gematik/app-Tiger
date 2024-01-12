package de.gematik.test.tiger.zion;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static de.gematik.rbellogger.data.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.matching.UrlPattern;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import de.gematik.test.tiger.zion.config.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import kong.unirest.*;
import lombok.SneakyThrows;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
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
                        .statusCode(666)
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
                .response(TigerMockResponseDescription.builder().statusCode(666).build())
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
                        .statusCode(666)
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
                        .statusCode(666)
                        .body(
                            "{'headers': \n"
                                + "    ["
                                + "        {'header': '${header}',\n"
                                + "         'tgrFor': 'header : request.headers.entrySet()'}]\n"
                                + "}")
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
                        .statusCode(666)
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
                 experimental:
                   trafficVisualization: true
               """)
  void testMultipleZionServer(TigerTestEnvMgr testEnvMgr, UnirestInstance unirestInstance) {
    testEnvMgr.getLocalTigerProxyOrFail().clearAllMessages();

    unirestInstance
        .get(
            TigerGlobalConfiguration.resolvePlaceholders(
                "http://localhost:${free.port.30}/helloWorld"))
        .header("password", "secret")
        .asJson();

    assertThat(testEnvMgr.getLocalTigerProxyOrFail().getRbelMessagesList().get(0))
        .extractChildWithPath("$.sender.bundledServerName")
        .hasStringContentEqualTo("mainServer");

    assertThat(testEnvMgr.getLocalTigerProxyOrFail().getRbelMessagesList().get(0))
        .extractChildWithPath("$.receiver.bundledServerName")
        .hasStringContentEqualTo("backendServer");

    assertThat(testEnvMgr.getLocalTigerProxyOrFail().getRbelMessagesList().get(1))
        .extractChildWithPath("$.sender.bundledServerName")
        .hasStringContentEqualTo("backendServer");

    assertThat(testEnvMgr.getLocalTigerProxyOrFail().getRbelMessagesList().get(1))
        .extractChildWithPath("$.receiver.bundledServerName")
        .hasStringContentEqualTo("mainServer");

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
                experimental:
                  trafficVisualization: true
                      """)
  void testOneZionServer(TigerTestEnvMgr testEnvMgr, UnirestInstance unirestInstance) {

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
}
