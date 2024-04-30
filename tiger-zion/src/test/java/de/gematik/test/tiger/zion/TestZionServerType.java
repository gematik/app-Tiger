package de.gematik.test.tiger.zion;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.test.tiger.ZionServerType;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import de.gematik.test.tiger.zion.config.ZionConfiguration;
import java.nio.charset.Charset;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.UnirestInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xmlunit.assertj.XmlAssert;

@ResetTigerConfiguration
class TestZionServerType {

  @BeforeEach
  @AfterEach
  public void resetConfig() {
    TigerGlobalConfiguration.reset();
  }

  @TigerTest(
      tigerYaml =
          """
servers:
  zionServer:
    type: zion
    zionConfiguration:
      serverPort: "${free.port.10}"
      mockResponses:
        helloWorld:
          requestCriterions:
            - message.method == 'GET'
            - message.url =~ '.*/helloWorld'
          response:
            statusCode: 222
            body: '{"Hello":"World"}'

""")
  @Test
  void testZionServer(UnirestInstance unirestInstance) {
    final HttpResponse<JsonNode> response =
        unirestInstance
            .get(
                TigerGlobalConfiguration.resolvePlaceholders(
                    "http://zionServer/blubBlab/helloWorld"))
            .asJson();

    assertThat(response.getStatus()).isEqualTo(222);
    assertThat(response.getBody().getObject().getString("Hello")).isEqualTo("World");
  }

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
    """)
  @Test
  void testExternalZionServer(UnirestInstance unirest) {
    final HttpResponse<JsonNode> response =
        unirest
            .get(
                TigerGlobalConfiguration.resolvePlaceholders(
                    "http://localhost:${free.port.10}/blubBlab/helloWorld"))
            .asJson();

    assertThat(response.getStatus()).isEqualTo(222);
    assertThat(response.getBody().getObject().getString("Hello")).isEqualTo("World");
  }

  @TigerTest(
      tigerYaml =
          """
    servers:
      zionServer:
        type: zion
        zionConfiguration:
          serverPort: "${free.port.10}"
          mockResponses:
            loop:
              requestCriterions:
                - message.url =~ '.*/loop.*'
              response:
                statusCode: 200
                body: '<?xml version="1.0"?>
                <rootNode>
                    <repeatedTag tgrFor="i : 1..!{$.path.number.value}">Nummer ${i}</repeatedTag>
                </rootNode>'
        """)
  @Test
  void testLoopWithPlacedholderValue(UnirestInstance unirest) {
    final HttpResponse<String> response =
        unirest
            .get(TigerGlobalConfiguration.resolvePlaceholders("http://zionServer/loop?number=4"))
            .asString();

    assertThat(response.getBody()).contains("<repeatedTag>Nummer 4</repeatedTag>");
  }

  @TigerTest(
      tigerYaml =
          """
        servers:
          mainServer:
            type: zion
            zionConfiguration:
              serverPort: "${free.port.50}"
              mockResponses:
                passwordCheckResponse:
                  backendRequests:
                    tokenCheck:
                      url: "http://localhost:${free.port.60}/checkPassword"
                      body: '?{$.header.password}'
                      assignments:
                        correctPassword: "!{$.responseCode == '200'}"
                  requestCriterions:
                    - message.method == 'GET'
                    - message.path == '/helloWorld'
                  nestedResponses:
                    correctPassword:
                      importance: 10
                      requestCriterions:
                        - "${correctPassword} == 'true'"
                      response:
                        statusCode: 200
                        body: '{"Hello":"World"}'
                    wrongPassword:
                      importance: 0
                      response:
                        statusCode: 405
                        body: '{"Wrong":"Password"}'
          backendServer:
            type: zion
            zionConfiguration:
              serverPort: "${free.port.60}"
              mockResponses:
                correctPassword:
                  importance: 10
                  requestCriterions:
                    - "$.body == 'secret'"
                  response:
                    statusCode: 200
                wrongPassword:
                  importance: 0
                  response:
                    statusCode: 400
        """)
  @Test
  void testMultipleZionServer(UnirestInstance unirest) {
    final HttpResponse<JsonNode> response =
        unirest
            .get(TigerGlobalConfiguration.resolvePlaceholders("http://mainServer/helloWorld"))
            .header("password", "secret")
            .asJson();

    assertThat(response.getStatus()).isEqualTo(200);
  }

  @TigerTest(
      tigerYaml =
          """
        servers:
          mainServer:
            type: zion
            zionConfiguration:
              serverPort: "${free.port.50}"
              mockResponses:
                passwordCheckResponse:
                  requestCriterions:
                    - message.method == 'GET'
                    - message.path == '/helloWorld'
                  nestedResponses:
                    regularResponse:
                      importance: 10
                      requestCriterions:
                        - "${shouldSendEmptyResponse|false}"
                      response:
                        statusCode: ${responseCode|200}
                    emptyResponse:
                      importance: 0
                      response:
                        statusCode: ${responseCode|200}
                        body: '{"Wrong":"Password"}'
        """)
  @Test
  void configureVariousResponseCodes(UnirestInstance unirest) {
    assertThat(unirest.get("http://mainServer/helloWorld").asJson().getStatus()).isEqualTo(200);

    TigerGlobalConfiguration.putValue("responseCode", "666");
    assertThat(unirest.get("http://mainServer/helloWorld").asJson().getStatus()).isEqualTo(666);

    TigerGlobalConfiguration.putValue("shouldSendEmptyResponse", "true");
    assertThat(unirest.get("http://mainServer/helloWorld").asString().getBody()).isEmpty();
  }

  @TigerTest(
      tigerYaml =
          """
 servers:
   mainServer:
     type: zion
     zionConfiguration:
       serverPort: "${free.port.50}"
       mockResponses:
         lessImportantEndpoint:
           importance: 0
           backendRequests:
             tokenCheck:
               url: "THIS IS INVALID TO THROW A 500 IF CALLED"
               body: '?{$.header.password}'
           requestCriterions:
             - message.method == 'GET'
             - message.path == '/lessImportantEndpoint'
           nestedResponses:
             helloWorld:
               response:
                 statusCode: 200
                 body: '{"Hello":"World"}'
         moreImportantEndpoint:
           importance: 20
           requestCriterions:
             - message.method == 'GET'
             - message.path == '/moreImportantEndpoint'
           response:
             statusCode: 777
             body: '{"Hello":"World"}'
           """)
  @Test
  void shouldNotCallBackendWhenHigherImportancePathMatches(UnirestInstance unirest) {
    // if we try to match the passwordCheckResponse we get a 500 because the backend request is not
    // configured
    final HttpResponse<String> errorResponse =
        unirest
            .get(
                TigerGlobalConfiguration.resolvePlaceholders(
                    "http://mainServer/lessImportantEndpoint"))
            .asString();

    assertThat(errorResponse.getStatus()).isEqualTo(500);

    // By matching another path with higher importance, the backend does not get called and no error
    // comes up.
    final HttpResponse<JsonNode> response =
        unirest
            .get(
                TigerGlobalConfiguration.resolvePlaceholders(
                    "http://mainServer/moreImportantEndpoint"))
            .asJson();

    assertThat(response.getStatus()).isEqualTo(777);
  }

  @TigerTest(
      tigerYaml =
          """
servers:
  mainServer:
    type: zion
    zionConfiguration:
      serverPort: "${free.port.50}"
      mockResponses:
        doBackendRequestBeforeSelection:
          assignments:
            blub: "nothing changed"
          request:
            path: "/helloMain"
            additionalCriterions:
              - "'${blub}' == 'changed before selection'"
          response:
            statusCode: 777
            body: ${blub}
          backendRequests:
            helloBackend:
              url: "http://localhost:${free.port.60}/helloBackend"
              assignments:
                blub: "changed before selection"
              executeAfterSelection: false
  backendServer:
    type: zion
    zionConfiguration:
      serverPort: "${free.port.60}"
      mockResponses:
        helloBackend:
          response:
            statusCode: 200
            body: '{"Hello":"World"}'
              """)
  @Test
  void testBackendRequestEvaluateBeforeWithVariableInCriterion_shouldMatchResponse(
      UnirestInstance unirest) {
    final HttpResponse<String> response =
        unirest
            .get(TigerGlobalConfiguration.resolvePlaceholders("http://mainServer/helloMain"))
            .asString();

    assertThat(response.getStatus()).isEqualTo(777);
    assertThat(response.getBody()).isEqualTo("changed before selection");
  }

  @TigerTest(
      tigerYaml =
          """
servers:
  mainServer:
    type: zion
    zionConfiguration:
      serverPort: "${free.port.50}"
      mockResponses:
        doBackendRequestBeforeSelection:
          assignments:
            blub: "nothing changed"
          request:
            path: "/helloMain"
            additionalCriterions:
              - "'${blub}' == 'changed after selection'"
          response:
            statusCode: 777
            body: ${blub}
          backendRequests:
            helloBackend:
              url: "http://localhost:${free.port.60}/helloBackend"
              assignments:
                blub: "changed after selection"
              executeAfterSelection: true
  backendServer:
    type: zion
    zionConfiguration:
      serverPort: "${free.port.60}"
      mockResponses:
        helloBackend:
          response:
            statusCode: 200
            body: '{"Hello":"World"}'
              """)
  @Test
  void testBackendRequestEvaluateAfterWithVariableInCriterion_shouldNotMatchResponse(
      UnirestInstance unirest) {
    final HttpResponse<String> response =
        unirest
            .get(TigerGlobalConfiguration.resolvePlaceholders("http://mainServer/helloMain"))
            .asString();

    // when nothing matches a 500 code is returned
    assertThat(response.getStatus()).isEqualTo(500);
  }

  @TigerTest(
      tigerYaml =
          """
                            servers:
                              mainServer:
                                type: zion
                                zionConfiguration:
                                  serverPort: "${free.port.50}"
                                  mockResponses:
                                    doBackendRequestBeforeSelection:
                                      assignments:
                                        blub: "nothing changed"
                                      request:
                                        path: "/helloMain"
                                        additionalCriterions:
                                          - "'${blub}' == 'nothing changed'"
                                      response:
                                        statusCode: 777
                                        body: ${blub}
                                      backendRequests:
                                        helloBackend:
                                          url: "http://localhost:${free.port.60}/helloBackend"
                                          assignments:
                                            blub: "changed after selection"
                                          executeAfterSelection: true
                              backendServer:
                                type: zion
                                zionConfiguration:
                                  serverPort: "${free.port.60}"
                                  mockResponses:
                                    helloBackend:
                                      response:
                                        statusCode: 200
                                        body: '{"Hello":"World"}'
                                          """)
  @Test
  void
      testBackendRequestEvaluateAfter_shouldMatchUnchangedValueInCriterion_and_useAfterValueInRenderedBody(
          UnirestInstance unirest) {
    final HttpResponse<String> response =
        unirest
            .get(TigerGlobalConfiguration.resolvePlaceholders("http://mainServer/helloMain"))
            .asString();

    assertThat(response.getStatus()).isEqualTo(777);
    assertThat(response.getBody()).isEqualTo("changed after selection");
  }

  @TigerTest(
      tigerYaml =
          """
        servers:
          mainServer:
            type: zion
            zionConfiguration:
              serverPort: "${free.port.50}"
              mockResponses:
                hello:
                    response:
                        statusCode: 666
                        body: "<hello value='Wöüärld'></hello>"
        """)
  @Test
  void zionShouldNotMessUpUnicodeCharactersWhenNoExplicitEncodingSet(UnirestInstance unirest) {
    final HttpResponse<String> response =
        unirest
            .get(TigerGlobalConfiguration.resolvePlaceholders("http://mainServer/helloWorld"))
            .asString();

    XmlAssert.assertThat(response.getBody())
        .and(
            """
                <?xml version="1.0" encoding="UTF-8"?>

