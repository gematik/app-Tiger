package de.gematik.test.tiger.zion;

import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.UnirestInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ResetTigerConfiguration
class TestZionServerType {

    @BeforeEach
    @AfterEach
    public void resetConfig() {
        TigerGlobalConfiguration.reset();
    }

    @TigerTest(tigerYaml = "servers:\n"
        + "  zionServer:\n"
        + "    type: zion\n"
        + "    zionConfiguration:\n"
        + "      serverPort: \"${free.port.10}\"\n"
        + "      mockResponses:\n"
        + "        helloWorld:\n"
        + "          requestCriterions:\n"
        + "            - message.method == 'GET'\n"
        + "            - message.url =~ '.*/helloWorld'\n"
        + "          response:\n"
        + "            statusCode: 222\n"
        + "            body: '{\"Hello\":\"World\"}'\n")
    @Test
    void testZionServer() {
        final HttpResponse<JsonNode> response = Unirest.get(TigerGlobalConfiguration.resolvePlaceholders(
            "http://localhost:${free.port.10}/blubBlab/helloWorld"
        )).asJson();

        assertThat(response.getStatus())
            .isEqualTo(222);
        assertThat(response.getBody().getObject().getString("Hello"))
            .isEqualTo("World");
    }
    @TigerTest(tigerYaml = "servers:\n"
        + "  zionExternal:\n"
        + "    type: externalJar\n"
        + "    healthcheckUrl:\n"
        + "      http://127.0.0.1:${free.port.10}\n"
        + "    externalJarOptions:\n"
        + "      arguments:\n"
        + "        - --server.port=${free.port.10}\n"
        + "        - --spring.profiles.active=echoserver\n"
        + "      workingDir: src/test/resources\n"
        + "    source:\n"
        + "      - local:../../../target/tiger-zion-*-executable.jar\n")
    @Test
    void testExternalZionServer(UnirestInstance unirest) {
        final HttpResponse<JsonNode> response = unirest.get(TigerGlobalConfiguration.resolvePlaceholders(
            "http://localhost:${free.port.10}/blubBlab/helloWorld"
        )).asJson();

        assertThat(response.getStatus())
            .isEqualTo(222);
        assertThat(response.getBody().getObject().getString("Hello"))
            .isEqualTo("World");
    }

    @TigerTest(tigerYaml = """
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
        final HttpResponse<JsonNode> response = unirest.get(TigerGlobalConfiguration.resolvePlaceholders(
                "http://mainServer/helloWorld"
            ))
            .header("password", "secret")
            .asJson();

        assertThat(response.getStatus())
            .isEqualTo(200);
    }
}