                <hello value="Wöüärld"></hello>""")
        .areIdentical();
  }

  @TigerTest(
      tigerYaml =
          """
        servers:
          mainServer:
            type: zion
            zionConfiguration:
              serverPort: "${free.port.50}"
              mockResponses:
                hello:
                    response:
                        statusCode: 666
                        body: "<hello value='Wöüärld'></hello>"
        """)
  @ParameterizedTest
  /*
  Why not more examples you ask? For that please have a look at XMLEntityManager::getEncodingInfo()
  The Java SAX-Parser determines the encoding NOT based on the stated encoding-information, but rather
  using the first four bytes of a document. Thus 'latin-1' will not work.
   */
  @ValueSource(strings = {"UTF-8", "UTF-16"})
  void zionShouldNotMessUpUnicodeCharactersWhenNoExplicitEncodingSet(
      String charsetName, UnirestInstance unirest, TigerTestEnvMgr tigerTestEnvMgr) {
    final Charset selectedCharset = Charset.forName(charsetName);
    final String xmlWithCharset =
        new String(
            ("<?xml version=\"1.0\" encoding=\""
                    + charsetName
                    + "\"?>\n"
                    + "<hello value=\"Wöüärld\"></hello>")
                .getBytes(selectedCharset),
            selectedCharset);
    ((ZionServerType) tigerTestEnvMgr.getServers().get("mainServer"))
        .getApplicationContext()
        .getBean(ZionConfiguration.class)
        .getMockResponses()
        .get("hello")
        .getResponse()
        .setBody(xmlWithCharset);

    final HttpResponse<String> response =
        unirest
            .get(TigerGlobalConfiguration.resolvePlaceholders("http://mainServer/helloWorld"))
            .asString();

    XmlAssert.assertThat(response.getBody()).and(xmlWithCharset).areIdentical();
  }

  @TigerTest(
      tigerYaml =
          """
            servers:
              serverTestName:
                type: zion
                zionConfiguration:
                  serverPort: ${free.port.3}
                  mockResponses:
                    testResponse:
                      nestedResponses:
                        login:
                          request:
                            method: POST
                            path: '/login/{someId}'
                          nestedResponses:
                            firstAlternative:
                              requestCriterions:
                                - "'${someId}' == '1'"
                              response:
                                statusCode: 888
                            secondAlternative:
                              requestCriterions:
                                - "'${someId}' != '1'"
                              response:
                                statusCode: 777
            """)
  @Test
  void testNestedResponsesInsideNestedResponses(UnirestInstance unirest) {
    HttpResponse<String> responseEmailConfirmed =
        unirest.post("http://serverTestName/login/1").asString();

    assertThat(responseEmailConfirmed.getStatus()).isEqualTo(888);

    HttpResponse<String> responseEmailNotConfirmed =
        unirest.post("http://serverTestName/login/2").asString();

    assertThat(responseEmailNotConfirmed.getStatus()).isEqualTo(777);
  }

  @TigerTest(
      tigerYaml =
          """
            servers:
              zionServer:
                type: zion
                zionConfiguration:
                  serverPort: "${free.port.10}"
                  mockResponses:
                    helloWorld:
                      requestCriterions:
                        - message.method == 'GET'
                      assignments:
                        foobar: blub
                      response:
                        body: '${foobar|fallback}'
            """)
  @Test
  void shouldLazyResolveRequestBodies(UnirestInstance unirestInstance) {
    final HttpResponse<String> response =
        unirestInstance
            .get(
                TigerGlobalConfiguration.resolvePlaceholders(
                    "http://zionServer/blubBlab/helloWorld"))
            .asString();

    assertThat(response.getBody()).isEqualTo("blub");
  }

  @TigerTest(
      tigerYaml =
          """
servers:
  serverTestName:
    type: zion
    zionConfiguration:
      serverPort: ${free.port.3}
      mockResponses:
        validToken:
          request:
            method: POST
          nestedResponses:
            registerUser:
              assignments:
                emailFromToken: hello@example.com
              nestedResponses:
                existingAssignment:
                  request:
                    path: "/existing"
                  requestCriterions:
                  response:
                    statusCode: 201
                    body: ${emailFromToken|InvalidEmail}
                nonExistingAssignment:
                  request:
                    path: "/nonExisting"
                  response:
                    statusCode: 401
                    body: ${emailNotExisting|InvalidEmail}
                    """)
  @Test
  void testAssignmentsWithFallbackValue(UnirestInstance unirest) {
    HttpResponse<String> responseExisting =
        unirest.post("http://serverTestName/existing/").asString();

    assertThat(responseExisting.getStatus()).isEqualTo(201);
    assertThat(responseExisting.getBody()).isEqualTo("hello@example.com");

    HttpResponse<String> responseNonExisting =
        unirest.post("http://serverTestName/nonExisting/").asString();

    assertThat(responseNonExisting.getStatus()).isEqualTo(401);
    assertThat(responseNonExisting.getBody()).isEqualTo("InvalidEmail");
  }

  @TigerTest(
      tigerYaml =
          """
              servers:
                mainServer:
                  type: externalJar
                  healthcheckUrl:
                    http://127.0.0.1:${free.port.30}
                  externalJarOptions:
                    arguments:
                      - --server.port=${free.port.30}
                      - --backendServer.port=${free.port.20}
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
                      - --spring.profiles.active=backendServer
                    workingDir: src/test/resources
                  source:
                    - local:../../../target/tiger-zion-*-executable.jar
                      """)
  @Test
  void testMultipleZionServerWithProfiles() {
    final HttpResponse<JsonNode> response =
        Unirest.get(
                TigerGlobalConfiguration.resolvePlaceholders(
                    "http://localhost:${free.port.30}/helloWorld"))
            .asJson();

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getBody().getObject().getString("Hello")).isEqualTo("World");
  }

  @TigerTest(
      tigerYaml =
          """
    servers:
      serverTestName:
        type: zion
        zionConfiguration:
          serverPort: ${free.port.3}
          mockResponses:
            testResponse:
              request:
                method: POST
              requestCriterions:
                - "$.body.['urn:telematik:claims:email'] == 'test'"
              response:
                statusCode: 777
                body: "?{$.body.['urn:telematik:claims:email']}"
                        """)
  @Test
  void testWithColonInPropertyname(UnirestInstance unirest) {
    HttpResponse<String> responseExisting =
        unirest
            .post("http://serverTestName/")
            .body(
                """
              {"urn:telematik:claims:email": "test"}
              """)
            .asString();

    assertThat(responseExisting.getStatus()).isEqualTo(777);
    assertThat(responseExisting.getBody()).isEqualTo("test");
  }

  @TigerTest(
      tigerYaml =
          """
        servers:
          zionServer:
            type: zion
            zionConfiguration:
              serverPort: "${free.port.10}"
              mockResponses:
                noSpecialKey:
                  requestCriterions:
                    - '$..specialKey == null'
                  response:
                    body: 'hassenich!'
                yesSpecialKey:
                  requestCriterions:
                    - '$..specialKey != null'
                  response:
                    body: 'baaam'
        """)
  @Test
  void testRbelPathElementNotPresentCriterions(UnirestInstance unirestInstance) {
    HttpResponse<String> response =
        unirestInstance
            .put(TigerGlobalConfiguration.resolvePlaceholders("http://zionServer/blub"))
            .body("{'somethingElse':'fdsafds'}")
            .asString();

    assertThat(response.getBody()).isEqualTo("hassenich!");

    response =
        unirestInstance
            .put(TigerGlobalConfiguration.resolvePlaceholders("http://zionServer/blub"))
            .body("{'specialKey':'fdsafds'}")
            .asString();

    assertThat(response.getBody()).isEqualTo("baaam");
  }
}
